package com.cody.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cody.home.character.CharacterState
import com.cody.home.character.CodyCharacter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reduced "pretend standby" look — device stays fully on underneath
 * (FLAG_KEEP_SCREEN_ON in MainActivity), this is purely visual. As little
 * motion as possible: no dashboard cards, Cody asleep, slow clock tick.
 */
@Composable
fun AmbientScreen(character: CharacterState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                color = Color(0xFF4A4F55),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN).format(now).replaceFirstChar { it.uppercase() },
                color = Color(0xFF2E3238),
                fontSize = 14.sp,
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            CodyCharacter(character = character)
        }
    }
}
