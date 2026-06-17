package com.example.autoclicker

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Basılı tutma süresince her [intervalMs] ms'de bir [onTick] çağıran Compose butonu.
 *
 * Nasıl çalışır:
 *  1. pointerInput → parmak indi/kalktı bilgisini yakalar (isPressed)
 *  2. LaunchedEffect(isPressed) → isPressed=true olduğunda coroutine başlar,
 *     false olduğunda (key değişir) coroutine iptal edilir → döngü otomatik durur.
 *  3. delay() → UI thread'i kilitlemeden bekler.
 */
@Composable
fun RepeatingActionButton(
    label: String,
    intervalMs: Long = 100L,
    onTick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            while (isActive) {
                onTick()
                delay(intervalMs)
            }
        }
    }

    Button(
        modifier = Modifier
            .size(180.dp, 56.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true       // parmak indi
                        tryAwaitRelease()      // parmak kalkana kadar suspend
                        isPressed = false      // parmak kalktı → LaunchedEffect iptal
                    }
                )
            },
        onClick = {}   // pointerInput yönetiyor, onClick boş
    ) {
        Text(text = if (isPressed) "Çalışıyor…" else label)
    }
}
