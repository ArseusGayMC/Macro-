package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"
        private const val INTERVAL_MS = 100L
        private const val DURATION_MS = 50L

        var instance: AutoClickerService? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Baglandi")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        instance = null
    }

    fun start(x: Float, y: Float) {
        stop()
        job = scope.launch {
            while (isActive) {
                tap(x, y)
                delay(INTERVAL_MS)
            }
        }
        Log.d(TAG, "Baslatildi: x=$x y=$y")
    }

    fun stop() {
        job?.cancel()
        job = null
        Log.d(TAG, "Durduruldu")
    }

    private fun tap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)

        val stroke = GestureDescription.StrokeDescription(path, 0L, DURATION_MS)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, null, null)
    }
}
