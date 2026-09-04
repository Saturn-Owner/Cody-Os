package com.cody.home.character

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cody — a small 2D AI-companion character, drawn natively with Canvas
 * (no bitmaps, no shaders/blur) so it stays cheap on this hardware.
 * Visual language follows the "Cody — AI Companion for Echo Show" reference:
 * dark anthracite shell, glass face-plate, large glowing cyan eyes, a ring-
 * shaped chest core, small arms, a side accent arc.
 *
 * This composable is the seam a future `RiveCharacterRenderer` would replace —
 * both would take the exact same [CharacterState] and fill this footprint.
 */

private val CHARACTER_SIZE = 300.dp

// Palette from the reference sheet.
private val ANTHRACITE = Color(0xFF1B1F24)
private val GRAPHITE = Color(0xFF2A2F36)
private val FACE_PLATE = Color(0xFF0C0E12) // near-black glass, deliberately darker than the shell
private val CYAN = Color(0xFF00E0FF)
private val TURQUOISE = Color(0xFF1DE6C1)
private val ACCENT_GLOW = Color(0xFF7FF9E5)
private val AMBER = Color(0xFFE0B45C)
private val RED = Color(0xFFE0716A)
private val SLEEP_GRAY = Color(0xFF454C55)

private fun accentFor(state: CharacterVisualState): Color = when (state) {
    CharacterVisualState.CONNECTING, CharacterVisualState.APPROVAL_REQUIRED -> AMBER
    CharacterVisualState.SUCCESS -> TURQUOISE
    CharacterVisualState.ERROR -> RED
    CharacterVisualState.OFFLINE, CharacterVisualState.SLEEPING -> SLEEP_GRAY
    else -> CYAN
}

private enum class EyeShape { OVAL, SMILE, CLOSED, SLANT, X }

private fun eyeShapeFor(state: CharacterVisualState): EyeShape = when (state) {
    CharacterVisualState.SPEAKING, CharacterVisualState.SUCCESS -> EyeShape.SMILE
    CharacterVisualState.THINKING -> EyeShape.SLANT
    CharacterVisualState.OFFLINE, CharacterVisualState.SLEEPING -> EyeShape.CLOSED
    CharacterVisualState.ERROR -> EyeShape.X
    else -> EyeShape.OVAL
}

@Composable
fun CodyCharacter(character: CharacterState, modifier: Modifier = Modifier) {
    val accent = accentFor(character.visualState)
    val vs = character.visualState
    val eyeShape = eyeShapeFor(vs)

    // Whole-character motion: bob, tilt and scale — one shared, slow clock so
    // everything stays in sync instead of each accessory drifting independently.
    val motion = rememberInfiniteTransition(label = "cody-motion")
    val bobPhase by motion.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "bobPhase",
    )
    val restBob = if (vs == CharacterVisualState.SLEEPING) 0f else sin(bobPhase) * bobAmplitude(vs)

    val targetScale = when (vs) {
        CharacterVisualState.LISTENING -> 1.05f
        CharacterVisualState.OFFLINE -> 0.92f
        CharacterVisualState.SLEEPING -> 0.88f
        else -> 1f
    }
    val scale by animateFloatAsState(targetScale, tween(400), label = "scale")

    val targetTilt = when (vs) {
        CharacterVisualState.APPROVAL_REQUIRED -> 5f
        CharacterVisualState.OFFLINE -> 3f
        CharacterVisualState.SLEEPING -> 8f
        else -> 0f
    }
    val tilt by animateFloatAsState(targetTilt, tween(500), label = "tilt")

    val lookX by animateFloatAsState(character.lookX, tween(700), label = "lookX")
    val lookY by animateFloatAsState(character.lookY, tween(500), label = "lookY")
    val coreGlow by animateFloatAsState(coreGlowTarget(vs, character.audioAmplitude), tween(150), label = "coreGlow")

    // Blinking only applies to the default oval eyes — a natural, irregular timer.
    var eyeOpen by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(vs) {
        if (eyeShapeFor(vs) != EyeShape.OVAL) {
            eyeOpen = 1f
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(2800, 6000))
            eyeOpen = 0.05f
            delay(110)
            eyeOpen = 1f
        }
    }

    var shakeX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(vs) {
        if (vs == CharacterVisualState.ERROR) {
            repeat(3) { shakeX = 7f; delay(60); shakeX = -7f; delay(60) }
            shakeX = 0f
        } else {
            shakeX = 0f
        }
    }

    Box(modifier = modifier.size(CHARACTER_SIZE)) {
        Canvas(Modifier.size(CHARACTER_SIZE)) {
            drawFloatingShadow(scale)
            translate(left = shakeX) {
                rotate(degrees = tilt, pivot = Offset(size.width / 2f, size.height * 0.62f)) {
                    drawCody(
                        accent = accent,
                        bob = restBob,
                        eyeOpen = eyeOpen,
                        eyeShape = eyeShape,
                        lookX = lookX,
                        lookY = lookY,
                        coreGlow = coreGlow,
                    )
                }
            }
        }

        when (vs) {
            CharacterVisualState.LISTENING, CharacterVisualState.SPEAKING -> AudioWaveOverlay(accent)
            CharacterVisualState.THINKING -> ThinkingParticlesOverlay(accent)
            CharacterVisualState.WORKING -> WorkingPanelsOverlay(accent)
            CharacterVisualState.SUCCESS -> CheckmarkOverlay(accent)
            CharacterVisualState.ERROR -> WarningBadgeOverlay(accent)
            CharacterVisualState.SLEEPING -> ZzzOverlay()
            else -> Unit
        }
    }
}

