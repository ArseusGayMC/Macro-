package com.ggmacro.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: MacroAccessibilityService? = null
        fun getInstance(): MacroAccessibilityService? = instance
        val isRunning = MutableStateFlow(false)
    }

    private val tapHandler = Handler(Looper.getMainLooper())

    @Volatile private var tapping = false

    // ── Public API ────────────────────────────────────────────────────────

    fun startTapLoop(x: Float, y: Float, durationMs: Long, delayMs: Long) {
        tapping = true
        tap(x, y, durationMs.coerceAtLeast(1L), delayMs.coerceAtLeast(16L))
    }

    fun stopTapLoop() {
        tapping = false
        tapHandler.removeCallbacksAndMessages(null)
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun tap(x: Float, y: Float, dur: Long, delay: Long) {
        if (!tapping) return
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        val ok = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, dur))
                .build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (tapping) tapHandler.postDelayed({ tap(x, y, dur, delay) }, delay)
                }
                override fun onCancelled(g: GestureDescription?) {
                    // another gesture was in flight — wait a bit longer
                    if (tapping) tapHandler.postDelayed({ tap(x, y, dur, delay) }, delay + 40L)
                }
            },
            tapHandler
        )
        if (!ok && tapping) {
            tapHandler.postDelayed({ tap(x, y, dur, delay) }, delay)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        tapping = false
        instance = this
        isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        tapping = false
        tapHandler.removeCallbacksAndMessages(null)
        instance = null
        isRunning.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        tapping = false
        tapHandler.removeCallbacksAndMessages(null)
        instance = null
        isRunning.value = false
    }
}
