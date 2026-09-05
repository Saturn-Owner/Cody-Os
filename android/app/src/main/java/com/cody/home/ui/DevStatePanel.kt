package com.cody.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.cody.home.state.CodyState

/**
 * Verstecktes Entwicklerpanel: jeder [CodyState] als Button (Mock, ohne
 * Gateway), plus ein Textfeld für den echten `/message`-Roundtrip. Öffnet sich
 * über 5 schnelle Taps auf Cody und ist am Aufruf über BuildConfig.DEBUG
 * geschützt.
 */
@Composable
fun DevStatePanel(
    onSelect: (CodyState) -> Unit,
    onSendMessage: (String) -> Unit,
    onForgetDevice: () -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember { mutableStateOf("Antworte nur mit Hallo Echo") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE0B0D10))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                text = "Dev · Cody-Zustand wählen",
                color = Color(0xFFE8EDF2),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(CodyState.entries.toList()) { state ->
                    Button(
                        onClick = { onSelect(state) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D2229)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(state.name, fontSize = 12.sp, color = Color(0xFFE8EDF2))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Dev · Echte Gateway-Anfrage",
                color = Color(0xFFE8EDF2),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSendMessage(draft) }) { Text("Senden") }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onForgetDevice) {
                    Text("Gerät vergessen (Re-Pairing)", color = Color(0xFFE0716A), fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text("Schließen", color = Color(0xFF8A93A0))
                }
            }
        }
    }
}
