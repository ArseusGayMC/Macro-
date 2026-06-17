package com.example.autoclicker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var xInput by remember { mutableStateOf("540") }
    var yInput by remember { mutableStateOf("960") }
    var floatRunning by remember { mutableStateOf(false) }

    val hasOverlayPerm = Settings.canDrawOverlays(context)

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("AutoClicker", fontSize = 26.sp, fontWeight = FontWeight.Bold)

            // ── Adim 1: Erisebilirlik izni ──────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasOverlayPerm)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Adim 1 — Erisebilirlik Izni", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Sistem geneli tiklama icin AccessibilityService etkinlestirilmeli.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Erisebilirlik Ayarlarini Ac") }
                }
            }

            // ── Adim 2: Overlay izni ────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasOverlayPerm)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Adim 2 — Uygulama Uzerinde Gorunme Izni", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasOverlayPerm) "✅ Izin verildi" else "❌ Bu izin olmadan floating buton calismiyor.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasOverlayPerm) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Overlay Iznini Ver") }
                    }
                }
            }

            HorizontalDivider()

            // ── Hedef koordinat ─────────────────────────────────────────
            Text("Tiklama Hedefi", fontWeight = FontWeight.SemiBold)
            Text(
                "Floating buton nereye tiklayacak? (piksel koordinati)",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = xInput,
                    onValueChange = {
                        xInput = it
                        FloatingButtonService.targetX = it.toFloatOrNull() ?: 540f
                    },
                    label = { Text("Hedef X") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = yInput,
                    onValueChange = {
                        yInput = it
                        FloatingButtonService.targetY = it.toFloatOrNull() ?: 960f
                    },
                    label = { Text("Hedef Y") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            HorizontalDivider()

            // ── Floating buton baslat / durdur ──────────────────────────
            Text("Floating Buton", fontWeight = FontWeight.SemiBold)
            Text(
                "Butonu baslat, ekranin istedigin yerine surukle.\nUzerine uzun bas → tiklama baslar.\nBirakinca durur.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        FloatingButtonService.targetX = xInput.toFloatOrNull() ?: 540f
                        FloatingButtonService.targetY = yInput.toFloatOrNull() ?: 960f
                        context.startService(Intent(context, FloatingButtonService::class.java))
                        floatRunning = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !floatRunning && hasOverlayPerm
                ) { Text("Floating Butonu Ac") }

                Button(
                    onClick = {
                        context.stopService(Intent(context, FloatingButtonService::class.java))
                        floatRunning = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = floatRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Kapat") }
            }

            if (floatRunning) {
                Text(
                    "Mor daire ekranda — surukle, uzun bas → tiklama baslar",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
