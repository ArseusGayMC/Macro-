package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"
        private const val CLICK_INTERVAL_MS = 100L
        private const val GESTURE_DURATION_MS = 50L

        var instance: AutoClickerService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Servis baglandi")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopAutoClick()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        instance = null
    }

    fun startAutoClick(x: Float, y: Float) {
        stopAutoClick()
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
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, GESTURE_DURATION_MS))
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
        if (!sent) Log.e(TAG, "Gestur gonderilemedi!")
    }
}
