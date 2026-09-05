package com.cody.home.state

/**
 * Jeder sichtbare Zustand, in dem Cody sein kann. Das spiegelt das Event-Modell
 * aus der Cody-Home-Planung (`cody.state`-Events); echte Gateway-Events mappen
 * direkt auf diese Werte.
 */
enum class CodyState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    WORKING,
    SPEAKING,
    SUCCESS,
    ERROR,
    OFFLINE,
    APPROVAL_REQUIRED,
}

enum class ConnectionState {
    ONLINE,
    CONNECTING,
    OFFLINE,
}

/**
 * Die einzige Wahrheit für alles, was die UI über Cody rendert. Composables
 * halten keine eigene Kopie von Zustand, Statustext oder Fortschritt, sondern
 * lesen aus genau dieser CodyUiState-Instanz.
 */
data class CodyUiState(
    val state: CodyState = CodyState.CONNECTING,
    val statusText: String = "Verbinde mit Cody...",
    val message: String = "",
    val taskTitle: String? = null,
    val taskProgress: Float? = null, // 0f..1f; null = unbestimmt/keine Fortschrittsanzeige
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
)

/** Standard-Statuszeile pro Zustand, wenn kein spezifischerer Text gesetzt ist. */
fun defaultStatusText(state: CodyState): String = when (state) {
    CodyState.IDLE -> "Bereit."
    CodyState.CONNECTING -> "Verbinde mit Cody..."
    CodyState.LISTENING -> "Ich höre zu..."
    CodyState.THINKING -> "Ich denke..."
    CodyState.WORKING -> "Arbeite..."
    CodyState.SPEAKING -> "Alles läuft normal."
    CodyState.SUCCESS -> "Erledigt."
    CodyState.ERROR -> "Etwas ist schiefgelaufen."
    CodyState.OFFLINE -> "Keine Verbindung zum Server."
    CodyState.APPROVAL_REQUIRED -> "Cody braucht deine Bestätigung."
}
