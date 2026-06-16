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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ggmacro.app.MainActivity
import kotlin.math.abs

class FloatingTriggerButtonService : Service() {

    companion object {
        const val NOTIFICATION_ID      = 1002
        const val CHANNEL_ID           = "gg_macro_trigger"
        const val EXTRA_MACRO_NAME     = "macro_name"
        const val EXTRA_TAP_DURATION   = "tap_duration"
        const val EXTRA_TAP_DELAY      = "tap_delay"
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
                putExtra(EXTRA_MACRO_NAME,     macroName)
                putExtra(EXTRA_TAP_DURATION,   tapDuration)
                putExtra(EXTRA_TAP_DELAY,      tapDelay)
                putExtra(EXTRA_HOLD_THRESHOLD, holdThreshold)
            }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTriggerButtonService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var overlayView: View? = null
    private lateinit var lp: WindowManager.LayoutParams

    private var macroName     = "Macro"
    private var tapDuration   = 50L
    private var tapDelay      = 50L
    private var holdThreshold = 350L

    @Volatile private var executing = false

    // Touch state
    private var downX  = 0f
    private var downY  = 0f
    private var moved  = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        macroName     = intent?.getStringExtra(EXTRA_MACRO_NAME)?.ifBlank { "Macro" } ?: "Macro"
        tapDuration   = (intent?.getLongExtra(EXTRA_TAP_DURATION,   50L) ?: 50L).coerceAtLeast(1L)
        tapDelay      = (intent?.getLongExtra(EXTRA_TAP_DELAY,      50L) ?: 50L).coerceAtLeast(16L)
        holdThreshold = (intent?.getLongExtra(EXTRA_HOLD_THRESHOLD, 350L) ?: 350L).coerceIn(50L, 2000L)
        if (overlayView == null) buildOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopLoop()
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Overlay ────────────────────────────────────────────────────────────

    private fun buildOverlay() {
        val dp     = resources.displayMetrics.density
        val sizePx = (84 * dp).toInt()

        // FLAG_LAYOUT_IN_SCREEN: x/y are ABSOLUTE screen coords (same as dispatchGesture)
        lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 300
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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

        val view = object : View(this@FloatingTriggerButtonService) {
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r  = width / 2f - 6f * dp
                val noSvc = MacroAccessibilityService.getInstance() == null

                bgPaint.color = when {
                    noSvc     -> Color.argb(100, 120, 0, 0)
                    executing -> Color.argb(90,  200, 30, 30)
                    else      -> Color.argb(40,  0,  170, 255)
                }
                borderPaint.color = when {
                    noSvc     -> Color.parseColor("#BB1111")
                    executing -> Color.parseColor("#FF2222")
                    else      -> Color.parseColor("#00BFFF")
                }
                txtPaint.color = borderPaint.color

                canvas.drawCircle(cx, cy, r, bgPaint)
                canvas.drawCircle(cx, cy, r, borderPaint)

                val label = when {
                    noSvc     -> "SERVIS\nKAPALI"
                    executing -> "●\nTIKLIYOR"
                    else      -> macroName.take(10)
                }
                val lines = label.split("\n")
                if (lines.size == 1) {
                    val s = lines[0]
                    if (s.length <= 6) {
                        canvas.drawText(s, cx, cy + txtPaint.textSize / 3f, txtPaint)
                    } else {
                        val h = s.length / 2
                        canvas.drawText(s.substring(0, h), cx, cy - txtPaint.textSize * 0.55f, txtPaint)
                        canvas.drawText(s.substring(h),    cx, cy + txtPaint.textSize * 0.95f, txtPaint)
                    }
                } else {
                    canvas.drawText(lines[0], cx, cy - txtPaint.textSize * 0.55f, txtPaint)
                    canvas.drawText(lines[1], cx, cy + txtPaint.textSize * 0.95f, txtPaint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouch(event)
                return true
            }
        }

        view.isClickable = true
        view.isFocusable = false
        overlayView = view
        try { wm.addView(view, lp) } catch (e: Exception) { /* permission not granted yet */ }
    }

    private val longPressAction = Runnable {
        val svc = MacroAccessibilityService.getInstance()
        if (svc == null || executing) return@Runnable
        executing = true
        vibrate()
        overlayView?.post { overlayView?.invalidate() }

        // FLAG_LAYOUT_IN_SCREEN → lp.x/y are absolute screen coords = same as dispatchGesture
        val dp     = resources.displayMetrics.density
        val halfPx = 42f * dp
        val tapX   = lp.x.toFloat() + halfPx
        val tapY   = lp.y.toFloat() + halfPx

        svc.startTapLoop(tapX, tapY, tapDuration, tapDelay)
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                handler.postDelayed(longPressAction, holdThreshold)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!moved && (abs(dx) > 10 || abs(dy) > 10)) {
                    moved = true
                    handler.removeCallbacks(longPressAction)
                    stopLoop()
                }
                if (moved) {
                    lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                    lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                    downX = event.rawX
                    downY = event.rawY
                    try { wm.updateViewLayout(overlayView, lp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressAction)
                stopLoop()
            }
        }
    }

    private fun stopLoop() {
        if (!executing) return
        executing = false
        MacroAccessibilityService.getInstance()?.stopTapLoop()
        overlayView?.post { overlayView?.invalidate() }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun vibrate() {
        try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        overlayView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "GG Macro Trigger", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Macro — Tetik Aktif")
            .setContentText("$macroName · Basılı tut → otomatik tıklama")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true).setSilent(true)
            .build()
    }
}
