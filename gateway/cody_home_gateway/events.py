from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4


PROTOCOL_VERSION = 1

EVENT_TYPES = {
    "connection.ready",
    "connection.error",
    "cody.state",
    "cody.message.started",
    "cody.message.delta",
    "cody.message.completed",
    "task.started",
    "task.progress",
    "task.completed",
    "task.failed",
    "tool.started",
    "tool.completed",
    "tool.failed",
    "model.changed",
    "notification",
    "approval.requested",
    "approval.resolved",
    "ping",
    "pong",
    "voice.audio.ready",
    "voice.tts.completed",
    "voice.tts.started",
    "voice.transcription.completed",
    "voice.transcription.started",
}

UI_STATES = {
    "IDLE",
    "CONNECTING",
    "LISTENING",
    "THINKING",
    "WORKING",
    "SPEAKING",
    "SUCCESS",
    "ERROR",
    "OFFLINE",
    "APPROVAL_REQUIRED",
    "SLEEPING",
}


@dataclass(frozen=True)
class CodyHomeEvent:
    type: str
    data: dict[str, Any] = field(default_factory=dict)
    request_id: str = field(default_factory=lambda: str(uuid4()))
    timestamp: str = field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat()
    )
    version: int = PROTOCOL_VERSION

    def to_dict(self) -> dict[str, Any]:
        if self.type not in EVENT_TYPES:
            raise ValueError(f"Unsupported event type: {self.type}")
        return {
            "version": self.version,
            "type": self.type,
            "timestamp": self.timestamp,
            "request_id": self.request_id,
            "data": self.data,
        }


def state_event(state: str, *, request_id: str | None = None) -> dict[str, Any]:
    if state not in UI_STATES:
        raise ValueError(f"Unsupported Cody Home state: {state}")
    event = CodyHomeEvent(
        type="cody.state",
        request_id=request_id or str(uuid4()),
        data={"state": state},
    )
    return event.to_dict()


def map_hermes_event_to_state(event_type: str) -> str:
    mapping = {
        "connection.ready": "IDLE",
        "connection.error": "ERROR",
        "message.started": "THINKING",
        "cody.message.started": "THINKING",
        "cody.message.delta": "THINKING",
        "cody.message.completed": "SUCCESS",
        "tool.started": "WORKING",
        "tool.completed": "THINKING",
        "tool.failed": "ERROR",
        "task.started": "THINKING",
        "task.progress": "WORKING",
        "task.completed": "SUCCESS",
        "task.failed": "ERROR",
        "approval.requested": "APPROVAL_REQUIRED",
    }
    return mapping.get(event_type, "IDLE")
