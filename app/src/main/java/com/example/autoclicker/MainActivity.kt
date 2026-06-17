package com.example.autoclicker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AutoClickerScreen()
            }
        }
    }
}

@Composable
fun AutoClickerScreen() {
    val context = LocalContext.current
    var tickCount by remember { mutableIntStateOf(0) }
    var xInput by remember { mutableStateOf("540") }
    var yInput by remember { mutableStateOf("960") }
    var serviceRunning by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "AutoClicker", fontSize = 28.sp, fontWeight = FontWeight.Bold)

            HorizontalDivider()

            Text(text = "Senaryo 1 — Uygulama İçi", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "Tick: $tickCount", fontSize = 22.sp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RepeatingActionButton(
                    label = "Basılı Tut",
                    intervalMs = 100L,
                    onTick = { tickCount++ }
                )
                TextButton(onClick = { tickCount = 0 }) {
                    Text("Sıfırla")
                }
            }

            HorizontalDivider()

            Text(text = "Senaryo 2 — Sistem Geneli", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = xInput,
                    onValueChange = { xInput = it },
                    label = { Text("X") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = yInput,
                    onValueChange = { yInput = it },
                    label = { Text("Y") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Erişilebilirlik İznini Aç →")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val x = xInput.toFloatOrNull() ?: 540f
                        val y = yInput.toFloatOrNull() ?: 960f
                        AutoClickerService.instance?.start(x, y)
                        serviceRunning = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !serviceRunning
                ) { Text("Başlat") }

                Button(
                    onClick = {
                        AutoClickerService.instance?.stop()
                        serviceRunning = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = serviceRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Durdur") }
            }

            if (serviceRunning) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aktif → ($xInput, $yInput)",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
