package com.cody.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Cody Home is a dark, always-on smart-display UI — there is no light variant by design.
private val CodyDarkColors = darkColorScheme(
    background = androidx.compose.ui.graphics.Color(0xFF0B0D10),
    surface = androidx.compose.ui.graphics.Color(0xFF15181D),
    primary = androidx.compose.ui.graphics.Color(0xFF3DDC97),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE8EDF2),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE8EDF2),
)

@Composable
fun CodyHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CodyDarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
