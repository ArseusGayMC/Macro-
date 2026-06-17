package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

/**
 * Sistem geneli oto-tıklayıcı — AccessibilityService + dispatchGesture.
 *
 * minSdk = 24 olduğu için @RequiresApi anotasyonu gereksizdir,
 * tüm cihazlar zaten API 24+ garantilidir.
 *
 * Kullanım:
 *   AutoClickerService.instance?.startAutoClick(x, y)
 *   AutoClickerService.instance?.stopAutoClick()
 */
class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"
        private const val CLICK_INTERVAL_MS = 100L   // tıklamalar arası bekleme (ms)
        private const val GESTURE_DURATION_MS = 50L  // tek tap süresi (ms)

        /** MainActivity'den servise erişmek için statik referans. */
        var instance: AutoClickerService? = null
            private set
    }

    // Servis yaşam döngüsüne bağlı coroutine scope
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    // ─── Yaşam döngüsü ───────────────────────────────────────────────────────

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

    // ─── Genel API ───────────────────────────────────────────────────────────

    /** Belirtilen (x, y) koordinatına sürekli tıklamayı başlatır. */
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

    /** Çalışan tıklama döngüsünü durdurur. */
    fun stopAutoClick() {
        clickJob?.cancel()
        clickJob = null
        Log.d(TAG, "Durduruldu")
    }

    // ─── Dahili: tek dokunuş ─────────────────────────────────────────────────

    /**
     * GestureDescription + StrokeDescription ile (x, y) noktasına tek tap gönderir.
     * AccessibilityService.GestureResultCallback → iç sınıf, tam nitelikli ad zorunlu.
     */
    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,                   // startTime: hemen başla
                    GESTURE_DURATION_MS   // duration: kısa = tap
                )
            )
            .build()

        val sent = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCancelled(g: GestureDescription) {
                    Log.w(TAG, "Gestur iptal edildi")
                }
            },
            null
        )

        if (!sent) Log.e(TAG, "Gestur gönderilemedi — servis aktif mi?")
    }
}
