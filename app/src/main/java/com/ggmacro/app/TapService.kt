package com.ggmacro.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class TapService : AccessibilityService() {

    companion object {
        @Volatile private var inst: TapService? = null
        fun get(): TapService? = inst
        @Volatile var running = false
    }

    private val h = Handler(Looper.getMainLooper())
    @Volatile private var active = false

    fun startTapping(x: Float, y: Float, intervalMs: Long = 50L) {
        active = false
        h.removeCallbacksAndMessages(null)
        active = true
        fire(x, y, intervalMs)
    }

    fun stopTapping() {
        active = false
        h.removeCallbacksAndMessages(null)
    }

    private fun fire(x: Float, y: Float, ms: Long) {
        if (!active) return
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        val ok = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 40L))
                .build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (active) h.postDelayed({ fire(x, y, ms) }, ms)
                }
                override fun onCancelled(g: GestureDescription?) {
                    if (active) h.postDelayed({ fire(x, y, ms) }, ms + 30L)
                }
            },
            h
        )
        if (!ok && active) h.postDelayed({ fire(x, y, ms) }, ms)
    }

    override fun onServiceConnected() { inst = this; running = true }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        active = false; h.removeCallbacksAndMessages(null)
        inst = null; running = false
        super.onDestroy()
    }
}