private fun bobAmplitude(state: CharacterVisualState): Float = when (state) {
    CharacterVisualState.THINKING, CharacterVisualState.WORKING -> 3f
    CharacterVisualState.LISTENING -> 1.5f
    CharacterVisualState.OFFLINE -> 1f
    else -> 5f
}

private fun coreGlowTarget(state: CharacterVisualState, audioAmplitude: Float): Float = when (state) {
    // Real playback amplitude (from VoicePlayer's Visualizer) pulses the core with the
    // voice when available; audioAmplitude stays 0 everywhere else, so this falls back
    // to the original flat glow whenever there's no live signal (Visualizer unavailable,
    // or any non-SPEAKING state).
    CharacterVisualState.SPEAKING -> if (audioAmplitude > 0.02f) (0.5f + audioAmplitude * 0.5f).coerceIn(0.3f, 1f) else 1f
    CharacterVisualState.LISTENING -> 1f
    CharacterVisualState.WORKING, CharacterVisualState.THINKING -> 0.75f
    CharacterVisualState.SUCCESS -> 1f
    CharacterVisualState.ERROR -> 0.6f
    CharacterVisualState.APPROVAL_REQUIRED -> 0.85f
    CharacterVisualState.CONNECTING -> 0.5f
    CharacterVisualState.OFFLINE, CharacterVisualState.SLEEPING -> 0.10f
    CharacterVisualState.IDLE -> 0.4f
}

private fun DrawScope.drawFloatingShadow(scale: Float) {
    val cx = size.width / 2f
    val cy = size.height * 0.92f
    val w = size.width * 0.30f * scale
    listOf(0.10f to 1.0f, 0.07f to 0.65f, 0.05f to 0.4f).forEach { (alpha, factor) ->
        drawOval(
            Color.Black.copy(alpha = alpha),
            topLeft = Offset(cx - w * factor, cy - w * 0.16f * factor),
            size = Size(w * 2 * factor, w * 0.32f * factor),
        )
    }
}

