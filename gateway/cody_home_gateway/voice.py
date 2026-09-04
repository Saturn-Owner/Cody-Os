from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path
import tempfile
from typing import Any
from uuid import uuid4

from fastapi import Request
from fastapi.responses import JSONResponse, Response
from starlette.datastructures import UploadFile

from .events import CodyHomeEvent
from .hermes_client import HermesClient
from .security import DeviceStore, NonceStore, verify_device_request

VOICE_TMP_DIRNAME = "voice_tmp"

_ALLOWED_EXTENSIONS = {
    ".m4a",
    ".mp4",
    ".aac",
    ".ogg",
    ".oga",
    ".opus",
    ".wav",
    ".webm",
    ".mp3",
}

_ALLOWED_CONTENT_TYPES = {
    "audio/mp4",
    "audio/m4a",
    "audio/aac",
    "audio/ogg",
    "audio/opus",
    "audio/wav",
    "audio/x-wav",
    "audio/webm",
    "audio/mpeg",
    "audio/mp3",
    "video/mp4",
    "application/octet-stream",
}

_CONTENT_TYPE_EXTENSION = {
    "audio/mp4": ".m4a",
    "audio/m4a": ".m4a",
    "audio/aac": ".aac",
    "audio/ogg": ".ogg",
    "audio/opus": ".opus",
    "audio/wav": ".wav",
    "audio/x-wav": ".wav",
    "audio/webm": ".webm",
    "audio/mpeg": ".mp3",
    "audio/mp3": ".mp3",
    "video/mp4": ".m4a",
}


def voice_event_types() -> list[str]:
    return [
        "voice.transcription.started",
        "voice.transcription.completed",
        "cody.message.started",
        "cody.message.delta",
        "cody.message.completed",
        "voice.tts.started",
        "voice.tts.completed",
        "voice.audio.ready",
    ]


