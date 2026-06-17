package com.example.autoclicker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    // Senaryo 1 için tick sayacı
    var tickCount by remember { mutableIntStateOf(0) }

    // X / Y koordinat girişleri (Senaryo 2)
    var xInput by remember { mutableStateOf("540") }
    var yInput by remember { mutableStateOf("960") }

    // AccessibilityService aktif mi? (basit durum göstergesi)
    var serviceRunning by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Başlık ────────────────────────────────────────────────────
            Text(
                text = "AutoClicker",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            // ── Senaryo 1: Uygulama İçi Uzun Basma ───────────────────────
            Text(
                text = "Senaryo 1 — Uygulama İçi",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Butona basılı tutun: her 100ms'de tick artar.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Tick: $tickCount",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
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

            // ── Senaryo 2: Sistem Geneli AccessibilityService ─────────────
            Text(
                text = "Senaryo 2 — Sistem Geneli",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Önce Erişilebilirlik iznini etkinleştirin, " +
                        "sonra koordinat girin ve Başlat'a basın.",
                style = MaterialTheme.typography.bodySmall
            )

            // Koordinat girişleri
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

            // Erişilebilirlik izni butonu
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Erişilebilirlik İznini Aç →")
            }

            // Başlat / Durdur
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val x = xInput.toFloatOrNull() ?: 540f
                        val y = yInput.toFloatOrNull() ?: 960f
                        AutoClickerService.instance?.startAutoClick(x, y)
                        serviceRunning = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !serviceRunning
                ) {
                    Text("Başlat")
                }
                Button(
                    onClick = {
                        AutoClickerService.instance?.stopAutoClick()
                        serviceRunning = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = serviceRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Durdur")
                }
            }

            if (serviceRunning) {
                Text(
                    text = "⚡ Tıklama aktif → (${xInput}, ${yInput})",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
