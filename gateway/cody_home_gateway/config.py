from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path


@dataclass(frozen=True)
class GatewayConfig:
    home_dir: Path
    state_dir: Path
    bind_host: str
    bind_port: int
    public_base_path: str
    hermes_api_base: str
    hermes_api_key: str
    hermes_session_key: str
    hermes_session_title: str
    pairing_ttl_seconds: int
    max_clock_skew_seconds: int
    nonce_ttl_seconds: int
    voice_enabled: bool
    max_voice_upload_bytes: int


def load_config() -> GatewayConfig:
    home_dir = Path(os.getenv("CODY_HOME_GATEWAY_HOME", ".")).resolve()
    state_dir = Path(os.getenv("CODY_HOME_GATEWAY_STATE", str(home_dir / "state"))).resolve()
    return GatewayConfig(
        home_dir=home_dir,
        state_dir=state_dir,
        bind_host=os.getenv("CODY_HOME_BIND_HOST", "127.0.0.1"),
        bind_port=_env_int("CODY_HOME_BIND_PORT", 8787),
        public_base_path=_normalize_base_path(os.getenv("CODY_HOME_PUBLIC_BASE_PATH", "/cody-home")),
        hermes_api_base=os.getenv("CODY_HOME_HERMES_API_BASE", "http://127.0.0.1:8642").rstrip("/"),
        hermes_api_key=os.getenv("CODY_HOME_HERMES_API_KEY", os.getenv("API_SERVER_KEY", "")),
        hermes_session_key=os.getenv("CODY_HOME_HERMES_SESSION_KEY", "cody-home"),
        hermes_session_title=os.getenv("CODY_HOME_HERMES_SESSION_TITLE", "Cody Home"),
        pairing_ttl_seconds=_env_int("CODY_HOME_PAIRING_TTL_SECONDS", 300),
        max_clock_skew_seconds=_env_int("CODY_HOME_MAX_CLOCK_SKEW_SECONDS", 30),
        nonce_ttl_seconds=_env_int("CODY_HOME_NONCE_TTL_SECONDS", 300),
        voice_enabled=_env_bool("CODY_HOME_VOICE_ENABLED", True),
        max_voice_upload_bytes=_env_int("CODY_HOME_MAX_VOICE_UPLOAD_BYTES", 15 * 1024 * 1024),
    )


def _env_int(name: str, default: int) -> int:
    try:
        value = int(str(os.getenv(name, "")).strip())
    except ValueError:
        return default
    return value if value > 0 else default


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _normalize_base_path(value: str) -> str:
    value = (value or "").strip()
    if not value or value == "/":
        return ""
    return "/" + value.strip("/")
