package com.ggmacro.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.ggmacro.app.data.model.ActionType
import com.ggmacro.app.data.model.MacroAction
import kotlinx.coroutines.flow.MutableStateFlow

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: MacroAccessibilityService? = null
        fun getInstance(): MacroAccessibilityService? = instance
        val isRunning = MutableStateFlow(false)
    }

    // Separate handlers: playback and tap-loop never interfere
    private val playHandler = Handler(Looper.getMainLooper())
    private val tapHandler  = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)

    @Volatile private var tapping = false

    // ── Tap-loop (FloatingTriggerButtonService) ───────────────────────────

    fun startTapLoop(x: Float, y: Float, durationMs: Long, delayMs: Long) {
        tapping = true
        tap(x, y, durationMs.coerceAtLeast(1L), delayMs.coerceAtLeast(16L))
    }

    fun stopTapLoop() {
        tapping = false
        tapHandler.removeCallbacksAndMessages(null)
    }

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
                    if (tapping) tapHandler.postDelayed({ tap(x, y, dur, delay) }, delay + 40L)
                }
            },
            tapHandler
        )
        if (!ok && tapping) tapHandler.postDelayed({ tap(x, y, dur, delay) }, delay)
    }

    // ── Macro playback (HomeViewModel) ────────────────────────────────────

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
                playHandler.post { onComplete() }
                return
            }
            repeatCount++
            scheduleActions(actions, playbackSpeed) { runLoop() }
        }
        playHandler.post { runLoop() }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playHandler.removeCallbacksAndMessages(null)   // only clears playback, NOT tapHandler
    }

    private fun scheduleActions(actions: List<MacroAction>, speed: Float, onDone: () -> Unit) {
        if (actions.isEmpty()) { onDone(); return }
        var delay = 0L
        actions.forEach { action ->
            val d   = (action.delayBefore / speed).toLong()
            val dur = (action.duration    / speed).toLong()
            delay += d
            val capturedDelay = delay
            playHandler.postDelayed({
                if (_isPlaying.value) dispatchAction(action, dur)
            }, capturedDelay)
            delay += dur
        }
        playHandler.postDelayed({ if (_isPlaying.value) onDone() }, delay + 50L)
    }

    private fun dispatchAction(action: MacroAction, duration: Long) {
        when (action.type) {
            ActionType.TAP         -> dispatchTap(action.x, action.y, duration)
            ActionType.LONG_PRESS  -> dispatchTap(action.x, action.y, duration)
            ActionType.SWIPE       -> dispatchSwipe(action.x, action.y, action.endX, action.endY, duration)
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
        playHandler.removeCallbacksAndMessages(null)
        instance = null
        isRunning.value = false
        _isPlaying.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        tapping = false
        tapHandler.removeCallbacksAndMessages(null)
        playHandler.removeCallbacksAndMessages(null)
        instance = null
        isRunning.value = false
        _isPlaying.value = false
    }
}
