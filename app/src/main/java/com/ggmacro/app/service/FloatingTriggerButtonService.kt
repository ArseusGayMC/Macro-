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

class FloatingTriggerButtonService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "gg_macro_trigger"
        const val EXTRA_MACRO_NAME = "macro_name"
        const val EXTRA_TAP_DURATION = "tap_duration"
        const val EXTRA_TAP_DELAY = "tap_delay"

        @Volatile
        var isRunning = false

        fun start(context: Context, macroName: String, tapDuration: Long, tapDelay: Long) {
            val intent = Intent(context, FloatingTriggerButtonService::class.java).apply {
                putExtra(EXTRA_MACRO_NAME, macroName)
                putExtra(EXTRA_TAP_DURATION, tapDuration)
                putExtra(EXTRA_TAP_DELAY, tapDelay)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTriggerButtonService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var triggerView: View? = null
    private lateinit var overlayParams: WindowManager.LayoutParams

    private var macroName: String = "Macro"
    private var tapDuration: Long = 50L
    private var tapDelay: Long = 50L

    @Volatile
    private var isExecuting = false

    private val longPressRunnable = Runnable { startExecution() }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        macroName = intent?.getStringExtra(EXTRA_MACRO_NAME)?.ifBlank { "Macro" } ?: "Macro"
        tapDuration = intent?.getLongExtra(EXTRA_TAP_DURATION, 50L) ?: 50L
        tapDelay = (intent?.getLongExtra(EXTRA_TAP_DELAY, 50L) ?: 50L).coerceAtLeast(30L)
        if (triggerView == null) showTriggerButton()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isExecuting = false
        handler.removeCallbacksAndMessages(null)
        removeTriggerView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showTriggerButton() {
        val density = resources.displayMetrics.density
        val buttonPx = (72 * density).toInt()

        overlayParams = WindowManager.LayoutParams(
            buttonPx, buttonPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 400
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val view = object : View(this) {
            private var downRawX = 0f
            private var downRawY = 0f
            private var hasMoved = false

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val radius = width / 2f - 5f * density

                bgPaint.color = if (isExecuting) Color.argb(70, 200, 30, 30)
                else Color.argb(25, 0, 180, 255)
                borderPaint.color = if (isExecuting) Color.parseColor("#FF4444")
                else Color.parseColor("#00BFFF")
                textPaint.color = if (isExecuting) Color.parseColor("#FF6666")
                else Color.parseColor("#00CFFF")

                canvas.drawCircle(cx, cy, radius, bgPaint)
                canvas.drawCircle(cx, cy, radius, borderPaint)

                val label = macroName.take(9)
                val lineCount = if (label.length <= 5) 1 else 2
                if (lineCount == 1) {
                    textPaint.textSize = 11f * density
                    canvas.drawText(label, cx, cy + textPaint.textSize / 3f, textPaint)
                } else {
                    val half = label.length / 2
                    val line1 = label.substring(0, half)
                    val line2 = label.substring(half)
                    textPaint.textSize = 9.5f * density
                    canvas.drawText(line1, cx, cy - textPaint.textSize * 0.6f, textPaint)
                    canvas.drawText(line2, cx, cy + textPaint.textSize * 0.8f, textPaint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        hasMoved = false
                        handler.postDelayed(longPressRunnable, 350L)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!hasMoved && (abs(dx) > 10 || abs(dy) > 10)) {
                            hasMoved = true
                            handler.removeCallbacks(longPressRunnable)
                            if (isExecuting) stopExecution()
                        }
                        if (hasMoved) {
                            overlayParams.x += dx.toInt()
                            overlayParams.y += dy.toInt()
                            try { windowManager.updateViewLayout(this, overlayParams) } catch (_: Exception) {}
                            downRawX = event.rawX
                            downRawY = event.rawY
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (isExecuting) stopExecution()
                    }
                }
                return true
            }
        }

        triggerView = view
        windowManager.addView(view, overlayParams)
    }

    private fun startExecution() {
        if (isExecuting) return
        MacroAccessibilityService.getInstance() ?: return
        isExecuting = true
        triggerView?.post { triggerView?.invalidate() }
        scheduleTap()
    }

    private fun scheduleTap() {
        if (!isExecuting) return
        val service = MacroAccessibilityService.getInstance() ?: run {
            isExecuting = false
            triggerView?.post { triggerView?.invalidate() }
            return
        }

        val density = resources.displayMetrics.density
        val halfPx = (36 * density)
        val tapX = overlayParams.x + halfPx
        val tapY = overlayParams.y + halfPx

        val path = Path().apply { moveTo(tapX, tapY) }
        val stroke = GestureDescription.StrokeDescription(
            path, 0L, tapDuration.coerceAtLeast(1L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                handler.postDelayed({ scheduleTap() }, tapDelay)
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                handler.postDelayed({ scheduleTap() }, tapDelay)
            }
        }, handler)
    }

    private fun stopExecution() {
        isExecuting = false
        handler.removeCallbacksAndMessages(null)
        triggerView?.post { triggerView?.invalidate() }
    }

    private fun removeTriggerView() {
        triggerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        triggerView = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "GG Macro Trigger Button",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Macro — Tetik Aktif")
            .setContentText("$macroName: Basılı tut → otomatik tıklama")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
