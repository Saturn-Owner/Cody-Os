package com.cody.home.network

import com.cody.home.state.CodyState
import com.cody.home.state.CodyStateHolder

/**
 * Translates raw [GatewayEvent]s onto the existing [CodyStateHolder] — the only
 * place that bridges the network layer and the state machine. Neither the UI
 * nor [CharacterController] ever see a [GatewayEvent] directly.
 */
object GatewayStateMapper {

    fun apply(event: GatewayEvent, stateHolder: CodyStateHolder) {
        when (event.type) {
            GatewayEventType.CODY_STATE -> {
                val raw = event.data.optString("state", "")
                val message = event.data.optString("message", "")
                mapState(raw)?.let { mapped ->
                    stateHolder.setState(mapped, message = message.ifBlank { stateHolder.uiState.message })
                }
            }

            GatewayEventType.MESSAGE_COMPLETED -> {
                val text = event.data.optString("text", "")
                if (text.isNotBlank()) stateHolder.setMessage(text)
            }

            GatewayEventType.TASK_STARTED -> {
                val title = event.data.optString("title", event.data.optString("message", "Arbeite..."))
                stateHolder.simulateWorkingTask(title)
            }

            GatewayEventType.TASK_PROGRESS -> {
                val progress = event.data.optDouble("progress", -1.0)
                if (progress in 0.0..1.0) stateHolder.updateTaskProgress(progress.toFloat())
            }

            GatewayEventType.APPROVAL_REQUESTED -> {
                val message = event.data.optString("command", event.data.optString("message", "Bestätigung erforderlich."))
                stateHolder.setState(CodyState.APPROVAL_REQUIRED, message = message)
            }

            GatewayEventType.NOTIFICATION -> {
                val text = event.data.optString("text", "")
                if (text.isNotBlank()) stateHolder.setMessage(text)
            }

            // connection.ready / connection.error / model.changed / task.completed / task.failed /
            // tool.* / approval.resolved: not mapped onto CodyUiState in V1 — connection.* is
            // handled by GatewayRepository's connectionState instead; the rest have no V1 UI slot yet.
            else -> Unit
        }
    }

    /** Server sends state names matching [CodyState] entries case-insensitively (e.g. "WORKING", "thinking"). */
    private fun mapState(raw: String): CodyState? =
        runCatching { CodyState.valueOf(raw.uppercase()) }.getOrNull()
}
