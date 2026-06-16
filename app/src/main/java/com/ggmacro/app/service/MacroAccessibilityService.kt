package com.ggmacro.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ggmacro.app.data.model.ActionType
import com.ggmacro.app.data.model.MacroAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MacroA11yService"

        @Volatile
        private var instance: MacroAccessibilityService? = null
        fun getInstance(): MacroAccessibilityService? = instance

        val isRunning = MutableStateFlow(false)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    // ── Trigger tap-loop ─────────────────────────────────────────────────
    @Volatile private var tapLooping = false

    fun startTapLoop(x: Float, y: Float, duration: Long, delay: Long) {
        if (tapLooping) return
        tapLooping = true
        scheduleTap(x, y, duration.coerceAtLeast(1L), delay.coerceAtLeast(16L))
    }

    fun stopTapLoop() {
        tapLooping = false
    }

    private fun scheduleTap(x: Float, y: Float, duration: Long, delay: Long) {
        if (!tapLooping) return
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (tapLooping) handler.postDelayed({ scheduleTap(x, y, duration, delay) }, delay)
                }
                override fun onCancelled(g: GestureDescription?) {
                    if (tapLooping) handler.postDelayed({ scheduleTap(x, y, duration, delay) }, delay)
                }
            }, handler
        )
        if (!ok && tapLooping) {
            handler.postDelayed({ scheduleTap(x, y, duration, delay) }, delay)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning.value = true
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        tapLooping = false
        instance = null
        isRunning.value = false
        _isPlaying.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        tapLooping = false
        instance = null
        isRunning.value = false
        stopPlayback()
    }

    // ── Macro playback ─────────────────────────────────────────────────────
    fun playMacro(
        actions: List<MacroAction>,
        loopCount: Int,
        playbackSpeed: Float,
        onComplete: () -> Unit
    ) {
        stopPlayback()
        _isPlaying.value = true
        var repeatCount = 0
        val maxLoops = if (loopCount == -1) Int.MAX_VALUE else loopCount

        fun runLoop() {
            if (!_isPlaying.value || repeatCount >= maxLoops) {
                _isPlaying.value = false
                handler.post { onComplete() }
                return
            }
            repeatCount++
            scheduleActions(actions, playbackSpeed) { runLoop() }
        }
        handler.post { runLoop() }
    }

    private fun scheduleActions(actions: List<MacroAction>, speed: Float, onDone: () -> Unit) {
        if (actions.isEmpty()) { onDone(); return }
        var delay = 0L
        actions.forEach { action ->
            val d  = (action.delayBefore / speed).toLong()
            val dur = (action.duration / speed).toLong()
            delay += d
            val captured = delay
            handler.postDelayed({ if (_isPlaying.value) dispatchAction(action, dur) }, captured)
            delay += dur
        }
        handler.postDelayed({ if (_isPlaying.value) onDone() }, delay + 50L)
    }

    private fun dispatchAction(action: MacroAction, duration: Long) {
        when (action.type) {
            ActionType.TAP        -> dispatchTap(action.x, action.y, duration)
            ActionType.LONG_PRESS -> dispatchTap(action.x, action.y, duration)
            ActionType.SWIPE      -> dispatchSwipe(action.x, action.y, action.endX, action.endY, duration)
            ActionType.MULTI_TOUCH -> dispatchMultiTouch(action, duration)
        }
    }

    private fun dispatchTap(x: Float, y: Float, duration: Long) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(1L)))
                .build(), null, null
        )
    }

    private fun dispatchSwipe(x: Float, y: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply { moveTo(x, y); lineTo(endX, endY) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(100L)))
                .build(), null, null
        )
    }

    private fun dispatchMultiTouch(action: MacroAction, duration: Long) {
        val builder = GestureDescription.Builder()
        action.touchPoints.take(10).forEach { pt ->
            val path = Path().apply { moveTo(pt.x, pt.y); lineTo(pt.x + 1f, pt.y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(1L)))
        }
        dispatchGesture(builder.build(), null, null)
    }

    fun stopPlayback() {
        _isPlaying.value = false
        handler.removeCallbacksAndMessages(null)
    }
}
