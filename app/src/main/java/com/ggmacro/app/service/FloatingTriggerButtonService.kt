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

    /* ── fields ─────────────────────────────────────────────────────────── */

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var overlayView: View? = null
    private lateinit var lp: WindowManager.LayoutParams

    private var macroName     = "Macro"
    private var tapDuration   = 50L
    private var tapDelay      = 50L
    private var holdThreshold = 350L
    private var statusBarH    = 0

    @Volatile private var executing = false

    // touch-tracking fields (shared by onTouchEvent and setOnTouchListener)
    private var touchDownX  = 0f
    private var touchDownY  = 0f
    private var touchMoved  = false
    private var lastEvtTime = -1L   // deduplicate dual-callback devices

    /* ── lifecycle ──────────────────────────────────────────────────────── */

    override fun onCreate() {
        super.onCreate()
        isRunning  = true
        wm         = getSystemService(WINDOW_SERVICE) as WindowManager
        statusBarH = getStatusBarHeight()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        macroName     = intent?.getStringExtra(EXTRA_MACRO_NAME)?.ifBlank { "Macro" } ?: "Macro"
        tapDuration   = (intent?.getLongExtra(EXTRA_TAP_DURATION,   50L) ?: 50L).coerceAtLeast(1L)
        tapDelay      = (intent?.getLongExtra(EXTRA_TAP_DELAY,      50L) ?: 50L).coerceAtLeast(16L)
        holdThreshold = (intent?.getLongExtra(EXTRA_HOLD_THRESHOLD, 350L) ?: 350L).coerceIn(50L, 2000L)
        if (overlayView == null) addOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopLoop()
        uiHandler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /* ── overlay ────────────────────────────────────────────────────────── */

    private fun addOverlay() {
        val dp     = resources.displayMetrics.density
        val sizePx = (82 * dp).toInt()

        lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,   // no FLAG_LAYOUT_IN_SCREEN
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                val cx    = width  / 2f
                val cy    = height / 2f
                val r     = width  / 2f - 6f * dp
                val noSvc = MacroAccessibilityService.getInstance() == null

                bgPaint.color = when {
                    noSvc     -> Color.argb(90,  140, 0,  0)
                    executing -> Color.argb(80,  200, 30, 30)
                    else      -> Color.argb(35,  0,  170, 255)
                }
                strokePaint.color = when {
                    noSvc     -> Color.parseColor("#CC1111")
                    executing -> Color.parseColor("#FF3333")
                    else      -> Color.parseColor("#00BFFF")
                }
                txtPaint.color = strokePaint.color

                canvas.drawCircle(cx, cy, r, bgPaint)
                canvas.drawCircle(cx, cy, r, strokePaint)

                val label = if (noSvc) "NO SVC" else macroName.take(8)
                if (label.length <= 5) {
                    canvas.drawText(label, cx, cy + txtPaint.textSize / 3f, txtPaint)
                } else {
                    val half = label.length / 2
                    canvas.drawText(label.substring(0, half), cx, cy - txtPaint.textSize * 0.55f, txtPaint)
                    canvas.drawText(label.substring(half),    cx, cy + txtPaint.textSize * 0.95f, txtPaint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                processTouch(event)
                return true
            }
        }

        view.isClickable = true
        view.isFocusable = false

        // Belt + suspenders for Xiaomi overlay windows
        view.setOnTouchListener { _, event ->
            // Skip if already handled by onTouchEvent (same event-time)
            if (event.eventTime != lastEvtTime) processTouch(event)
            true
        }

        overlayView = view
        wm.addView(view, lp)
    }

    private fun processTouch(event: MotionEvent) {
        lastEvtTime = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                touchMoved = false
                uiHandler.postDelayed(longPressAction, holdThreshold)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchDownX
                val dy = event.rawY - touchDownY
                if (!touchMoved && (abs(dx) > 8 || abs(dy) > 8)) {
                    touchMoved = true
                    uiHandler.removeCallbacks(longPressAction)
                    stopLoop()
                }
                if (touchMoved) {
                    lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                    lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    try { wm.updateViewLayout(overlayView, lp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(longPressAction)
                stopLoop()
            }
        }
    }

    /* ── long-press callback ─────────────────────────────────────────────  */

    private val longPressAction = Runnable {
        val svc = MacroAccessibilityService.getInstance()
        if (svc == null || executing) {
            overlayView?.invalidate()
            return@Runnable
        }
        executing = true
        vibrate()
        overlayView?.post { overlayView?.invalidate() }

        val dp     = resources.displayMetrics.density
        val halfPx = 41f * dp
        // lp.y is below-status-bar offset; add statusBarH for absolute screen coords
        val tapX = lp.x.toFloat() + halfPx
        val tapY = statusBarH.toFloat() + lp.y.toFloat() + halfPx

        svc.startTapLoop(tapX, tapY, tapDuration, tapDelay)
    }

    private fun stopLoop() {
        if (!executing) return
        executing = false
        MacroAccessibilityService.getInstance()?.stopTapLoop()
        overlayView?.post { overlayView?.invalidate() }
    }

    /* ── helpers ─────────────────────────────────────────────────────────  */

    private fun vibrate() {
        try {
            val vib = getSystemService(Vibrator::class.java)
            vib?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 80
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
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
