package com.example.autoclicker

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ─────────────────────────────────────────────────────────────────────────────
// Senaryo 1: Uygulama İçi Sürekli Tıklama (Jetpack Compose)
//
// Kullanıcı butona basılı tuttuğu sürece her 100ms'de bir onTick() çağrılır.
// Parmak kalktığında döngü durur. UI thread'i kilitlenmez.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Basılı tutma süresince her [intervalMs] milisaniyede bir [onTick] fonksiyonunu
 * çalıştıran Compose bileşeni.
 *
 * @param label       Buton üzerinde gösterilecek metin
 * @param intervalMs  Tick aralığı (ms). Varsayılan: 100ms
 * @param onTick      Her aralıkta çağrılacak lambda (suspend değil — ana iş parçacığından güvenli)
 */
@Composable
fun RepeatingActionButton(
    label: String,
    intervalMs: Long = 100L,
    onTick: () -> Unit
) {
    // isPressed: parmağın butonda olup olmadığını izler
    var isPressed by remember { mutableStateOf(false) }

    // isPressed true olduğunda LaunchedEffect döngüyü başlatır,
    // false olduğunda (key değişir) coroutine iptal edilir → döngü durur.
    LaunchedEffect(isPressed) {
        if (isPressed) {
            // coroutine aktif olduğu ve basılı tutulduğu sürece çalış
            while (isActive) {
                onTick()            // kullanıcı tanımlı aksiyonu çalıştır
                delay(intervalMs)   // bir sonraki tick'e kadar bekle (UI thread'ini bloke etmez)
            }
        }
    }

    Button(
        modifier = Modifier
            .size(180.dp, 60.dp)
            // pointerInput: ham dokunma olaylarını dinler
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { _ ->
                        isPressed = true        // parmak indi → döngüyü başlat
                        tryAwaitRelease()       // parmak kalkana kadar bekle
                        isPressed = false       // parmak kalktı → döngüyü durdur
                    }
                )
            },
        // Butonun kendi onClick'ini devre dışı bırakıyoruz;
        // tüm mantık pointerInput üzerinden yürütülüyor.
        onClick = {}
    ) {
        Text(text = if (isPressed) "Çalışıyor…" else label)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Örnek kullanım: sayaç ekranı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LongPressScreen() {
    var tickCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tick sayısı: $tickCount",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Her 100ms'de tick sayacını bir artır
        RepeatingActionButton(
            label = "Basılı Tut",
            intervalMs = 100L,
            onTick = { tickCount++ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { tickCount = 0 }) {
            Text("Sıfırla")
        }
    }
}
