package com.ggmacro.app.data.model

data class MacroAction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ActionType,
    val x: Float = 0f,
    val y: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f,
    val duration: Long = 50L,
    val delayBefore: Long = 0L,
    val touchPoints: List<TouchPoint> = emptyList()
)

data class TouchPoint(
    val x: Float,
    val y: Float,
    val pointerId: Int = 0
)

enum class ActionType {
    TAP,
    LONG_PRESS,
    SWIPE,
    MULTI_TOUCH
}
