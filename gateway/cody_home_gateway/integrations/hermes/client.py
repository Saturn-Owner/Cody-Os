from __future__ import annotations

import json
from pathlib import Path
from typing import AsyncIterator

import httpx

from ...config import GatewayConfig, load_config


class HermesClient:
    """Generischer Adapter für ein Hermes-kompatibles HTTP/SSE-Backend.

    Dieses Gateway enthält keinen Agenten, keine Memory und keinen
    Model-Provider. Es leitet Text-Turns an ein separat konfiguriertes
    Hermes-kompatibles Backend weiter.
    """

    def __init__(
        self,
        *,
        config: GatewayConfig | None = None,
        base_url: str | None = None,
        api_key: str | None = None,
        session_key: str | None = None,
        session_title: str | None = None,
        session_id_file: Path | None = None,
        timeout_seconds: float = 120,
    ) -> None:
        cfg = config or load_config()
        self.base_url = (base_url or cfg.hermes_api_base).rstrip("/")
        self.api_key = api_key if api_key is not None else cfg.hermes_api_key
        self.session_key = session_key or cfg.hermes_session_key
        self.session_title = session_title or cfg.hermes_session_title
        self.session_id_file = session_id_file
        self.timeout_seconds = timeout_seconds

    def configured(self) -> bool:
        return bool(self.base_url and self.api_key)

    async def health(self) -> dict:
        if not self.configured():
            return {"ok": False, "reason": "hermes_api_key_missing"}
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(f"{self.base_url}/health", headers=self._headers())
        return {"ok": response.status_code == 200, "status_code": response.status_code}

    async def stream_message(self, text: str, *, request_id: str) -> AsyncIterator[dict]:
        if not self.configured():
            yield {"type": "connection.error", "data": {"reason": "hermes_api_key_missing"}}
            return
        async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
            session_id = await self._ensure_session(client)
            url = f"{self.base_url}/api/sessions/{session_id}/chat/stream"
            headers = self._headers()
            headers["X-Hermes-Session-Key"] = self.session_key
            async with client.stream("POST", url, headers=headers, json={"message": text}) as response:
                if response.status_code >= 400:
                    yield {"type": "connection.error", "data": {"reason": "hermes_api_error", "status_code": response.status_code}}
                    return
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    raw = line[5:].strip()
                    if not raw or raw == "[DONE]":
                        continue
                    try:
                        payload = json.loads(raw)
                    except json.JSONDecodeError:
                        continue
                    yield _map_hermes_sse(payload)

    async def _ensure_session(self, client: httpx.AsyncClient) -> str:
        if self.session_id_file and self.session_id_file.exists():
            session_id = self.session_id_file.read_text(encoding="utf-8").strip()
            if session_id:
                return session_id
        existing = await self._find_existing_session(client)
        if existing:
            self._persist_session_id(existing)
            return existing
        response = await client.post(f"{self.base_url}/api/sessions", headers=self._headers(), json={"title": self.session_title})
        if response.status_code == 400:
            existing = await self._find_existing_session(client)
            if existing:
                self._persist_session_id(existing)
                return existing
        response.raise_for_status()
        payload = response.json()
        nested_session = payload.get("session") if isinstance(payload, dict) else None
        session_id = str(payload.get("session_id") or payload.get("id") or (nested_session or {}).get("id"))
        self._persist_session_id(session_id)
        return session_id

    async def _find_existing_session(self, client: httpx.AsyncClient) -> str:
        response = await client.get(f"{self.base_url}/api/sessions", headers=self._headers())
        if response.status_code >= 400:
            return ""
        payload = response.json()
        for item in payload.get("data", []):
            if item.get("title") == self.session_title and item.get("id"):
                return str(item["id"])
        return ""

    def _persist_session_id(self, session_id: str) -> None:
        if self.session_id_file and session_id and session_id != "None":
            self.session_id_file.parent.mkdir(parents=True, exist_ok=True)
            self.session_id_file.write_text(session_id + "\n", encoding="utf-8")
            try:
                self.session_id_file.chmod(0o600)
            except OSError:
                pass

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.api_key}"}


def _map_hermes_sse(payload: dict) -> dict:
    try:
        from codyos_hermes_integration import map_hermes_sse_to_codyos_event

        mapped = map_hermes_sse_to_codyos_event(payload)
        return {"type": mapped.get("type", "task.progress"), "data": mapped.get("data", payload)}
    except Exception:
        pass

    event_type = str(payload.get("event") or payload.get("type") or "")
    if event_type in {"tool.started", "tool.completed", "tool.failed", "approval.requested", "approval.resolved"}:
        return {"type": event_type, "data": payload}
    if payload.get("completed") and isinstance(payload.get("messages"), list):
        for message in reversed(payload["messages"]):
            if isinstance(message, dict) and message.get("role") == "assistant":
                return {"type": "cody.message.completed", "data": {"text": str(message.get("content", "")), "raw": payload}}
    text = payload.get("delta") or payload.get("text") or payload.get("content")
    if text:
        return {"type": "cody.message.delta", "data": {"text": str(text)}}
    if event_type in {"completed", "message.completed", "response.completed"}:
        return {"type": "cody.message.completed", "data": payload}
    return {"type": "task.progress", "data": payload}