async def handle_voice_request(
    *,
    request: Request,
    device_store: DeviceStore,
    nonce_store: NonceStore,
    hermes: HermesClient,
    state_dir: Path,
    x_cody_device_id: str,
    x_cody_timestamp: str,
    x_cody_nonce: str,
    x_cody_signature: str,
    voice_enabled: bool = True,
    max_upload_bytes: int = 15 * 1024 * 1024,
) -> Response:
    if not voice_enabled:
        return JSONResponse({"error": "voice_disabled"}, status_code=404)

    body = await request.body()
    ok, reason = verify_device_request(
        device_store=device_store,
        nonce_store=nonce_store,
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

    content_type = request.headers.get("content-type", "")
    if "multipart/form-data" not in content_type.lower():
        return JSONResponse({"error": "multipart_required"}, status_code=415)

    try:
        form = await request.form()
    except Exception:
        return JSONResponse({"error": "invalid_multipart"}, status_code=400)

    request_id = str(form.get("request_id") or uuid4())
    audio = form.get("audio")
    input_format = str(form.get("input_format") or "").strip().lower()
    if not isinstance(audio, UploadFile):
        return JSONResponse({"error": "audio_required"}, status_code=400)

    tmp_dir = state_dir / VOICE_TMP_DIRNAME
    tmp_dir.mkdir(parents=True, exist_ok=True)
    try:
        tmp_dir.chmod(0o700)
    except OSError:
        pass

    input_path: Path | None = None
    output_paths: list[Path] = []
    try:
        input_bytes = await audio.read(max_upload_bytes + 1)
        if not input_bytes:
            return JSONResponse({"error": "audio_empty"}, status_code=400)
        if len(input_bytes) > max_upload_bytes:
            return JSONResponse({"error": "audio_too_large", "max_bytes": max_upload_bytes}, status_code=413)

        ext = _resolve_audio_extension(
            filename=audio.filename or "",
            content_type=audio.content_type or "",
            input_format=input_format,
        )
        if not ext:
            return JSONResponse({"error": "unsupported_audio_format"}, status_code=415)

        fd, raw_input_path = tempfile.mkstemp(prefix="voice_in_", suffix=ext, dir=tmp_dir)
        input_path = Path(raw_input_path)
        with os.fdopen(fd, "wb") as fh:
            fh.write(input_bytes)
        try:
            input_path.chmod(0o600)
        except OSError:
            pass

        transcript_result = await asyncio.to_thread(_transcribe_audio, str(input_path))
        if not transcript_result.get("success"):
            return JSONResponse(
                {"error": "transcription_failed", "reason": str(transcript_result.get("error", "unknown"))},
                status_code=502,
            )
        transcript = str(transcript_result.get("transcript", "")).strip()
        if not transcript:
            return JSONResponse({"error": "no_speech_detected"}, status_code=422)

        final_text = ""
        accumulated = ""
        async for event in hermes.stream_message(transcript, request_id=request_id):
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
        answer = (final_text or accumulated).strip()
        if not answer:
            return JSONResponse({"error": "empty_cody_response"}, status_code=502)

        output_path = tmp_dir / f"voice_out_{request_id.replace('/', '_')}.mp3"
        tts_result = await asyncio.to_thread(_text_to_speech, answer, str(output_path))
        if not tts_result.get("success", False):
            return JSONResponse(
                {"error": "tts_failed", "reason": str(tts_result.get("error", "unknown"))},
                status_code=502,
            )
        raw_paths = tts_result.get("file_paths") or [tts_result.get("file_path") or str(output_path)]
        output_paths = [Path(str(p)) for p in raw_paths if p]
        mp3_path = next((p for p in output_paths if p.exists()), output_path)
        if not mp3_path.exists():
            return JSONResponse({"error": "tts_output_missing"}, status_code=502)
        audio_bytes = mp3_path.read_bytes()
        if not audio_bytes:
            return JSONResponse({"error": "tts_output_empty"}, status_code=502)

        headers = {
            "X-Cody-Request-Id": request_id,
            "X-Cody-Voice-Events": ",".join(voice_event_types()),
            "X-Cody-Transcript-Provider": str(transcript_result.get("provider", "local")),
            "X-Cody-TTS-Provider": str(tts_result.get("provider", "edge")),
            "Cache-Control": "no-store",
        }
        return Response(content=audio_bytes, media_type="audio/mpeg", headers=headers)
    finally:
        try:
            if isinstance(audio, UploadFile):
                await audio.close()
        except Exception:
            pass
        paths = []
        if input_path is not None:
            paths.append(input_path)
        paths.extend(output_paths)
        for path in paths:
            try:
                if path.is_file() and path.is_relative_to(tmp_dir):
                    path.unlink()
            except Exception:
                pass


def _resolve_audio_extension(*, filename: str, content_type: str, input_format: str) -> str:
    candidates = []
    if input_format:
        fmt = input_format.lower().strip()
        if not fmt.startswith("."):
            fmt = "." + fmt
        candidates.append(fmt)
    if filename:
        candidates.append(Path(filename).suffix.lower())
    ctype = content_type.split(";", 1)[0].strip().lower()
    if ctype and ctype not in _ALLOWED_CONTENT_TYPES:
        return ""
    if ctype in _CONTENT_TYPE_EXTENSION:
        candidates.append(_CONTENT_TYPE_EXTENSION[ctype])
    for candidate in candidates:
        if candidate in _ALLOWED_EXTENSIONS:
            return candidate
    return ""


def _transcribe_audio(path: str) -> dict[str, Any]:
    from tools.transcription_tools import transcribe_audio

    return transcribe_audio(path, source="cody_home_voice")


def _text_to_speech(text: str, output_path: str) -> dict[str, Any]:
    from tools.tts_tool import text_to_speech_tool

    raw = text_to_speech_tool(text=text, output_path=output_path, provider="edge")
    if isinstance(raw, str):
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"success": False, "error": "invalid_tts_result"}
    return raw if isinstance(raw, dict) else {"success": False, "error": "invalid_tts_result"}
