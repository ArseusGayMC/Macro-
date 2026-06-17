package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*

/**
 * Sistem geneli oto-tıklayıcı.
 *
 * dispatchGesture + GestureDescription kullanarak hedef koordinata
 * her 100ms'de bir dokunuş gönderir.
 *
 * Kullanım:
 *   AutoClickerService.instance?.startAutoClick(x, y)
 *   AutoClickerService.instance?.stopAutoClick()
 *
 * Minimum API: 24 (Android 7.0) — dispatchGesture bu sürümde eklendi.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"
        private const val CLICK_INTERVAL_MS = 100L
        private const val GESTURE_DURATION_MS = 50L

        /** Activity'den servise erişmek için statik referans. */
        var instance: AutoClickerService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Servis bağlandı")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        Log.d(TAG, "Servis kesildi")
        stopAutoClick()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        instance = null
        Log.d(TAG, "Servis yok edildi")
    }

    fun startAutoClick(x: Float, y: Float) {
        stopAutoClick()
        Log.d(TAG, "Başlatıldı → ($x, $y)")
        clickJob = serviceScope.launch {
            while (isActive) {
                withContext(Dispatchers.Main) { dispatchTap(x, y) }
                delay(CLICK_INTERVAL_MS)
            }
        }
    }

    fun stopAutoClick() {
        clickJob?.cancel()
        clickJob = null
        Log.d(TAG, "Durduruldu")
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0L, GESTURE_DURATION_MS)
            )
            .build()

        // DÜZELTME: AccessibilityService.GestureResultCallback() — tam nitelikli ad zorunlu
        val sent = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCancelled(g: GestureDescription) {
                    Log.w(TAG, "Gestur iptal edildi")
                }
            },
            null
        )

        if (!sent) Log.e(TAG, "Gestur gönderilemedi!")
    }
}
