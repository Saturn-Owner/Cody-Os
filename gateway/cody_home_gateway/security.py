from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import threading
import time
from typing import Any


MAX_CLOCK_SKEW_SECONDS = int(os.getenv("CODY_HOME_MAX_CLOCK_SKEW_SECONDS", "30"))
DEFAULT_REQUEST_TTL_SECONDS = 60


def canonical_request(
    *,
    method: str,
    path: str,
    timestamp: str,
    nonce: str,
    body: bytes,
) -> bytes:
    body_hash = hashlib.sha256(body).hexdigest()
    return "\n".join(
        [
            method.upper(),
            path,
            timestamp,
            nonce,
            body_hash,
        ]
    ).encode("utf-8")


def sign_request(
    *,
    secret: str,
    method: str,
    path: str,
    timestamp: str,
    nonce: str,
    body: bytes,
) -> str:
    return hmac.new(
        secret.encode("utf-8"),
        canonical_request(
            method=method,
            path=path,
            timestamp=timestamp,
            nonce=nonce,
            body=body,
        ),
        hashlib.sha256,
    ).hexdigest()


@dataclass
class NonceStore:
    path: Path | None = None
    ttl_seconds: int = 300
    _entries: dict[str, float] = field(default_factory=dict)
    _lock: threading.Lock = field(default_factory=threading.Lock)

    def __post_init__(self) -> None:
        if not self.path or not self.path.exists():
            return
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            raw = {}
        if isinstance(raw, dict):
            self._entries = {str(k): float(v) for k, v in raw.items()}

    def claim(self, nonce: str, *, now: float | None = None) -> bool:
        current = time.time() if now is None else now
        nonce_hash = _hash(nonce)
        with self._lock:
            self._prune_locked(current)
            if nonce_hash in self._entries:
                return False
            self._entries[nonce_hash] = current + self.ttl_seconds
            self._persist_locked()
            return True

    def _prune_locked(self, now: float) -> None:
        expired = [key for key, expires_at in self._entries.items() if expires_at <= now]
        for key in expired:
            self._entries.pop(key, None)

    def _persist_locked(self) -> None:
        if not self.path:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(self.path.suffix + ".tmp")
        tmp.write_text(json.dumps(self._entries, sort_keys=True), encoding="utf-8")
        os.replace(tmp, self.path)
        try:
            self.path.chmod(0o600)
        except OSError:
            pass


@dataclass(frozen=True)
class Device:
    device_id: str
    secret: str
    name: str
    created_at: float


class DeviceStore:
    def __init__(self, path: Path) -> None:
        self.path = path
        self._lock = threading.Lock()
        self._devices: dict[str, Device] = {}
        self._load()

    def get(self, device_id: str) -> Device | None:
        with self._lock:
            return self._devices.get(device_id)

    def add(self, name: str) -> Device:
        with self._lock:
            device = Device(
                device_id=secrets.token_urlsafe(18),
                secret=secrets.token_urlsafe(48),
                name=name[:80] or "Cody Home",
                created_at=time.time(),
            )
            self._devices[device.device_id] = device
            self._persist_locked()
            return device

    def count(self) -> int:
        with self._lock:
            return len(self._devices)

    def _load(self) -> None:
        if not self.path.exists():
            return
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            raw = {}
        devices = raw.get("devices", {}) if isinstance(raw, dict) else {}
        if not isinstance(devices, dict):
            return
        for device_id, item in devices.items():
            if not isinstance(item, dict):
                continue
            secret = str(item.get("secret", ""))
            name = str(item.get("name", "Cody Home"))
            created_at = float(item.get("created_at", 0))
            if secret:
                self._devices[str(device_id)] = Device(
                    device_id=str(device_id),
                    secret=secret,
                    name=name,
                    created_at=created_at,
                )

    def _persist_locked(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "devices": {
                device_id: {
                    "secret": device.secret,
                    "name": device.name,
                    "created_at": device.created_at,
                }
                for device_id, device in self._devices.items()
            }
        }
        tmp = self.path.with_suffix(self.path.suffix + ".tmp")
        tmp.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
        os.replace(tmp, self.path)
        try:
            self.path.chmod(0o600)
        except OSError:
            pass


class PairingStore:
    def __init__(self, path: Path) -> None:
        self.path = path
        self._lock = threading.Lock()

    def create_code(self, *, ttl_seconds: int = 300) -> str:
        code = f"{secrets.randbelow(1_000_000):06d}"
        payload = {
            "code_hash": _hash(code),
            "expires_at": time.time() + ttl_seconds,
            "used": False,
        }
        with self._lock:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.path.with_suffix(self.path.suffix + ".tmp")
            tmp.write_text(json.dumps(payload, sort_keys=True), encoding="utf-8")
            os.replace(tmp, self.path)
            try:
                self.path.chmod(0o600)
            except OSError:
                pass
        return code

    def consume(self, code: str) -> bool:
        with self._lock:
            try:
                payload = json.loads(self.path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                return False
            if not isinstance(payload, dict) or payload.get("used"):
                return False
            if float(payload.get("expires_at", 0)) <= time.time():
                return False
            if not hmac.compare_digest(str(payload.get("code_hash", "")), _hash(code)):
                return False
            payload["used"] = True
            tmp = self.path.with_suffix(self.path.suffix + ".tmp")
            tmp.write_text(json.dumps(payload, sort_keys=True), encoding="utf-8")
            os.replace(tmp, self.path)
            return True


def verify_device_request(
    *,
    device_store: DeviceStore,
    nonce_store: NonceStore,
    device_id: str,
    signature: str,
    method: str,
    path: str,
    timestamp: str,
    nonce: str,
    body: bytes,
    now: float | None = None,
) -> tuple[bool, str]:
    current = time.time() if now is None else now
    if not device_id or not signature or not timestamp or not nonce:
        return False, "device_auth_required"
    try:
        ts = float(timestamp)
    except ValueError:
        return False, "invalid_timestamp"
    if abs(current - ts) > MAX_CLOCK_SKEW_SECONDS:
        return False, "request_expired"
    device = device_store.get(device_id)
    if device is None:
        return False, "unknown_device"
    expected = sign_request(
        secret=device.secret,
        method=method,
        path=path,
        timestamp=timestamp,
        nonce=nonce,
        body=body,
    )
    if not hmac.compare_digest(signature, expected):
        return False, "invalid_signature"
    if not nonce_store.claim(nonce, now=current):
        return False, "replay_detected"
    return True, "ok"


def _hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()
