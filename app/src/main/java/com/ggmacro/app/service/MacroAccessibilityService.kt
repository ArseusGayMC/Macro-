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
import kotlinx.coroutines.flow.StateFlow

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: MacroAccessibilityService? = null
        fun getInstance(): MacroAccessibilityService? = instance
        val isRunning = MutableStateFlow(false)
    }

    // TWO separate handlers so stopPlayback() cannot kill the tap loop
    private val playHandler = Handler(Looper.getMainLooper())   // macro playback only
    private val tapHandler  = Handler(Looper.getMainLooper())   // trigger-button tap loop only

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    @Volatile private var tapLooping = false

    // ── Tap loop (called from FloatingTriggerButtonService) ───────────────

    fun startTapLoop(x: Float, y: Float, duration: Long, delay: Long) {
        tapLooping = true
        doTap(x, y, duration.coerceAtLeast(1L), delay.coerceAtLeast(16L))
    }

    fun stopTapLoop() {
        tapLooping = false
        tapHandler.removeCallbacksAndMessages(null)
    }

    private fun doTap(x: Float, y: Float, duration: Long, delay: Long) {
        if (!tapLooping) return
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        val ok = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
                .build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (tapLooping) tapHandler.postDelayed({ doTap(x, y, duration, delay) }, delay)
                }
                override fun onCancelled(g: GestureDescription?) {
                    // another gesture was running — wait a bit longer then retry
                    if (tapLooping) tapHandler.postDelayed({ doTap(x, y, duration, delay) }, delay + 40L)
                }
            }, tapHandler   // callbacks delivered on tapHandler's thread
        )
        if (!ok && tapLooping) {
            // dispatchGesture returned false (service busy) — retry after delay
            tapHandler.postDelayed({ doTap(x, y, duration, delay) }, delay)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        tapLooping = false
        instance = this
        isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        tapLooping = false
        instance = null
        isRunning.value = false
        _isPlaying.value = false
        tapHandler.removeCallbacksAndMessages(null)
        playHandler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        tapLooping = false
        instance = null
        isRunning.value = false
        _isPlaying.value = false
        // Clean up BOTH handlers so no callbacks fire after destroy
        tapHandler.removeCallbacksAndMessages(null)
        playHandler.removeCallbacksAndMessages(null)
    }

    // ── Macro playback ─────────────────────────────────────────────────────

    fun playMacro(
        actions: List<MacroAction>,
        loopCount: Int,
        playbackSpeed: Float,
        onComplete: () -> Unit
    ) {
        stopPlayback()      // stops playback, does NOT touch tapHandler
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

    fun stopPlayback() {
        _isPlaying.value = false
        playHandler.removeCallbacksAndMessages(null)    // only clears playback, NOT tap loop
    }
}
