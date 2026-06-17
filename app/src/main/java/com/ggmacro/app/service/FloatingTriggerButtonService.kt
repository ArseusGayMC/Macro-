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
import android.widget.Toast
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

        fun start(context: Context, macroName: String, tapDuration: Long,
                  tapDelay: Long, holdThreshold: Long = 350L) {
            context.startForegroundService(
                Intent(context, FloatingTriggerButtonService::class.java).apply {
                    putExtra(EXTRA_MACRO_NAME,     macroName)
                    putExtra(EXTRA_TAP_DURATION,   tapDuration)
                    putExtra(EXTRA_TAP_DELAY,      tapDelay)
                    putExtra(EXTRA_HOLD_THRESHOLD, holdThreshold)
                }
            )
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, FloatingTriggerButtonService::class.java))
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
    private var downX = 0f; private var downY = 0f; private var moved = false

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

    // ── Overlay ───────────────────────────────────────────────────────────

    private fun buildOverlay() {
        val dp     = resources.displayMetrics.density
        val sizePx = (90 * dp).toInt()   // slightly larger = easier to hold

        lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 300 }

        val bgPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f * dp
            pathEffect = DashPathEffect(floatArrayOf(10f * dp, 5f * dp), 0f)
        }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = 11f * dp
        }

        val view = object : View(this@FloatingTriggerButtonService) {
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                val r  = width / 2f - 6f * dp
                val svc = MacroAccessibilityService.getInstance()

                bgPaint.color = when {
                    svc == null -> Color.argb(180, 150, 0, 0)
                    executing   -> Color.argb(200, 220, 30, 30)
                    else        -> Color.argb(60,  0, 170, 255)
                }
                borderPaint.color = when {
                    svc == null -> Color.parseColor("#FF2222")
                    executing   -> Color.parseColor("#FF0000")
                    else        -> Color.parseColor("#00BFFF")
                }
                txtPaint.color = Color.WHITE

                canvas.drawCircle(cx, cy, r, bgPaint)
                canvas.drawCircle(cx, cy, r, borderPaint)

                val lines: List<String> = when {
                    svc == null -> listOf("SERVİS", "KAPALI")
                    executing   -> listOf("● TIKLIYOR")
                    else        -> {
                        val n = macroName.take(12)
                        if (n.length <= 7) listOf(n) else listOf(n.take(n.length/2), n.drop(n.length/2))
                    }
                }
                txtPaint.textSize = if (lines.size == 1) 11f * dp else 9.5f * dp
                if (lines.size == 1) {
                    canvas.drawText(lines[0], cx, cy + txtPaint.textSize / 3f, txtPaint)
                } else {
                    canvas.drawText(lines[0], cx, cy - txtPaint.textSize * 0.6f, txtPaint)
                    canvas.drawText(lines[1], cx, cy + txtPaint.textSize * 0.9f, txtPaint)
                }
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                onTouch(e); return true
            }
        }

        view.isClickable = true; view.isFocusable = false
        overlayView = view
        try { wm.addView(view, lp) }
        catch (e: Exception) {
            handler.post { Toast.makeText(this, "Overlay izni yok! Ayarlardan verin.", Toast.LENGTH_LONG).show() }
        }
    }

    // ── Touch handling ────────────────────────────────────────────────────

    private val holdAction = Runnable {
        val svc = MacroAccessibilityService.getInstance()

        if (svc == null) {
            // Clear toast so user knows why it's not working
            handler.post {
                Toast.makeText(
                    this,
                    "⚠ Erişilebilirlik Servisi kapalı!\nAyarlar → Erişilebilirlik → GG Macro Service → Aç",
                    Toast.LENGTH_LONG
                ).show()
            }
            overlayView?.post { overlayView?.invalidate() }
            return@Runnable
        }

        if (executing) return@Runnable

        executing = true
        vibrate()
        overlayView?.post { overlayView?.invalidate() }

        // with FLAG_LAYOUT_IN_SCREEN, lp.x/y == absolute screen coords
        val dp    = resources.displayMetrics.density
        val half  = 45f * dp
        val tapX  = lp.x.toFloat() + half
        val tapY  = lp.y.toFloat() + half

        handler.post {
            Toast.makeText(this, "✓ Tıklama başladı! Bırakınca durur.", Toast.LENGTH_SHORT).show()
        }

        svc.startTapLoop(tapX, tapY, tapDuration, tapDelay)
    }

    private fun onTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; moved = false
                handler.postDelayed(holdAction, holdThreshold)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX; val dy = e.rawY - downY
                if (!moved && (abs(dx) > 10 || abs(dy) > 10)) {
                    moved = true
                    handler.removeCallbacks(holdAction)
                    stopLoop()
                }
                if (moved) {
                    lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                    lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                    downX = e.rawX; downY = e.rawY
                    try { wm.updateViewLayout(overlayView, lp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(holdAction)
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun vibrate() {
        try { getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
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
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Macro — Tetik Aktif")
            .setContentText("$macroName · Basılı tut → otomatik tıklama")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
