from __future__ import annotations

from typing import Any

CHARACTER_STATE_BY_EVENT = {
    "connection.ready": "IDLE",
    "connection.error": "ERROR",
    "message.started": "THINKING",
    "cody.message.started": "THINKING",
    "cody.message.delta": "THINKING",
    "cody.message.completed": "SUCCESS",
    "task.started": "THINKING",
    "task.progress": "WORKING",
    "task.completed": "SUCCESS",
    "task.failed": "ERROR",
    "tool.started": "WORKING",
    "tool.completed": "THINKING",
    "tool.failed": "ERROR",
    "model.changed": "THINKING",
    "notification": "IDLE",
    "approval.requested": "APPROVAL_REQUIRED",
    "approval.resolved": "THINKING",
    "voice.transcription.started": "THINKING",
    "voice.transcription.completed": "THINKING",
    "voice.tts.started": "SPEAKING",
    "voice.tts.completed": "SPEAKING",
    "voice.audio.ready": "SPEAKING",
}


def map_event_type_to_character_state(event_type: str) -> str:
    return CHARACTER_STATE_BY_EVENT.get(str(event_type or ""), "IDLE")


def map_hermes_sse_to_codyos_event(payload: dict[str, Any]) -> dict[str, Any]:
    """Mappt ein Hermes-API-SSE-Payload auf ein CodyOS/Cody-Home-Event.

    Diese Funktion ist bewusst konservativ: Sie reicht bekannte Lifecycle-,
    Tool-, Task-, Approval-, Model- und Notification-Events weiter, ohne
    Approvals aufzulösen oder Hermes-Sicherheitsverhalten zu verändern.
    """
    event_type = str(payload.get("event") or payload.get("type") or "")
    if event_type in {
        "tool.started",
        "tool.completed",
        "tool.failed",
        "task.started",
        "task.progress",
        "task.completed",
        "task.failed",
        "model.changed",
        "notification",
        "approval.requested",
        "approval.resolved",
    }:
        return {"type": event_type, "data": payload, "state": map_event_type_to_character_state(event_type)}

    if payload.get("completed") and isinstance(payload.get("messages"), list):
        for message in reversed(payload["messages"]):
            if isinstance(message, dict) and message.get("role") == "assistant":
                return {
                    "type": "cody.message.completed",
                    "data": {"text": str(message.get("content", "")), "raw": payload},
                    "state": "SUCCESS",
                }

    text = payload.get("delta") or payload.get("text") or payload.get("content")
    if text:
        return {"type": "cody.message.delta", "data": {"text": str(text)}, "state": "THINKING"}

    if event_type in {"completed", "message.completed", "response.completed"}:
        return {"type": "cody.message.completed", "data": payload, "state": "SUCCESS"}

    return {"type": "task.progress", "data": payload, "state": "WORKING"}
