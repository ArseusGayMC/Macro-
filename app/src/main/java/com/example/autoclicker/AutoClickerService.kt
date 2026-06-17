package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*

// ─────────────────────────────────────────────────────────────────────────────
// Senaryo 2: Sistem Geneli Oto-Tıklayıcı (AccessibilityService)
//
// dispatchGesture + GestureDescription kullanarak hedef koordinata
// sürekli tıklama yapar. Tıklamalar arası bekleme coroutine ile
// yönetilir; UI thread'i asla bloke edilmez.
//
// Minimum API: 24 (dispatchGesture Android 7.0'da eklendi)
// ─────────────────────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.N)
class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"

        // Tıklamalar arası bekleme süresi (ms)
        private const val CLICK_INTERVAL_MS = 100L

        // Dokunuşun ne kadar süre basılı tutulacağı (ms)
        // Kısa tut → gerçek "tap" gibi davranır
        private const val GESTURE_DURATION_MS = 50L
    }

    // Servis yaşam döngüsüne bağlı bir CoroutineScope.
    // onDestroy'da iptal edilerek kaynak sızıntısı önlenir.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Çalışan tıklama işini tutan referans; durdurma için kullanılır.
    private var clickJob: Job? = null

    // ── Servis bağlandığında çağrılır ────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AutoClickerService bağlandı")
        // Bağlanır bağlanmaz varsayılan koordinatlara tıklamayı başlat
        startAutoClick(x = 540f, y = 960f)
    }

    // ── Erişilebilirlik olaylarını yakala (zorunlu override) ─────────────────
    // Bu örnekte olayları işlemiyoruz; sadece gestur kullanıyoruz.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    // ── Servisi kesmek gerektiğinde ──────────────────────────────────────────
    override fun onInterrupt() {
        Log.d(TAG, "AutoClickerService kesildi")
        stopAutoClick()
    }

    // ── Servis yok edildiğinde coroutine scope'u temizle ────────────────────
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "AutoClickerService yok edildi, scope iptal edildi")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dışarıdan çağrılabilen API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Belirtilen (x, y) ekran koordinatına sürekli tıklamayı başlatır.
     * Zaten çalışan bir iş varsa önce durdurur.
     *
     * @param x  Hedef X koordinatı (piksel, ekranın sol üst köşesinden)
     * @param y  Hedef Y koordinatı (piksel, ekranın sol üst köşesinden)
     */
    fun startAutoClick(x: Float, y: Float) {
        stopAutoClick() // Varsa önceki işi durdur
        Log.d(TAG, "Oto-tıklama başladı → ($x, $y)")

        clickJob = serviceScope.launch {
            while (isActive) {
                // Ana iş parçacığında gestur gönder
                withContext(Dispatchers.Main) {
                    dispatchSingleTap(x, y)
                }
                delay(CLICK_INTERVAL_MS) // Bir sonraki tıklamaya kadar bekle
            }
        }
    }

    /** Çalışan oto-tıklama döngüsünü durdurur. */
    fun stopAutoClick() {
        clickJob?.cancel()
        clickJob = null
        Log.d(TAG, "Oto-tıklama durduruldu")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dahili yardımcı — tek bir dokunuş gesturunu gönderir
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [dispatchGesture] kullanarak (x, y) noktasına tek bir dokunuş gestur gönderir.
     *
     * GestureDescription.StrokeDescription:
     *   • path       → tıklanacak nokta (Path.moveTo ile tanımlanır)
     *   • startTime  → gesturun başlangıç gecikmesi (ms); 0 = hemen
     *   • duration   → dokunuşun süresi (ms); kısa = tap, uzun = long-press
     */
    private fun dispatchSingleTap(x: Float, y: Float) {
        // 1. Tıklanacak noktayı bir Path nesnesiyle tanımla
        val tapPath = Path().apply {
            moveTo(x, y)
        }

        // 2. GestureDescription oluştur ve stroke ekle
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    tapPath,
                    /* startTime = */ 0L,
                    /* duration  = */ GESTURE_DURATION_MS
                )
            )
            .build()

        // 3. Gesturu sistem tarafına gönder
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    // Gestur başarıyla tamamlandı (isteğe bağlı log)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Gestur iptal edildi")
                }
            },
            null // Handler null → callback ana iş parçacığında çalışır
        )

        if (!dispatched) {
            Log.e(TAG, "Gestur gönderilemedi! Servisin aktif ve etkinleştirilmiş olduğundan emin olun.")
        }
    }
}