private fun DrawScope.drawCody(
    accent: Color,
    bob: Float,
    eyeOpen: Float,
    eyeShape: EyeShape,
    lookX: Float,
    lookY: Float,
    coreGlow: Float,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    translate(top = bob) {
        // --- floating hands (drawn first, so the body overlaps their inner edge) ---
        // Sized off `w` like everything else here, not fixed px — a fixed size looked
        // fine at the old 200dp character but read as barely-there once it grew to 300dp.
        val handY = h * 0.67f
        val handW = w * 0.095f
        val handH = w * 0.145f
        listOf(-1f, 1f).forEach { side ->
            val hx = cx + side * w * 0.33f
            drawRoundRect(
                ANTHRACITE,
                topLeft = Offset(hx - handW / 2f, handY - handH / 2f),
                size = Size(handW, handH),
                cornerRadius = CornerRadius(handW / 2f, handW / 2f),
            )
        }

        // --- body ---
        val bodyTopLeft = Offset(cx - w * 0.17f, h * 0.58f)
        val bodySize = Size(w * 0.34f, h * 0.28f)
        drawRoundRect(ANTHRACITE, topLeft = bodyTopLeft, size = bodySize, cornerRadius = CornerRadius(w * 0.15f, w * 0.15f))

        // core — a glowing ring, not a filled disc
        val coreCenter = Offset(cx, h * 0.72f)
        val coreRadius = w * 0.065f
        drawCircle(accent.copy(alpha = 0.12f * coreGlow), radius = coreRadius * 2.4f, center = coreCenter)
        drawCircle(accent.copy(alpha = 0.25f * coreGlow), radius = coreRadius * 1.6f, center = coreCenter)
        drawCircle(
            accent.copy(alpha = 0.55f + 0.45f * coreGlow),
            radius = coreRadius,
            center = coreCenter,
            style = Stroke(width = w * 0.014f),
        )

        // --- head (large, dome-shaped — most of the silhouette) ---
        val headTopLeft = Offset(cx - w * 0.33f, h * 0.10f)
        val headSize = Size(w * 0.66f, h * 0.48f)
        val headRadius = CornerRadius(w * 0.30f, w * 0.30f)
        drawRoundRect(ANTHRACITE, topLeft = headTopLeft, size = headSize, cornerRadius = headRadius)

        // side accent arc — small glowing bracket on the right of the head
        val arcRect = Offset(cx + w * 0.24f, h * 0.24f)
        drawArc(
            accent.copy(alpha = 0.85f),
            startAngle = -60f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = arcRect,
            size = Size(w * 0.10f, h * 0.14f),
            style = Stroke(width = w * 0.012f),
        )

        // face-plate: darker glass visor inset within the head
        val faceMargin = w * 0.06f
        val faceTopLeft = Offset(headTopLeft.x + faceMargin, headTopLeft.y + faceMargin * 1.3f)
        val faceSize = Size(headSize.width - faceMargin * 2, headSize.height - faceMargin * 2.1f)
        drawRoundRect(FACE_PLATE, topLeft = faceTopLeft, size = faceSize, cornerRadius = CornerRadius(w * 0.20f, w * 0.20f))

        // --- eyes ---
        val eyeCy = faceTopLeft.y + faceSize.height * 0.48f + lookY * h * 0.025f
        val eyeDx = w * 0.115f + lookX * w * 0.02f
        listOf(-1f, 1f).forEach { side ->
            val ex = cx + side * eyeDx
            drawEye(Offset(ex, eyeCy), w, accent, eyeShape, eyeOpen)
        }
    }
}

private fun DrawScope.drawEye(center: Offset, w: Float, accent: Color, shape: EyeShape, openness: Float) {
    val eyeW = w * 0.075f
    val eyeH = w * 0.11f

    // soft glow behind every eye shape
    drawCircle(accent.copy(alpha = 0.16f), radius = eyeW * 1.7f, center = center)

    when (shape) {
        EyeShape.OVAL -> {
            val h = (eyeH * openness).coerceAtLeast(3f)
            if (openness > 0.12f) {
                drawRoundRect(
                    accent,
                    topLeft = Offset(center.x - eyeW / 2f, center.y - h / 2f),
                    size = Size(eyeW, h),
                    cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f),
                )
            } else {
                drawLine(accent, Offset(center.x - eyeW / 2f, center.y), Offset(center.x + eyeW / 2f, center.y), strokeWidth = 4f)
            }
        }
        EyeShape.SMILE -> {
            drawArc(
                accent,
                startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(center.x - eyeW / 2f, center.y - eyeH * 0.3f),
                size = Size(eyeW, eyeH * 0.7f),
                style = Stroke(width = eyeW * 0.28f),
            )
        }
        EyeShape.CLOSED -> {
            drawArc(
                accent,
                startAngle = 10f, sweepAngle = 160f, useCenter = false,
                topLeft = Offset(center.x - eyeW / 2f, center.y - eyeH * 0.18f),
                size = Size(eyeW, eyeH * 0.4f),
                style = Stroke(width = eyeW * 0.22f),
            )
        }
        EyeShape.SLANT -> {
            drawLine(
                accent,
                Offset(center.x - eyeW / 2f, center.y + eyeH * 0.12f),
                Offset(center.x + eyeW / 2f, center.y - eyeH * 0.12f),
                strokeWidth = eyeW * 0.26f,
            )
        }
        EyeShape.X -> {
            val r = eyeW * 0.42f
            drawLine(accent, Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r), strokeWidth = eyeW * 0.24f)
            drawLine(accent, Offset(center.x - r, center.y + r), Offset(center.x + r, center.y - r), strokeWidth = eyeW * 0.24f)
        }
    }
}

