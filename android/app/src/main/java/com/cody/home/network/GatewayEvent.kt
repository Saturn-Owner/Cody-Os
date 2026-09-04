package com.cody.home.network

import org.json.JSONObject

/** Mirrors the CodyHomeEvent envelope: {version, type, timestamp, request_id, data}. */
data class GatewayEvent(
    val version: Int,
    val type: String,
    val timestamp: String,
    val requestId: String,
    val data: JSONObject,
) {
    companion object {
        fun parse(raw: String): GatewayEvent? = runCatching {
            val obj = JSONObject(raw)
            GatewayEvent(
                version = obj.optInt("version", 1),
                type = obj.getString("type"),
                timestamp = obj.optString("timestamp", ""),
                requestId = obj.optString("request_id", ""),
                data = obj.optJSONObject("data") ?: JSONObject(),
            )
        }.getOrNull()
    }
}

/** Event type strings from the Gateway protocol — grouped here so nothing is typo'd twice. */
object GatewayEventType {
    const val CONNECTION_READY = "connection.ready"
    const val CONNECTION_ERROR = "connection.error"
    const val CODY_STATE = "cody.state"
    const val MESSAGE_STARTED = "cody.message.started"
    const val MESSAGE_DELTA = "cody.message.delta"
    const val MESSAGE_COMPLETED = "cody.message.completed"
    const val TASK_STARTED = "task.started"
    const val TASK_PROGRESS = "task.progress"
    const val TASK_COMPLETED = "task.completed"
    const val TASK_FAILED = "task.failed"
    const val TOOL_STARTED = "tool.started"
    const val TOOL_COMPLETED = "tool.completed"
    const val TOOL_FAILED = "tool.failed"
    const val MODEL_CHANGED = "model.changed"
    const val NOTIFICATION = "notification"
    const val APPROVAL_REQUESTED = "approval.requested"
    const val APPROVAL_RESOLVED = "approval.resolved"
}
