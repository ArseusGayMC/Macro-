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
        private const val CLICK_INTERVAL_MS = 100L   // tıklamalar arası bekleme
        private const val GESTURE_DURATION_MS = 50L  // tek dokunuşun süresi (tap = kısa)

        /** Activity'den servise erişmek için statik referans. */
        var instance: AutoClickerService? = null
            private set
    }

    // Servis yaşam döngüsüne bağlı scope; onDestroy'da iptal edilir.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    // ── Yaşam döngüsü ────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Servis bağlandı")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit // kullanılmıyor

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

    // ── Genel API ────────────────────────────────────────────────────────────

    /**
     * Belirtilen (x, y) koordinatına sürekli tıklamayı başlatır.
     * Zaten çalışan bir döngü varsa önce durdurur.
     */
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

    // ── Dahili: tek dokunuş ──────────────────────────────────────────────────

    /**
     * GestureDescription ile (x, y) noktasına tek bir tap gönderir.
     *
     * Path.moveTo → tıklanacak nokta
     * StrokeDescription:
     *   startTime = 0   → hemen başla
     *   duration        → kısa = tap, uzun = long-press
     */
    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0L, GESTURE_DURATION_MS)
            )
            .build()

        val sent = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCancelled(g: GestureDescription) {
                    Log.w(TAG, "Gestur iptal edildi")
                }
            },
            null
        )

        if (!sent) Log.e(TAG, "Gestur gönderilemedi!")
    }
}
