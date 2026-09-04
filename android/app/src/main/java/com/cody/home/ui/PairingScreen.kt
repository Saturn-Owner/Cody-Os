package com.cody.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown once, before any credentials are stored: enter the pairing code shown
 * by `python -m cody_home_gateway.create_pairing_code` on the VPS side.
 */
@Composable
fun PairingScreen(
    isPairing: Boolean,
    errorMessage: String?,
    onSubmit: (code: String, deviceName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("Cody Home Echo") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(360.dp)
        ) {
            Text(
                text = "Cody verbinden",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pairing-Code vom Gateway eingeben",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                label = { Text("Pairing-Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Gerätename") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onSubmit(code, deviceName) },
                enabled = code.length == 6 && !isPairing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isPairing) "Verbinde..." else "Verbinden")
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
