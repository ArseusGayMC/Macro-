package com.ggmacro.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ggmacro.app.MainActivity
import com.ggmacro.app.data.model.MacroAction

class FloatingTriggerButtonService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "gg_macro_trigger"
        const val EXTRA_MACRO_NAME = "macro_name"
        const val EXTRA_LOOP_COUNT = "loop_count"
        const val EXTRA_SPEED = "speed"

        var pendingActions: List<MacroAction> = emptyList()

        fun start(
            context: Context,
            actions: List<MacroAction>,
            macroName: String,
            loopCount: Int,
            speed: Float
        ) {
            pendingActions = actions
            val intent = Intent(context, FloatingTriggerButtonService::class.java).apply {
                putExtra(EXTRA_MACRO_NAME, macroName)
                putExtra(EXTRA_LOOP_COUNT, loopCount)
                putExtra(EXTRA_SPEED, speed)
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

    private var macroName: String = ""
    private var loopCount: Int = 1
    private var speed: Float = 1.0f
    private var actions: List<MacroAction> = emptyList()

    private var isExecuting = false

    private val longPressRunnable = Runnable {
        startExecution()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        macroName = intent?.getStringExtra(EXTRA_MACRO_NAME) ?: ""
        loopCount = intent?.getIntExtra(EXTRA_LOOP_COUNT, 1) ?: 1
        speed = intent?.getFloatExtra(EXTRA_SPEED, 1.0f) ?: 1.0f
        actions = pendingActions.toList()
        if (triggerView == null) showTriggerButton()
        return START_STICKY
    }

    override fun onDestroy() {
        stopExecution()
        removeTriggerView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    private fun showTriggerButton() {
        val density = resources.displayMetrics.density
        val buttonSize = (72 * density).toInt()

        overlayParams = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
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

        val view = object : View(this) {
            private var downRawX = 0f
            private var downRawY = 0f
            private var isDragging = false

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val radius = width / 2f - 5f

                val bgColor = if (isExecuting) "#CC330000" else "#CC001433"
                val strokeColor = if (isExecuting) "#FF4444" else "#00E5FF"
                val textColor = if (isExecuting) "#FF6666" else "#00E5FF"

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(bgColor)
                    style = Paint.Style.FILL
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(strokeColor)
                    strokeWidth = 3f * density
                    style = Paint.Style.STROKE
                }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(textColor)
                    textSize = 10f * density
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(textColor)
                    textSize = 7.5f * density
                    textAlign = Paint.Align.CENTER
                }

                canvas.drawCircle(cx, cy, radius, bgPaint)
                canvas.drawCircle(cx, cy, radius, strokePaint)

                val label = if (isExecuting) "RUNNING" else "HOLD"
                val sub = if (isExecuting) "Release" else "to run"
                canvas.drawText(label, cx, cy - 2f * density, textPaint)
                canvas.drawText(sub, cx, cy + 10f * density, subTextPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        isDragging = false
                        handler.postDelayed(longPressRunnable, 400L)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!isDragging && (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12)) {
                            isDragging = true
                            handler.removeCallbacks(longPressRunnable)
                            if (isExecuting) stopExecution()
                        }
                        if (isDragging) {
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
        if (isExecuting || actions.isEmpty()) return
        val service = MacroAccessibilityService.getInstance() ?: return
        isExecuting = true
        handler.post { triggerView?.invalidate() }
        service.playMacro(actions, -1, speed) {
            isExecuting = false
            handler.post { triggerView?.invalidate() }
        }
    }

    private fun stopExecution() {
        if (!isExecuting) return
        MacroAccessibilityService.getInstance()?.stopPlayback()
        isExecuting = false
        handler.post { triggerView?.invalidate() }
    }

    private fun removeTriggerView() {
        triggerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        triggerView = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GG Macro Trigger Button",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Macro - Trigger Active")
            .setContentText("Hold button to execute: $macroName")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
