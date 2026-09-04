from __future__ import annotations

from dataclasses import dataclass
import json
import os
from pathlib import Path
from typing import AsyncIterator

import httpx

from .events import map_hermes_sse_to_codyos_event


@dataclass(frozen=True)
class HermesBackendConfig:
    api_base: str = "http://127.0.0.1:8642"
    api_key: str = ""
    session_key: str = "codyos-home"
    session_title: str = "CodyOS Home"
    timeout_seconds: float = 120.0

    @classmethod
    def from_env(cls) -> "HermesBackendConfig":
        return cls(
            api_base=os.getenv("CODYOS_HERMES_API_BASE", os.getenv("CODY_HOME_HERMES_API_BASE", cls.api_base)).rstrip("/"),
            api_key=os.getenv("CODYOS_HERMES_API_KEY", os.getenv("CODY_HOME_HERMES_API_KEY", os.getenv("API_SERVER_KEY", ""))),
            session_key=os.getenv("CODYOS_HERMES_SESSION_KEY", os.getenv("CODY_HOME_HERMES_SESSION_KEY", cls.session_key)),
            session_title=os.getenv("CODYOS_HERMES_SESSION_TITLE", os.getenv("CODY_HOME_HERMES_SESSION_TITLE", cls.session_title)),
            timeout_seconds=float(os.getenv("CODYOS_HERMES_TIMEOUT_SECONDS", str(cls.timeout_seconds))),
        )


class HermesBackendClient:
    """Generic CodyOS adapter for a user-owned Hermes-compatible API server."""

    def __init__(self, config: HermesBackendConfig | None = None, *, session_id_file: Path | None = None) -> None:
        self.config = config or HermesBackendConfig.from_env()
        self.session_id_file = session_id_file

    def configured(self) -> bool:
        return bool(self.config.api_base and self.config.api_key)

    async def health(self) -> dict:
        if not self.configured():
            return {"ok": False, "reason": "hermes_api_key_missing"}
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(f"{self.config.api_base}/health", headers=self._headers())
        return {"ok": response.status_code == 200, "status_code": response.status_code}

    async def stream_text(self, text: str, *, request_id: str = "") -> AsyncIterator[dict]:
        if not self.configured():
            yield {"type": "connection.error", "data": {"reason": "hermes_api_key_missing"}, "state": "ERROR"}
            return
        async with httpx.AsyncClient(timeout=self.config.timeout_seconds) as client:
            session_id = await self._ensure_session(client)
            headers = self._headers()
            headers["X-Hermes-Session-Key"] = self.config.session_key
            async with client.stream(
                "POST",
                f"{self.config.api_base}/api/sessions/{session_id}/chat/stream",
                headers=headers,
                json={"message": text},
            ) as response:
                if response.status_code >= 400:
                    yield {"type": "connection.error", "data": {"reason": "hermes_api_error", "status_code": response.status_code}, "state": "ERROR"}
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
                    yield map_hermes_sse_to_codyos_event(payload)

    async def _ensure_session(self, client: httpx.AsyncClient) -> str:
        if self.session_id_file and self.session_id_file.exists():
            session_id = self.session_id_file.read_text(encoding="utf-8").strip()
            if session_id:
                return session_id
        existing = await self._find_existing_session(client)
        if existing:
            self._persist_session_id(existing)
            return existing
        response = await client.post(
            f"{self.config.api_base}/api/sessions",
            headers=self._headers(),
            json={"title": self.config.session_title},
        )
        if response.status_code == 400:
            existing = await self._find_existing_session(client)
            if existing:
                self._persist_session_id(existing)
                return existing
        response.raise_for_status()
        payload = response.json()
        nested = payload.get("session") if isinstance(payload, dict) else None
        session_id = str(payload.get("session_id") or payload.get("id") or (nested or {}).get("id"))
        self._persist_session_id(session_id)
        return session_id

    async def _find_existing_session(self, client: httpx.AsyncClient) -> str:
        response = await client.get(f"{self.config.api_base}/api/sessions", headers=self._headers())
        if response.status_code >= 400:
            return ""
        payload = response.json()
        for item in payload.get("data", []):
            if item.get("title") == self.config.session_title and item.get("id"):
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
        return {"Authorization": f"Bearer {self.config.api_key}"}
