package com.cody.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cody.home.character.CharacterState
import com.cody.home.character.CodyCharacter
import com.cody.home.state.CodyState
import com.cody.home.state.CodyUiState
import com.cody.home.state.ConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The main "awake" dashboard: compact smart-display cards on the left, Cody
 * — now a proper character, not a text label — taking the right third.
 */
@Composable
fun DashboardScreen(
    uiState: CodyUiState,
    character: CharacterState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
    characterTapModifier: Modifier = Modifier,
    isRecording: Boolean = false,
    onMicTap: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 18.dp)
        ) {
            ClockRow()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GreetingText()
                    InfoCard(label = "Nächster Termin", icon = { CalendarGlyph() }) {
                        CardValueText("Keine Termine")
                    }
                    InfoCard(label = "Cody Status", icon = { StatusDot(uiState.connectionState, small = true) }) {
                        CodyStatusValue(uiState.connectionState)
                    }
                    InfoCard(label = "Aktuelle Aktivität", icon = { PulseGlyph() }) {
                        ActivityContent(uiState)
                    }
                }

                Spacer(Modifier.width(18.dp))

                Box(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    CodyCharacter(character = character, modifier = characterTapModifier)
                }
            }
        }

        if (onMicTap != null) {
            MicButton(
                isRecording = isRecording,
                onTap = onMicTap,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 26.dp, bottom = 18.dp),
            )
        }

        if (uiState.state == CodyState.APPROVAL_REQUIRED) {
            ApprovalOverlay(
                message = uiState.message.ifBlank { uiState.statusText },
                onApprove = onApprove,
                onDeny = onDeny,
            )
        }
    }
}

/** V1 voice entry point — tap to start listening, tap again to stop and send. */
@Composable
private fun MicButton(isRecording: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val background = if (isRecording) Color(0xFFE0716A) else Color(0x1FFFFFFF)
    val iconColor = if (isRecording) Color(0xFF14100E) else Color(0xFFE8EDF2)

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        MicGlyph(color = iconColor)
    }
}

@Composable
private fun MicGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // capsule body
        drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.32f, 0f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f, w * 0.18f),
        )
        // stand
        drawArc(
            color,
            startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f),
        )
        drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.78f), androidx.compose.ui.geometry.Offset(w * 0.5f, h), strokeWidth = w * 0.09f)
    }
}

@Composable
private fun ClockRow() {
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Date()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(15_000)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 38.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = SimpleDateFormat("EEEE", Locale.GERMAN).format(now).replaceFirstChar { it.uppercase() },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun GreetingText() {
    val hour = androidx.compose.runtime.remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        else -> "Guten Abend"
    }
    Text(
        text = greeting,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun StatusDot(connectionState: ConnectionState, small: Boolean = false) {
    val color = when (connectionState) {
        ConnectionState.ONLINE -> MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> Color(0xFFE0B45C)
        ConnectionState.OFFLINE -> Color(0xFF5A6470)
    }
    val dotSize = if (small) 8.dp else 10.dp

    if (connectionState == ConnectionState.CONNECTING) {
        val transition = rememberInfiniteTransition(label = "dot-pulse")
        val alpha by transition.animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(700, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "dot-alpha",
        )
        Box(
            Modifier
                .size(dotSize)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = alpha))
        )
    } else {
        Box(Modifier.size(dotSize).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
private fun CodyStatusValue(connectionState: ConnectionState) {
    val label = when (connectionState) {
        ConnectionState.ONLINE -> "Online"
        ConnectionState.CONNECTING -> "Verbindet..."
        ConnectionState.OFFLINE -> "Offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(connectionState)
        Spacer(Modifier.width(6.dp))
        CardValueText(label)
    }
}

@Composable
private fun ActivityContent(uiState: CodyUiState) {
    if (uiState.state == CodyState.WORKING) {
        AnimatedContent(
            targetState = uiState.taskTitle ?: uiState.statusText,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "task-title",
        ) { title -> CardValueText(title) }

        val animatedProgress by animateFloatAsState(uiState.taskProgress ?: 0f, tween(400), label = "progress")
        if (uiState.taskProgress != null) {
            Spacer(Modifier.height(6.dp))
            // Hand-rolled instead of Material3's LinearProgressIndicator: that one draws a
            // "stop" dot fixed at the track's end regardless of progress, which reads as
            // "always nearly full" at low values — this starts genuinely empty at 0.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    } else {
        AnimatedContent(
            targetState = uiState.message.ifBlank { uiState.statusText },
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "activity-text",
        ) { text -> CardValueText("\"$text\"") }
    }
}

@Composable
private fun ApprovalOverlay(message: String, onApprove: () -> Unit, onDeny: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC0B0D10)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF15181D))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Bestätigung erforderlich", color = Color(0xFFE0B45C), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFFE8EDF2), fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onDeny) { Text("Ablehnen") }
                Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3DDC97))) {
                    Text("Genehmigen", color = Color(0xFF0B0D10))
                }
            }
        }
    }
}