@Composable
private fun AudioWaveOverlay(accent: Color) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "phase")
    Canvas(Modifier.size(CHARACTER_SIZE)) {
        val x = size.width * 0.86f
        val cy = size.height * 0.30f
        listOf(-1, 0, 1).forEachIndexed { i, dir ->
            val local = (phase + i * 0.25f) % 1f
            val h = size.height * (0.03f + 0.05f * sin(local * Math.PI).toFloat())
            drawLine(accent.copy(alpha = 0.85f), Offset(x, cy + dir * 14f + h / 2), Offset(x, cy + dir * 14f - h / 2), strokeWidth = 5f)
        }
    }
}

@Composable
private fun ThinkingParticlesOverlay(accent: Color) {
    val transition = rememberInfiniteTransition(label = "think")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "angle")
    Canvas(Modifier.size(CHARACTER_SIZE)) {
        val center = Offset(size.width / 2f, size.height * 0.06f)
        listOf(0f, 120f, 240f).forEach { offsetDeg ->
            val rad = Math.toRadians((angle + offsetDeg).toDouble())
            val r = size.width * 0.14f
            val p = Offset(center.x + (r * cos(rad)).toFloat(), center.y + (r * 0.35f * sin(rad)).toFloat())
            drawCircle(accent.copy(alpha = 0.8f), radius = 3.5f, center = p)
        }
    }
}

@Composable
private fun WorkingPanelsOverlay(accent: Color) {
    val transition = rememberInfiniteTransition(label = "panels")
    val scan by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "scan")
    Canvas(Modifier.size(CHARACTER_SIZE)) {
        listOf(-1f, 1f).forEachIndexed { idx, side ->
            val px = size.width / 2f + side * size.width * 0.44f
            val py = size.height * 0.34f + idx * size.height * 0.10f
            val pw = size.width * 0.13f
            val ph = size.height * 0.09f
            drawRoundRect(
                accent.copy(alpha = 0.55f),
                topLeft = Offset(px - pw / 2f, py - ph / 2f),
                size = Size(pw, ph),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.5f),
            )
            drawLine(
                accent.copy(alpha = 0.9f),
                Offset(px - pw / 2f, py - ph / 2f + ph * scan),
                Offset(px + pw / 2f, py - ph / 2f + ph * scan),
                strokeWidth = 1.5f,
            )
        }
    }
}

@Composable
private fun CheckmarkOverlay(accent: Color) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "check-scale")
    Canvas(Modifier.size(CHARACTER_SIZE)) {
        if (scale <= 0.01f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height * 0.05f
        val path = Path().apply {
            moveTo(cx - 10f * scale, cy)
            lineTo(cx - 2f * scale, cy + 8f * scale)
            lineTo(cx + 12f * scale, cy - 10f * scale)
        }
        drawPath(path, accent, style = Stroke(width = 4f))
    }
}

@Composable
private fun WarningBadgeOverlay(accent: Color) {
    Canvas(Modifier.size(CHARACTER_SIZE)) {
        val cx = size.width * 0.86f
        val cy = size.height * 0.20f
        val path = Path().apply {
            moveTo(cx, cy - 10f); lineTo(cx + 10f, cy + 8f); lineTo(cx - 10f, cy + 8f); close()
        }
        drawPath(path, accent, style = Stroke(width = 3f))
        drawLine(accent, Offset(cx, cy - 3f), Offset(cx, cy + 2f), strokeWidth = 2.5f)
        drawCircle(accent, radius = 1.5f, center = Offset(cx, cy + 5f))
    }
}

@Composable
private fun ZzzOverlay() {
    Canvas(Modifier.size(CHARACTER_SIZE)) {
        val base = Offset(size.width * 0.68f, size.height * 0.14f)
        listOf(0f to 13f, 10f to 9f, 18f to 6f).forEach { (dx, fontLike) ->
            val p = Offset(base.x + dx, base.y - dx * 0.3f)
            drawLine(SLEEP_GRAY, Offset(p.x - fontLike / 2, p.y), Offset(p.x + fontLike / 2, p.y), strokeWidth = 2f)
        }
    }
}
