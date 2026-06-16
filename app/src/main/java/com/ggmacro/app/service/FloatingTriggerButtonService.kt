package com.ggmacro.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ggmacro.app.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingTriggerButtonService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "gg_macro_trigger"
        const val EXTRA_MACRO_NAME = "macro_name"
        const val EXTRA_TAP_DURATION = "tap_duration"
        const val EXTRA_TAP_DELAY = "tap_delay"
        const val EXTRA_HOLD_THRESHOLD = "hold_threshold"

        @Volatile var isRunning = false

        fun start(
            context: Context,
            macroName: String,
            tapDuration: Long,
            tapDelay: Long,
            holdThreshold: Long = 350L
        ) {
            val i = Intent(context, FloatingTriggerButtonService::class.java).apply {
                putExtra(EXTRA_MACRO_NAME, macroName)
                putExtra(EXTRA_TAP_DURATION, tapDuration)
                putExtra(EXTRA_TAP_DELAY, tapDelay)
                putExtra(EXTRA_HOLD_THRESHOLD, holdThreshold)
            }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTriggerButtonService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    // LayoutParams – gravity START|TOP, x/y = offset from top-left of visible screen (below status bar)
    private lateinit var lp: WindowManager.LayoutParams

    private var macroName: String = "Macro"
    private var tapDuration: Long = 50L
    private var tapDelay: Long = 50L
    private var holdThreshold: Long = 350L

    // Height of the status bar in pixels – needed to convert lp.y to absolute screen coords
    private var statusBarHeight: Int = 0

    @Volatile private var isExecuting = false

    private val longPressRunnable = Runnable { startExecution() }

    /* ------------------------------------------------------------------ */
    /*  Lifecycle                                                            */
    /* ------------------------------------------------------------------ */

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        statusBarHeight = getStatusBarHeight()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        macroName = intent?.getStringExtra(EXTRA_MACRO_NAME)?.ifBlank { "Macro" } ?: "Macro"
        tapDuration = (intent?.getLongExtra(EXTRA_TAP_DURATION, 50L) ?: 50L).coerceAtLeast(1L)
        tapDelay = (intent?.getLongExtra(EXTRA_TAP_DELAY, 50L) ?: 50L).coerceAtLeast(30L)
        holdThreshold = (intent?.getLongExtra(EXTRA_HOLD_THRESHOLD, 350L) ?: 350L).coerceIn(50L, 2000L)
        if (overlayView == null) addOverlayView()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isExecuting = false
        handler.removeCallbacksAndMessages(null)
        removeOverlayView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /* ------------------------------------------------------------------ */
    /*  Overlay view                                                         */
    /* ------------------------------------------------------------------ */

    private fun addOverlayView() {
        val dp = resources.displayMetrics.density
        val sizePx = (80 * dp).toInt()   // slightly bigger hit-target

        // Do NOT use FLAG_LAYOUT_IN_SCREEN → lp.y is relative to below the status bar.
        // We add statusBarHeight when building gesture coordinates.
        lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * dp
            pathEffect = DashPathEffect(floatArrayOf(9f * dp, 5f * dp), 0f)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // Float accumulator for smooth dragging on high-dpi screens
        var accX = 0f
        var accY = 0f
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false

        val view = object : View(this@FloatingTriggerButtonService) {

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val radius = width / 2f - 6f * dp

                val serviceUp = MacroAccessibilityService.getInstance() == null

                bgPaint.color = when {
                    serviceUp     -> Color.argb(80, 150, 0, 0)
                    isExecuting   -> Color.argb(80, 200, 30, 30)
                    else          -> Color.argb(30, 0, 180, 255)
                }
                borderPaint.color = when {
                    serviceUp     -> Color.parseColor("#AA2222")
                    isExecuting   -> Color.parseColor("#FF4444")
                    else          -> Color.parseColor("#00BFFF")
                }
                textPaint.color = when {
                    serviceUp     -> Color.parseColor("#FF3333")
                    isExecuting   -> Color.parseColor("#FF6666")
                    else          -> Color.parseColor("#00CFFF")
                }

                canvas.drawCircle(cx, cy, radius, bgPaint)
                canvas.drawCircle(cx, cy, radius, borderPaint)

                val label = if (serviceUp) "NO SVC" else macroName.take(9)
                textPaint.textSize = 9.5f * dp
                if (label.length <= 5) {
                    canvas.drawText(label, cx, cy + textPaint.textSize / 3f, textPaint)
                } else {
                    val half = label.length / 2
                    canvas.drawText(label.substring(0, half), cx, cy - textPaint.textSize * 0.55f, textPaint)
                    canvas.drawText(label.substring(half), cx, cy + textPaint.textSize * 0.95f, textPaint)
                }
            }
        }

        // Use setOnTouchListener – more reliable on Xiaomi overlay windows
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    accX = 0f
                    accY = 0f
                    dragging = false
                    handler.postDelayed(longPressRunnable, holdThreshold)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    downRawX = event.rawX
                    downRawY = event.rawY

                    if (!dragging && (abs(dx) > 8 || abs(dy) > 8)) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                        if (isExecuting) stopExecution()
                    }

                    if (dragging) {
                        accX += dx
                        accY += dy
                        val moveX = accX.roundToInt()
                        val moveY = accY.roundToInt()
                        if (moveX != 0 || moveY != 0) {
                            lp.x = (lp.x + moveX).coerceAtLeast(0)
                            lp.y = (lp.y + moveY).coerceAtLeast(0)
                            accX -= moveX
                            accY -= moveY
                            try { windowManager.updateViewLayout(v, lp) } catch (_: Exception) {}
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (isExecuting) stopExecution()
                    true
                }
                else -> false
            }
        }

        overlayView = view
        windowManager.addView(view, lp)
    }

    /* ------------------------------------------------------------------ */
    /*  Execution loop                                                       */
    /* ------------------------------------------------------------------ */

    private fun startExecution() {
        if (isExecuting) return
        if (MacroAccessibilityService.getInstance() == null) {
            overlayView?.post { overlayView?.invalidate() }
            return
        }
        isExecuting = true
        overlayView?.post { overlayView?.invalidate() }
        dispatchNextTap()
    }

    private fun dispatchNextTap() {
        if (!isExecuting) return

        val service = MacroAccessibilityService.getInstance() ?: run {
            isExecuting = false
            overlayView?.post { overlayView?.invalidate() }
            return
        }

        // lp.x/y are relative to below-status-bar area (no FLAG_LAYOUT_IN_SCREEN).
        // dispatchGesture uses ABSOLUTE screen coords → add statusBarHeight to Y.
        val dp = resources.displayMetrics.density
        val halfPx = (40 * dp)   // half of 80dp button
        val tapX = (lp.x + halfPx)
        val tapY = (statusBarHeight + lp.y + halfPx)

        val path = Path().apply { moveTo(tapX, tapY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, tapDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                handler.postDelayed({ dispatchNextTap() }, tapDelay)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                handler.postDelayed({ dispatchNextTap() }, tapDelay)
            }
        }, handler)
    }

    private fun stopExecution() {
        isExecuting = false
        handler.removeCallbacksAndMessages(null)
        overlayView?.post { overlayView?.invalidate() }
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                              */
    /* ------------------------------------------------------------------ */

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 72
    }

    private fun removeOverlayView() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "GG Macro Trigger Button",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Macro — Tetik Buton Aktif")
            .setContentText("$macroName · Basılı tut → otomatik tıklama")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
