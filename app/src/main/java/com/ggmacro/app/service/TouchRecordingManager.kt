package com.ggmacro.app.service

import android.view.MotionEvent
import com.ggmacro.app.data.model.ActionType
import com.ggmacro.app.data.model.MacroAction
import com.ggmacro.app.data.model.TouchPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TouchRecordingManager @Inject constructor() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordedActions = MutableStateFlow<List<MacroAction>>(emptyList())
    val recordedActions: StateFlow<List<MacroAction>> = _recordedActions

    private val pendingActions = mutableListOf<MacroAction>()
    private var lastEventTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    fun startRecording() {
        pendingActions.clear()
        _recordedActions.value = emptyList()
        lastEventTime = System.currentTimeMillis()
        _isRecording.value = true
    }

    fun stopRecording(): List<MacroAction> {
        _isRecording.value = false
        val result = pendingActions.toList()
        _recordedActions.value = result
        return result
    }

    fun onTouchEvent(event: MotionEvent) {
        if (!_isRecording.value) return

        val now = System.currentTimeMillis()
        val delayBefore = if (pendingActions.isEmpty()) 0L else now - lastEventTime
        lastEventTime = now

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = now
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val multiPoints = (0 until event.pointerCount).map { i ->
                    TouchPoint(event.getX(i), event.getY(i), event.getPointerId(i))
                }
                pendingActions.add(
                    MacroAction(
                        type = ActionType.MULTI_TOUCH,
                        x = event.x,
                        y = event.y,
                        duration = 80L,
                        delayBefore = delayBefore,
                        touchPoints = multiPoints
                    )
                )
            }

            MotionEvent.ACTION_UP -> {
                val holdDuration = now - downTime
                val deltaX = event.x - downX
                val deltaY = event.y - downY
                val distance = Math.hypot(deltaX.toDouble(), deltaY.toDouble())

                val action = when {
                    holdDuration >= 500L && distance < 20f -> MacroAction(
                        type = ActionType.LONG_PRESS,
                        x = downX,
                        y = downY,
                        duration = holdDuration,
                        delayBefore = delayBefore
                    )
                    distance >= 20f -> MacroAction(
                        type = ActionType.SWIPE,
                        x = downX,
                        y = downY,
                        endX = event.x,
                        endY = event.y,
                        duration = holdDuration.coerceAtLeast(100L),
                        delayBefore = delayBefore
                    )
                    else -> MacroAction(
                        type = ActionType.TAP,
                        x = downX,
                        y = downY,
                        duration = holdDuration.coerceAtLeast(50L),
                        delayBefore = delayBefore
                    )
                }
                pendingActions.add(action)
            }
        }
    }
}
