package com.cody.home.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Die eine Stelle, der [CodyUiState] gehört. Die UI liest diesen State, ändert
 * ihn aber nicht direkt.
 */
class CodyStateHolder {
    var uiState by mutableStateOf(CodyUiState())
        private set

    /** Setzt einen neuen Cody-Zustand inklusive sinnvoller Standardwerte. */
    fun setState(
        state: CodyState,
        statusText: String = defaultStatusText(state),
        message: String = uiState.message,
        taskTitle: String? = null,
        taskProgress: Float? = null,
    ) {
        uiState = uiState.copy(
            state = state,
            statusText = statusText,
            message = message,
            taskTitle = taskTitle,
            taskProgress = taskProgress,
            connectionState = when (state) {
                CodyState.OFFLINE -> ConnectionState.OFFLINE
                CodyState.CONNECTING -> ConnectionState.CONNECTING
                else -> ConnectionState.ONLINE
            },
        )
    }

    /** Demo-Helfer: simuliert eine laufende Aufgabe mit Fortschrittsanzeige. */
    fun simulateWorkingTask(title: String) {
        setState(CodyState.WORKING, taskTitle = title, taskProgress = 0f)
    }

    fun updateTaskProgress(progress: Float) {
        if (uiState.state == CodyState.WORKING) {
            uiState = uiState.copy(taskProgress = progress.coerceIn(0f, 1f))
        }
    }

    /**
     * Aktualisiert nur die angezeigte Nachricht, ohne einen Zustandswechsel zu
     * erzwingen. Die cody.state-Events des Servers steuern den Zustand.
     */
    fun setMessage(message: String) {
        uiState = uiState.copy(message = message)
    }
}
