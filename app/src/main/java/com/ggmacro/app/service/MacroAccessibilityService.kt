package com.ggmacro.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ggmacro.app.data.model.ActionType
import com.ggmacro.app.data.model.MacroAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

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

    // ── Trigger overlay ──────────────────────────────────────────────────
    private var triggerView: View?             = null
    private var wm:          WindowManager?    = null
    private lateinit var     overlayLp: WindowManager.LayoutParams

    private var overlayMacroName   = "Macro"
    private var overlayTapDuration = 50L
    private var overlayTapDelay    = 50L
    private var overlayHoldMs      = 350L
    private var overlayStatusBarH  = 0

    @Volatile private var tapLooping = false
    @Volatile private var overlayExecuting = false

    private var touchDownX  = 0f
    private var touchDownY  = 0f
    private var touchMoved  = false

    fun showTriggerOverlay(
        macroName:  String,
        tapDuration: Long,
        tapDelay:    Long,
        holdMs:      Long
    ) {
        hideTriggerOverlay()
        overlayMacroName   = macroName
        overlayTapDuration = tapDuration.coerceAtLeast(1L)
        overlayTapDelay    = tapDelay.coerceAtLeast(16L)
        overlayHoldMs      = holdMs.coerceIn(50L, 2000L)
        overlayStatusBarH  = getStatusBarHeight()
        overlayExecuting   = false
        tapLooping         = false
        handler.post { buildOverlay() }
    }

    fun hideTriggerOverlay() {
        tapLooping       = false
        overlayExecuting = false
        handler.post {
            triggerView?.let {
                try { wm?.removeView(it) } catch (_: Exception) {}
            }
            triggerView = null
        }
    }

    private fun buildOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        this.wm = wm

        val dp     = resources.displayMetrics.density
        val sizePx = (84 * dp).toInt()

        // TYPE_ACCESSIBILITY_OVERLAY: works even in Xiaomi Game Mode
        overlayLp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        val bgPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            strokeWidth = 3.5f * dp
            pathEffect  = DashPathEffect(floatArrayOf(10f * dp, 5f * dp), 0f)
        }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign      = Paint.Align.CENTER
            isFakeBoldText = true
            textSize       = 10f * dp
        }

        val view = object : View(this@MacroAccessibilityService) {
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r  = width / 2f - 6f * dp

                bgPaint.color = if (overlayExecuting) Color.argb(80, 200, 30, 30)
                                else Color.argb(35, 0, 170, 255)
                borderPaint.color = if (overlayExecuting) Color.parseColor("#FF3333")
                                    else Color.parseColor("#00BFFF")
                txtPaint.color = borderPaint.color

                canvas.drawCircle(cx, cy, r, bgPaint)
                canvas.drawCircle(cx, cy, r, borderPaint)

                val label = overlayMacroName.take(8)
                if (label.length <= 5) {
                    canvas.drawText(label, cx, cy + txtPaint.textSize / 3f, txtPaint)
                } else {
                    val h = label.length / 2
                    canvas.drawText(label.substring(0, h), cx, cy - txtPaint.textSize * 0.55f, txtPaint)
                    canvas.drawText(label.substring(h),    cx, cy + txtPaint.textSize * 0.95f, txtPaint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleOverlayTouch(event)
                return true
            }
        }

        view.isClickable = true
        view.isFocusable = false
        triggerView = view
        wm.addView(view, overlayLp)
    }

    private val longPressRunnable = Runnable {
        if (overlayExecuting || tapLooping) return@Runnable
        overlayExecuting = true
        tapLooping       = true
        vibrate()
        triggerView?.post { triggerView?.invalidate() }

        // With FLAG_LAYOUT_IN_SCREEN: overlayLp.x/y are absolute screen coords
        val dp     = resources.displayMetrics.density
        val halfPx = 42f * dp
        val tapX   = overlayLp.x.toFloat() + halfPx
        val tapY   = overlayLp.y.toFloat() + halfPx

        doTap(tapX, tapY)
    }

    private fun doTap(x: Float, y: Float) {
        if (!tapLooping) return
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, overlayTapDuration)
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (tapLooping) handler.postDelayed({ doTap(x, y) }, overlayTapDelay)
                }
                override fun onCancelled(g: GestureDescription?) {
                    if (tapLooping) handler.postDelayed({ doTap(x, y) }, overlayTapDelay)
                }
            }, handler
        )
        if (!ok && tapLooping) handler.postDelayed({ doTap(x, y) }, overlayTapDelay)
    }

    private fun stopTapLoop() {
        tapLooping       = false
        overlayExecuting = false
        triggerView?.post { triggerView?.invalidate() }
    }

    private fun handleOverlayTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                touchMoved = false
                handler.postDelayed(longPressRunnable, overlayHoldMs)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchDownX
                val dy = event.rawY - touchDownY
                if (!touchMoved && (abs(dx) > 8f || abs(dy) > 8f)) {
                    touchMoved = true
                    handler.removeCallbacks(longPressRunnable)
                    stopTapLoop()
                }
                if (touchMoved) {
                    overlayLp.x = (overlayLp.x + dx.toInt()).coerceAtLeast(0)
                    overlayLp.y = (overlayLp.y + dy.toInt()).coerceAtLeast(0)
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    try { wm?.updateViewLayout(triggerView, overlayLp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                stopTapLoop()
            }
        }
    }

    // ── Public tap loop API (kept for compatibility) ─────────────────────
    fun startTapLoop(x: Float, y: Float, duration: Long, delay: Long) {
        if (tapLooping) return
        tapLooping = true
        overlayExecuting = true
        doTap(x, y)
    }

    fun stopTapLoopPublic() {
        stopTapLoop()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning.value = true
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() { Log.d(TAG, "Service interrupted") }

    override fun onUnbind(intent: Intent?): Boolean {
        hideTriggerOverlay()
        instance = null
        isRunning.value = false
        _isPlaying.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideTriggerOverlay()
        instance = null
        isRunning.value = false
        stopPlayback()
    }

    // ── Macro playback ────────────────────────────────────────────────────
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

    private fun scheduleActions(
        actions: List<MacroAction>,
        speed: Float,
        onDone: () -> Unit
    ) {
        if (actions.isEmpty()) { onDone(); return }
        var cumulativeDelay = 0L
        actions.forEach { action ->
            val scaledDelay    = (action.delayBefore / speed).toLong()
            val scaledDuration = (action.duration / speed).toLong()
            cumulativeDelay += scaledDelay
            val cd = cumulativeDelay
            handler.postDelayed({
                if (_isPlaying.value) dispatchMacroAction(action, scaledDuration)
            }, cd)
            cumulativeDelay += scaledDuration
        }
        handler.postDelayed({ if (_isPlaying.value) onDone() }, cumulativeDelay + 50L)
    }

    private fun dispatchMacroAction(action: MacroAction, duration: Long) {
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
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun vibrate() {
        try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 80
    }
}
