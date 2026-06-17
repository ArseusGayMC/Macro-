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
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ggmacro.app.MainActivity
import kotlin.math.abs

/**
 * İKİ overlay:
 *  1. Trigger button  — kullanıcı BUNU basılı tutar (ekran kenarında)
 *  2. Crosshair       — tıklamanın nereye gideceğini gösterir, sürüklenebilir
 *
 * Tıklama crosshair pozisyonuna gider.
 * Tıklama sırasında crosshair FLAG_NOT_TOUCHABLE olur → gesture game'e ulaşır.
 */
class FloatingTriggerButtonService : Service() {

    companion object {
        private const val TAG = "GGMacro"

        const val NOTIF_ID            = 1002
        const val CHANNEL_ID          = "gg_macro_trigger"
        const val EXTRA_MACRO_NAME    = "macro_name"
        const val EXTRA_TAP_DURATION  = "tap_duration"
        const val EXTRA_TAP_DELAY     = "tap_delay"

        @Volatile var isRunning = false

        fun start(ctx: Context, macroName: String,
                  tapDuration: Long, tapDelay: Long) {
            ctx.startForegroundService(Intent(ctx, FloatingTriggerButtonService::class.java).apply {
                putExtra(EXTRA_MACRO_NAME,   macroName)
                putExtra(EXTRA_TAP_DURATION, tapDuration)
                putExtra(EXTRA_TAP_DELAY,    tapDelay)
            })
        }

        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, FloatingTriggerButtonService::class.java))
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager

    private var macroName   = "Macro"
    private var tapDuration = 50L
    private var tapDelay    = 50L

    // Trigger button
    private var btnView: View? = null
    private lateinit var btnLp: WindowManager.LayoutParams
    private var btnDownX = 0f; private var btnDownY = 0f; private var btnMoved = false

    // Crosshair
    private var hairView: View? = null
    private lateinit var hairLp: WindowManager.LayoutParams
    private var hairDownX = 0f; private var hairDownY = 0f

    @Volatile private var executing = false

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
        Log.d(TAG, "FloatingTriggerButtonService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        macroName   = intent?.getStringExtra(EXTRA_MACRO_NAME)?.ifBlank { "Macro" } ?: "Macro"
        tapDuration = (intent?.getLongExtra(EXTRA_TAP_DURATION, 50L) ?: 50L).coerceAtLeast(1L)
        tapDelay    = (intent?.getLongExtra(EXTRA_TAP_DELAY,    50L) ?: 50L).coerceAtLeast(16L)
        if (btnView  == null) buildTriggerButton()
        if (hairView == null) buildCrosshair()
        Log.d(TAG, "onStartCommand: macro=$macroName tapDuration=$tapDuration tapDelay=$tapDelay")
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopMacro()
        handler.removeCallbacksAndMessages(null)
        removeView(btnView);  btnView  = null
        removeView(hairView); hairView = null
        Log.d(TAG, "FloatingTriggerButtonService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Trigger button ────────────────────────────────────────────────────

    private fun buildTriggerButton() {
        val dp = resources.displayMetrics.density
        val sz = (88 * dp).toInt()

        btnLp = baseParams(sz, sz).apply {
            x = 20; y = 400    // left edge
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f * dp
            pathEffect = DashPathEffect(floatArrayOf(10f * dp, 5f * dp), 0f)
        }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; isFakeBoldText = true
            textSize = 10f * dp; color = Color.WHITE
        }

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                val r  = width / 2f - 5f * dp
                val svc = MacroAccessibilityService.getInstance()

                bgPaint.color = when {
                    svc == null -> Color.argb(190, 140, 0, 0)
                    executing   -> Color.argb(200, 220, 30, 30)
                    else        -> Color.argb(55,  0, 170, 255)
                }
                ringPaint.color = when {
                    svc == null -> Color.rgb(220, 30, 30)
                    executing   -> Color.rgb(255,  0,  0)
                    else        -> Color.rgb(0, 191, 255)
                }
                c.drawCircle(cx, cy, r, bgPaint)
                c.drawCircle(cx, cy, r, ringPaint)

                val lines = when {
                    svc == null -> listOf("SERVİS", "KAPALI")
                    executing   -> listOf("◉ TIKLIYOR")
                    else        -> {
                        val n = macroName.take(12)
                        if (n.length <= 7) listOf(n)
                        else listOf(n.take(6), n.drop(6))
                    }
                }
                txtPaint.textSize = (if (lines.size == 1) 10f else 9f) * dp
                if (lines.size == 1) {
                    c.drawText(lines[0], cx, cy + txtPaint.textSize / 3f, txtPaint)
                } else {
                    c.drawText(lines[0], cx, cy - txtPaint.textSize * 0.6f, txtPaint)
                    c.drawText(lines[1], cx, cy + txtPaint.textSize * 0.9f, txtPaint)
                }
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                onBtnTouch(e)
                return true
            }
        }
        v.isClickable = true
        btnView = v
        try { wm.addView(v, btnLp) } catch (ex: Exception) {
            Log.e(TAG, "Failed to add trigger button: ${ex.message}")
        }
    }

    /**
     * Starts the macro immediately without any delay.
     * Guard against duplicate calls with [executing] flag.
     */
    private fun startMacro() {
        val svc = MacroAccessibilityService.getInstance()
        if (svc == null) {
            Log.w(TAG, "startMacro: AccessibilityService is null — showing toast")
            handler.post {
                Toast.makeText(this,
                    "⚠ Erişilebilirlik Servisi kapalı!\nAyarlar → Erişilebilirlik → GG Macro → Aç",
                    Toast.LENGTH_LONG).show()
            }
            return
        }

        if (executing) {
            Log.d(TAG, "startMacro: already executing, skip duplicate start")
            return
        }

        executing = true
        btnView?.post { btnView?.invalidate() }

        // Make crosshair FLAG_NOT_TOUCHABLE so gestures reach the game
        hairLp.flags = hairLp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { wm.updateViewLayout(hairView, hairLp) } catch (ex: Exception) {
            Log.e(TAG, "updateViewLayout (crosshair not-touchable): ${ex.message}")
        }

        val dp   = resources.displayMetrics.density
        val half = 40f * dp
        val tapX = hairLp.x.toFloat() + half
        val tapY = hairLp.y.toFloat() + half

        Log.d(TAG, "startMacro: tapping at ($tapX, $tapY) dur=$tapDuration delay=$tapDelay")
        handler.post {
            Toast.makeText(this, "● Tıklama başladı — bırakınca durur", Toast.LENGTH_SHORT).show()
        }

        svc.startTapLoop(tapX, tapY, tapDuration, tapDelay)
    }

    /**
     * Stops the macro immediately.
     */
    private fun stopMacro() {
        if (!executing) return
        executing = false
        Log.d(TAG, "stopMacro: tap loop stopped")
        MacroAccessibilityService.getInstance()?.stopTapLoop()

        // Re-enable crosshair dragging
        hairLp.flags = hairLp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try { wm.updateViewLayout(hairView, hairLp) } catch (ex: Exception) {
            Log.e(TAG, "updateViewLayout (crosshair touchable): ${ex.message}")
        }

        btnView?.post { btnView?.invalidate() }
    }

    private fun onBtnTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "ACTION_DOWN — starting macro immediately")
                btnDownX = e.rawX; btnDownY = e.rawY; btnMoved = false
                // ✅ Start immediately on press — no delay
                startMacro()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - btnDownX; val dy = e.rawY - btnDownY
                if (!btnMoved && (abs(dx) > 12 || abs(dy) > 12)) {
                    // User is dragging the button — cancel macro and allow drag
                    btnMoved = true
                    Log.d(TAG, "ACTION_MOVE: drag detected, stopping macro")
                    stopMacro()
                }
                if (btnMoved) {
                    btnLp.x = (btnLp.x + dx.toInt()).coerceAtLeast(0)
                    btnLp.y = (btnLp.y + dy.toInt()).coerceAtLeast(0)
                    btnDownX = e.rawX; btnDownY = e.rawY
                    try { wm.updateViewLayout(btnView, btnLp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "ACTION_UP — stopping macro")
                stopMacro()
            }
            MotionEvent.ACTION_CANCEL -> {
                Log.d(TAG, "ACTION_CANCEL — stopping macro")
                stopMacro()
            }
        }
    }

    // ── Crosshair ──────────────────────────────────────────────────────────

    private fun buildCrosshair() {
        val dp = resources.displayMetrics.density
        val sz = (80 * dp).toInt()

        val dm = resources.displayMetrics
        hairLp = baseParams(sz, sz).apply {
            x = dm.widthPixels  / 2 - sz / 2
            y = dm.heightPixels / 2 - sz / 2
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f * dp
            color = Color.rgb(255, 80, 80)
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f * dp
            color = Color.argb(200, 255, 80, 80)
        }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 8f * dp
            color = Color.rgb(255, 120, 120)
        }

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                val arm = width * 0.28f

                c.drawLine(cx - arm, cy, cx + arm, cy, linePaint)
                c.drawLine(cx, cy - arm, cx, cy + arm, linePaint)
                c.drawCircle(cx, cy, arm * 0.55f, circlePaint)
                c.drawText("HEDEF", cx, cy + arm + 12f * resources.displayMetrics.density, txtPaint)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                onHairTouch(e); return true
            }
        }
        v.isClickable = true
        hairView = v
        try { wm.addView(v, hairLp) } catch (ex: Exception) {
            Log.e(TAG, "Failed to add crosshair: ${ex.message}")
        }
    }

    private fun onHairTouch(e: MotionEvent) {
        // Only draggable when macro is not executing (FLAG_NOT_TOUCHABLE is set during execution)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { hairDownX = e.rawX; hairDownY = e.rawY }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - hairDownX; val dy = e.rawY - hairDownY
                hairLp.x = (hairLp.x + dx.toInt()).coerceAtLeast(0)
                hairLp.y = (hairLp.y + dy.toInt()).coerceAtLeast(0)
                hairDownX = e.rawX; hairDownY = e.rawY
                try { wm.updateViewLayout(hairView, hairLp) } catch (_: Exception) {}
            }
            MotionEvent.ACTION_UP -> {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Base WindowManager params for overlay views.
     * FLAG_NOT_FOCUSABLE  — don't steal keyboard focus
     * FLAG_NOT_TOUCH_MODAL — pass touch events outside our view to underlying apps
     * FLAG_LAYOUT_IN_SCREEN — place relative to screen, not parent window
     *
     * NOTE: Do NOT add FLAG_NOT_TOUCHABLE here — that would block our own touch events.
     */
    private fun baseParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun removeView(v: View?) {
        v?.let { try { wm.removeView(it) } catch (_: Exception) {} }
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "GG Macro Tetik", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Macro — Tetik Aktif")
            .setContentText("Mavi butonu bas → tıklama başlar | Bırak → durur | Kırmızı hedefi sürükle")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
