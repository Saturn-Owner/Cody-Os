from __future__ import annotations

import json
import time
from typing import Any
from uuid import uuid4

from fastapi import FastAPI, Header, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse, Response

from . import __version__
from .events import CodyHomeEvent, state_event
from .config import load_config
from .hermes_client import HermesClient
from .security import DeviceStore, NonceStore, PairingStore, verify_device_request
from .voice import handle_voice_request


CONFIG = load_config()
STATE_DIR = CONFIG.state_dir
DEVICE_STORE = DeviceStore(STATE_DIR / "devices.json")
PAIRING_STORE = PairingStore(STATE_DIR / "pairing.json")
NONCE_STORE = NonceStore(STATE_DIR / "nonces.json", ttl_seconds=CONFIG.nonce_ttl_seconds)
HERMES = HermesClient(config=CONFIG, session_id_file=STATE_DIR / "hermes_session_id")

app = FastAPI(title="Cody Home Gateway", version=__version__)


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "service": "cody-home-gateway",
        "version": __version__,
        "devices_paired": DEVICE_STORE.count(),
        "hermes_configured": HERMES.configured(),
        "voice_enabled": CONFIG.voice_enabled,
        "public_base_path": CONFIG.public_base_path,
    }


@app.post("/pair")
async def pair(request: Request) -> JSONResponse:
    try:
        body = await request.json()
    except json.JSONDecodeError:
        return JSONResponse({"error": "json_body_required"}, status_code=400)
    if not isinstance(body, dict):
        return JSONResponse({"error": "json_object_required"}, status_code=400)
    code = str(body.get("code", ""))
    if not code:
        return JSONResponse({"error": "code_required"}, status_code=400)
    device_name = str(body.get("device_name", "Cody Home Echo"))
    if not PAIRING_STORE.consume(code):
        return JSONResponse({"error": "invalid_or_expired_pairing_code"}, status_code=401)
    device = DEVICE_STORE.add(device_name)
    # Das Secret wird einmalig über den HTTPS-Kanal an das Gerät zurückgegeben.
    # Auf Disk wird nur der Hash gespeichert.
    return JSONResponse(
        {
            "device_id": device.device_id,
            "device_secret": device.secret,
            "protocol_version": 1,
        },
        status_code=201,
    )

@app.post("/message")
async def message(
    request: Request,
    x_cody_device_id: str = Header(default=""),
    x_cody_timestamp: str = Header(default=""),
    x_cody_nonce: str = Header(default=""),
    x_cody_signature: str = Header(default=""),
) -> JSONResponse:
    body = await request.body()
    ok, reason = verify_device_request(
        device_store=DEVICE_STORE,
        nonce_store=NONCE_STORE,
        device_id=x_cody_device_id,
        signature=x_cody_signature,
        method=request.method,
        path=request.url.path,
        timestamp=x_cody_timestamp,
        nonce=x_cody_nonce,
        body=body,
    )
    if not ok:
        return JSONResponse({"error": reason}, status_code=401)
    payload = json.loads(body.decode("utf-8") or "{}")
    text = str(payload.get("text", "")).strip()
    if not text:
        return JSONResponse({"error": "text_required"}, status_code=400)
    request_id = str(payload.get("request_id") or uuid4())
    accumulated = ""
    final_text = ""
    async for event in HERMES.stream_message(text, request_id=request_id):
        if event["type"] == "cody.message.delta":
            delta = str(event.get("data", {}).get("text", ""))
            if delta.startswith(accumulated):
                accumulated = delta
            else:
                accumulated += delta
        elif event["type"] == "cody.message.completed":
            final_text = str(event.get("data", {}).get("text", "")) or final_text
        elif event["type"] == "connection.error":
            return JSONResponse(event, status_code=502)
    return JSONResponse(
        CodyHomeEvent(
            type="cody.message.completed",
            request_id=request_id,
            data={"text": final_text or accumulated},
        ).to_dict()
    )


@app.post("/voice")
async def voice(
    request: Request,
    x_cody_device_id: str = Header(default=""),
    x_cody_timestamp: str = Header(default=""),
    x_cody_nonce: str = Header(default=""),
    x_cody_signature: str = Header(default=""),
) -> Response:
    return await handle_voice_request(
        request=request,
        device_store=DEVICE_STORE,
        nonce_store=NONCE_STORE,
        hermes=HERMES,
        state_dir=STATE_DIR,
        x_cody_device_id=x_cody_device_id,
        x_cody_timestamp=x_cody_timestamp,
        x_cody_nonce=x_cody_nonce,
        x_cody_signature=x_cody_signature,
        voice_enabled=CONFIG.voice_enabled,
        max_upload_bytes=CONFIG.max_voice_upload_bytes,
    )


@app.websocket("/ws")
async def ws(websocket: WebSocket) -> None:
    await websocket.accept()
    authenticated = False
    try:
        await websocket.send_json(CodyHomeEvent(type="connection.ready", data={"authenticated": False}).to_dict())
        while True:
            frame = await websocket.receive_json()
            frame_type = str(frame.get("type", ""))
            if frame_type == "ping":
                await websocket.send_json(CodyHomeEvent(type="pong", data={}).to_dict())
                continue
            if not authenticated:
                if frame_type != "connection.auth":
                    await websocket.send_json(CodyHomeEvent(type="connection.error", data={"reason": "device_auth_required"}).to_dict())
                    continue
                ok, reason = _verify_ws_auth(frame)
                if not ok:
                    await websocket.send_json(CodyHomeEvent(type="connection.error", data={"reason": reason}).to_dict())
                    continue
                authenticated = True
                await websocket.send_json(CodyHomeEvent(type="connection.ready", data={"authenticated": True}).to_dict())
                await websocket.send_json(state_event("IDLE"))
                continue
            if frame_type != "cody.message":
                await websocket.send_json(CodyHomeEvent(type="connection.error", data={"reason": "unsupported_event"}).to_dict())
                continue
            await _handle_ws_message(websocket, frame)
    except WebSocketDisconnect:
        return


def _verify_ws_auth(frame: dict[str, Any]) -> tuple[bool, str]:
    body = json.dumps(frame.get("data", {}), separators=(",", ":"), sort_keys=True).encode("utf-8")
    return verify_device_request(
        device_store=DEVICE_STORE,
        nonce_store=NONCE_STORE,
        device_id=str(frame.get("device_id", "")),
        signature=str(frame.get("signature", "")),
        method="WEBSOCKET",
        path="/ws",
        timestamp=str(frame.get("timestamp", "")),
        nonce=str(frame.get("nonce", "")),
        body=body,
    )


async def _handle_ws_message(websocket: WebSocket, frame: dict[str, Any]) -> None:
    request_id = str(frame.get("request_id") or uuid4())
    data = frame.get("data") or {}
    text = str(data.get("text", "")).strip() if isinstance(data, dict) else ""
    if not text:
        await websocket.send_json(CodyHomeEvent(type="connection.error", request_id=request_id, data={"reason": "text_required"}).to_dict())
        return
    await websocket.send_json(state_event("THINKING", request_id=request_id))
    await websocket.send_json(CodyHomeEvent(type="cody.message.started", request_id=request_id, data={}).to_dict())
    async for event in HERMES.stream_message(text, request_id=request_id):
        await websocket.send_json(CodyHomeEvent(type=event["type"], request_id=request_id, data=event.get("data", {})).to_dict())
    await websocket.send_json(CodyHomeEvent(type="cody.message.completed", request_id=request_id, data={}).to_dict())
    await websocket.send_json(state_event("SUCCESS", request_id=request_id))
    await websocket.send_json(state_event("IDLE", request_id=request_id))
