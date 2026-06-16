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

        private var instance: MacroAccessibilityService? = null

        fun getInstance(): MacroAccessibilityService? = instance

        val isRunning = MutableStateFlow(false)

        const val ACTION_STOP_PLAYBACK = "com.ggmacro.app.STOP_PLAYBACK"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var playbackRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning.value = true
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        isRunning.value = false
        _isPlaying.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning.value = false
        stopPlayback()
    }

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
            scheduleActions(actions, playbackSpeed) {
                runLoop()
            }
        }

        handler.post { runLoop() }
    }

    private fun scheduleActions(
        actions: List<MacroAction>,
        speed: Float,
        onDone: () -> Unit
    ) {
        if (actions.isEmpty()) {
            onDone()
            return
        }

        var cumulativeDelay = 0L

        actions.forEachIndexed { index, action ->
            val scaledDelay = (action.delayBefore / speed).toLong()
            val scaledDuration = (action.duration / speed).toLong()
            cumulativeDelay += scaledDelay

            val capturedDelay = cumulativeDelay

            handler.postDelayed({
                if (_isPlaying.value) {
                    dispatchMacroAction(action, scaledDuration)
                }
            }, capturedDelay)

            cumulativeDelay += scaledDuration
        }

        val totalDuration = cumulativeDelay
        handler.postDelayed({
            if (_isPlaying.value) onDone()
        }, totalDuration + 50L)
    }

    private fun dispatchMacroAction(action: MacroAction, duration: Long) {
        when (action.type) {
            ActionType.TAP -> dispatchTap(action.x, action.y, duration)
            ActionType.LONG_PRESS -> dispatchTap(action.x, action.y, duration)
            ActionType.SWIPE -> dispatchSwipe(action.x, action.y, action.endX, action.endY, duration)
            ActionType.MULTI_TOUCH -> dispatchMultiTouch(action, duration)
        }
    }

    private fun dispatchTap(x: Float, y: Float, duration: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(x: Float, y: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(100L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchMultiTouch(action: MacroAction, duration: Long) {
        val builder = GestureDescription.Builder()
        action.touchPoints.take(10).forEach { point ->
            val path = Path().apply { moveTo(point.x, point.y) }
            builder.addStroke(
                GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(1L))
            )
        }
        dispatchGesture(builder.build(), null, null)
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
    }
}
