package com.cody.home.character

import com.cody.home.state.CodyState
import com.cody.home.state.CodyUiState

/** Cody's own mood, layered on top of [CodyState] — cosmetic, not functional yet. */
enum class CodyEmotion { NEUTRAL, FOCUSED, HAPPY, CONCERNED, CURIOUS, SAD }

/** Mirrors [CodyState] plus a display-only SLEEPING state used for Ambient mode. */
enum class CharacterVisualState { IDLE, CONNECTING, LISTENING, THINKING, WORKING, SPEAKING, SUCCESS, ERROR, OFFLINE, APPROVAL_REQUIRED, SLEEPING }

/**
 * Everything the character rendering needs to know, decoupled from [CodyUiState].
 * This is the `CharacterRenderState` from the design brief — named [CharacterState]
 * here since it predates that brief, same shape:
 *   state (→ [visualState]), emotion, lookX, lookY, activityLevel, audioAmplitude
 * plus two fields the brief's example didn't list but the behavior spec needs:
 * [blinkTrigger] (fires one deliberate blink, e.g. on a state change) and
 * [attention] (posture "alertness", used for the idle/listening/offline slump).
 *
 * This is the seam for a future Rive character — see RIVE_MIGRATION.md-style
 * notes in [CharacterController] for exactly which fields map to which inputs.
 */
data class CharacterState(
    val visualState: CharacterVisualState,
    val lookX: Float = 0f,       // -1f (left, toward the info cards) .. 1f (right)
    val lookY: Float = 0f,       // -1f (up) .. 1f (down)
    val audioAmplitude: Float = 0f, // 0f..1f, driven by TTS output later
    val activityLevel: Float = 0f,  // 0f..1f, general "how busy" intensity
    val emotion: CodyEmotion = CodyEmotion.NEUTRAL,
    val blinkTrigger: Long = 0L,    // incrementing this fires one blink
    val attention: Float = 0f,      // 0f..1f, how "alert" the posture is
)

/**
 * Derives the character's animation parameters from the app's single source of
 * truth ([CodyUiState]) plus whether Ambient mode is active.
 *
 * ---- Rive migration plan (not implemented yet) ----
 * When a real Rive asset exists, `RiveCharacterRenderer` takes the exact same
 * [CharacterState] this produces and, instead of driving a Canvas, sets Rive
 * State Machine inputs on every recomposition:
 *   - visualState   → a Rive "State" number/enum input selecting the state-machine branch
 *   - lookX, lookY  → two Rive number inputs driving eye-target bones
 *   - audioAmplitude→ a Rive number input driving a mouth/wave blend
 *   - activityLevel → a Rive number input scaling motion speed/intensity
 *   - emotion       → a Rive number/enum input selecting an eye-shape blend
 *   - blinkTrigger  → a Rive boolean "trigger" input, fired on change
 *   - attention     → a Rive number input driving posture lean/scale
 * No caller of [derive] or of `CodyCharacter(character = ...)` would need to
 * change — only the composable's internals get swapped out.
 */
object CharacterController {
    /** [liveAudioAmplitude] (0f..1f) is only meaningful while SPEAKING — see [CharacterState.audioAmplitude]. */
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
