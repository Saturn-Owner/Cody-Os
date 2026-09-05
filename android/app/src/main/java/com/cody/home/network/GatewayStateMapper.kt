package com.cody.home.network

import com.cody.home.state.CodyState
import com.cody.home.state.CodyStateHolder

/**
 * Übersetzt rohe [GatewayEvent]s auf den bestehenden [CodyStateHolder].
 * Das ist die einzige Brücke zwischen Netzwerk-Layer und State Machine.
 * UI und [CharacterController] sehen [GatewayEvent]s nie direkt.
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
            // tool.* / approval.resolved: in V1 nicht direkt auf CodyUiState gemappt.
            // connection.* läuft über GatewayRepository.connectionState; für den Rest gibt es
            // noch keinen eigenen V1-UI-Slot.
            else -> Unit
        }
    }

    /** Server sendet State-Namen passend zu [CodyState], ohne Beachtung der Groß-/Kleinschreibung. */
    private fun mapState(raw: String): CodyState? =
        runCatching { CodyState.valueOf(raw.uppercase()) }.getOrNull()
}
