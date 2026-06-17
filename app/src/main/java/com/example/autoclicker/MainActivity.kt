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
        setContent { MaterialTheme { MainScreen() } }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val hasOverlay = Settings.canDrawOverlays(context)
    var running by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("AutoClicker", fontSize = 26.sp, fontWeight = FontWeight.Bold)

            // Adim 1: Erisebilirlik
            StepCard(
                title = "Adim 1 — Erisebilirlik Servisi",
                desc = "Sistem geneli tiklama icin zorunlu. Bir kez yapilir.",
                ok = AutoClickerService.instance != null,
                okText = "Servis aktif",
                buttonText = "Erisebilirlik Ayarlarini Ac",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )

            // Adim 2: Overlay izni
            StepCard(
                title = "Adim 2 — Overlay Izni",
                desc = "Floating butonun diger uygulamalarin uzerinde gorunmesi icin.",
                ok = hasOverlay,
                okText = "Izin verildi",
                buttonText = "Izin Ver",
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )

            HorizontalDivider()

            Text("Nasil kullanilir?", fontWeight = FontWeight.SemiBold)
            Text(
                "1. Floating Butonu Ac
" +
                "2. Mor daireyi istedigin uygulamaya suru
" +
                "3. Tiklama yapmasini istedigin yere gotur
" +
                "4. Uzun bas (0.6sn) → kirmiziya doner, tiklamaya baslar
" +
                "5. Parmagi birak → durur",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        context.startService(Intent(context, FloatingButtonService::class.java))
                        running = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !running && hasOverlay
                ) { Text("Floating Butonu Ac") }

                Button(
                    onClick = {
                        context.stopService(Intent(context, FloatingButtonService::class.java))
                        running = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = running,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Kapat") }
            }

            if (!hasOverlay) {
                Text(
                    "Once Adim 2 deki overlay iznini ver!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (running) {
                Text(
                    "Mor daire ekranda. Nereye surukleersen oraya tiklar.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun StepCard(
    title: String,
    desc: String,
    ok: Boolean,
    okText: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall)
            if (ok) {
                Text("✅ $okText", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(buttonText)
                }
            }
        }
    }
}
