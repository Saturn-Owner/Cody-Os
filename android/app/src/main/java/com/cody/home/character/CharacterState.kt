package com.cody.home.character

import com.cody.home.state.CodyState
import com.cody.home.state.CodyUiState

/** Codys Stimmung zusätzlich zu [CodyState] — aktuell kosmetisch, nicht funktional. */
enum class CodyEmotion { NEUTRAL, FOCUSED, HAPPY, CONCERNED, CURIOUS, SAD }

/** Spiegelt [CodyState] plus den reinen Anzeigezustand SLEEPING für den Ambient-Modus. */
enum class CharacterVisualState { IDLE, CONNECTING, LISTENING, THINKING, WORKING, SPEAKING, SUCCESS, ERROR, OFFLINE, APPROVAL_REQUIRED, SLEEPING }

/**
 * Alles, was das Character-Rendering wissen muss, entkoppelt von [CodyUiState].
 * Das entspricht dem `CharacterRenderState` aus dem Design-Briefing; hier heißt
 * es [CharacterState], weil der Name älter ist:
 *   state (→ [visualState]), emotion, lookX, lookY, activityLevel, audioAmplitude
 * plus zwei Felder, die das Beispiel nicht enthielt, die das Verhalten aber
 * braucht: [blinkTrigger] für bewusstes Blinzeln und [attention] für die
 * aufrechte/aufmerksame Haltung.
 *
 * Das ist die Nahtstelle für einen späteren Rive-Character; siehe die Notizen
 * in [CharacterController] zum Mapping der Felder.
 */
data class CharacterState(
    val visualState: CharacterVisualState,
    val lookX: Float = 0f,       // -1f (links, Richtung Infokarten) .. 1f (rechts)
    val lookY: Float = 0f,       // -1f (oben) .. 1f (unten)
    val audioAmplitude: Float = 0f, // 0f..1f, später durch TTS-Ausgabe gesteuert
    val activityLevel: Float = 0f,  // 0f..1f, allgemeine Aktivitätsintensität
    val emotion: CodyEmotion = CodyEmotion.NEUTRAL,
    val blinkTrigger: Long = 0L,    // Erhöhen löst ein Blinzeln aus
    val attention: Float = 0f,      // 0f..1f, wie aufmerksam die Haltung ist
)

/**
 * Leitet die Animationsparameter des Characters aus der einzigen UI-Wahrheit
 * ([CodyUiState]) und dem Ambient-Modus ab.
 *
 * ---- Rive-Migrationsplan (noch nicht implementiert) ----
 * Sobald ein echtes Rive-Asset existiert, nutzt `RiveCharacterRenderer` denselben
 * [CharacterState] und setzt statt Canvas-Rendering die passenden Rive-
 * State-Machine-Inputs. Aufrufer von [derive] oder `CodyCharacter(...)` müssten
 * dafür nicht geändert werden.
 */
object CharacterController {
    /** [liveAudioAmplitude] (0f..1f) ist nur während SPEAKING relevant. */
    fun derive(uiState: CodyUiState, isAmbient: Boolean, liveAudioAmplitude: Float = 0f): CharacterState {
        if (isAmbient) {
            return CharacterState(visualState = CharacterVisualState.SLEEPING, attention = 0f)
        }
        val visual = when (uiState.state) {
            CodyState.IDLE -> CharacterVisualState.IDLE
            CodyState.CONNECTING -> CharacterVisualState.CONNECTING
            CodyState.LISTENING -> CharacterVisualState.LISTENING
            CodyState.THINKING -> CharacterVisualState.THINKING
            CodyState.WORKING -> CharacterVisualState.WORKING
            CodyState.SPEAKING -> CharacterVisualState.SPEAKING
            CodyState.SUCCESS -> CharacterVisualState.SUCCESS
            CodyState.ERROR -> CharacterVisualState.ERROR
            CodyState.OFFLINE -> CharacterVisualState.OFFLINE
            CodyState.APPROVAL_REQUIRED -> CharacterVisualState.APPROVAL_REQUIRED
        }
        return CharacterState(
            visualState = visual,
            lookX = if (visual == CharacterVisualState.APPROVAL_REQUIRED) -0.7f else 0f,
            lookY = if (visual == CharacterVisualState.THINKING) -0.6f else 0f,
            audioAmplitude = if (visual == CharacterVisualState.SPEAKING) liveAudioAmplitude else 0f,
            activityLevel = when (visual) {
                CharacterVisualState.WORKING, CharacterVisualState.LISTENING -> 0.8f
                CharacterVisualState.THINKING -> 0.5f
                else -> 0.1f
            },
            emotion = when (visual) {
                CharacterVisualState.SUCCESS -> CodyEmotion.HAPPY
                CharacterVisualState.ERROR -> CodyEmotion.CONCERNED
                CharacterVisualState.OFFLINE -> CodyEmotion.SAD
                CharacterVisualState.LISTENING, CharacterVisualState.THINKING -> CodyEmotion.FOCUSED
                CharacterVisualState.APPROVAL_REQUIRED -> CodyEmotion.CURIOUS
                else -> CodyEmotion.NEUTRAL
            },
            attention = when (visual) {
                CharacterVisualState.LISTENING, CharacterVisualState.APPROVAL_REQUIRED -> 1f
                CharacterVisualState.OFFLINE -> 0f
                else -> 0.4f
            },
        )
    }
}
