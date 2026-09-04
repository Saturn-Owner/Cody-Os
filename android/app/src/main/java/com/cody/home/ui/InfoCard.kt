package com.cody.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Text tokens — kept in one place so every info block reads as one system. */
private val LABEL_COLOR = Color(0xFF7C8996)
private val VALUE_COLOR = Color(0xFFE8EDF2)

// Card surface — intentionally very quiet: a whisper of fill, a hairline edge.
// (Two rounds of feedback landed here: v1 had a hard 1dp stroke, v2 had no
// card at all — this is the middle ground the design brief actually asked for.)
private val CARD_BG = Color(0x0FFFFFFF)     // ~6% white
private val CARD_BORDER = Color(0x14FFFFFF) // ~8% white, 0.75dp

/** Small, quiet "smart-display card" shell — icon + label row on top, content below. */
@Composable
fun InfoCard(
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CARD_BG)
            .border(0.75.dp, CARD_BORDER, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                color = LABEL_COLOR,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.padding(top = 3.dp))
        content()
    }
}

@Composable
fun CardValueText(text: String) {
    Text(text = text, color = VALUE_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun CalendarGlyph(color: Color = LABEL_COLOR, size: androidx.compose.ui.unit.Dp = 13.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color,
            topLeft = Offset(0f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.8f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            style = Stroke(width = 1.4f),
        )
        drawLine(color, Offset(w * 0.28f, 0f), Offset(w * 0.28f, h * 0.28f), strokeWidth = 1.4f)
        drawLine(color, Offset(w * 0.72f, 0f), Offset(w * 0.72f, h * 0.28f), strokeWidth = 1.4f)
    }
}

@Composable
fun PulseGlyph(color: Color = LABEL_COLOR, size: androidx.compose.ui.unit.Dp = 13.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.5f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.45f, h * 0.1f)
            lineTo(w * 0.6f, h * 0.9f)
            lineTo(w * 0.75f, h * 0.5f)
            lineTo(w, h * 0.5f)
        }
        drawPath(path, color, style = Stroke(width = 1.6f))
    }
}
