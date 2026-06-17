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
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ggmacro.app.MainActivity
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Floating trigger button service.
 *
 * "+" control button → tap to add a new trigger button.
 * Each trigger button:
 *   - Drag to reposition (tap target = button center)
 *   - Press & hold (no drag) → AccessibilityService starts rapid tapping at button center
 *   - Release → tapping stops
 *   - After release: small "×" badge appears for 3 s → tap to delete button
 */
class FloatingTriggerButtonService : Service() {

    companion object {
        private const val TAG = "GGMacro"
        const val NOTIF_ID = 1002
        const val CHANNEL_ID = "gg_macro_trigger"

        @Volatile var isRunning = false

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, FloatingTriggerButtonService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, FloatingTriggerButtonService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager

    private val dp get() = resources.displayMetrics.density
    private val BTN_SIZE get() = (72 * dp).toInt()
    private val DEL_SIZE get() = (30 * dp).toInt()
    private val CTRL_SIZE get() = (52 * dp).toInt()
    private val DRAG_THRESH get() = 10 * dp

    // ── Trigger button state ───────────────────────────────────────────────

    private data class TapBtn(
        val id: Int,
        var view: View? = null,
        var lp: WindowManager.LayoutParams,
        @Volatile var executing: Boolean = false,
        var rawX: Float = 0f,
        var rawY: Float = 0f,
        var moved: Boolean = false,
        var delView: View? = null,
        var delLp: WindowManager.LayoutParams? = null,
        var delVisible: Boolean = false
    )

    private val buttons = mutableListOf<TapBtn>()
    private var nextId = 0

    // "+" control
    private var ctrlView: View? = null
    private var ctrlLp: WindowManager.LayoutParams? = null
    private var ctrlRawX = 0f; private var ctrlRawY = 0f; private var ctrlMoved = false

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
        buildControl()
        Log.d(TAG, "FloatingTriggerButtonService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        MacroAccessibilityService.getInstance()?.stopTapLoop()
        buttons.forEach { removeBtn(it, notify = false) }
        buttons.clear()
        removeView(ctrlView); ctrlView = null
        Log.d(TAG, "FloatingTriggerButtonService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── "+" control button ────────────────────────────────────────────────

    private fun buildControl() {
        val sz = CTRL_SIZE
        val dm = resources.displayMetrics

        ctrlLp = WindowManager.LayoutParams(
            sz, sz,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - sz - 12
            y = 180
        }

        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(230, 0, 160, 255)
        }
        val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f * dp
            color = Color.argb(180, 255, 255, 255)
        }
        val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 24f * dp
            color = Color.WHITE; isFakeBoldText = true
        }
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 6f * dp
            color = Color.argb(210, 200, 240, 255)
        }

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2f * dp
                c.drawCircle(cx, cy, r, bgP)
                c.drawCircle(cx, cy, r, ringP)
                c.drawText("+", cx, cy + txtP.textSize * 0.35f - 4f * dp, txtP)
                c.drawText("YENİ", cx, cy + r - 6f * dp, subP)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        ctrlRawX = e.rawX; ctrlRawY = e.rawY; ctrlMoved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - ctrlRawX; val dy = e.rawY - ctrlRawY
                        if (!ctrlMoved && sqrt(dx * dx + dy * dy) > DRAG_THRESH) ctrlMoved = true
                        if (ctrlMoved) {
                            ctrlLp?.let { lp ->
                                lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                                lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                                ctrlRawX = e.rawX; ctrlRawY = e.rawY
                                try { wm.updateViewLayout(ctrlView, lp) } catch (_: Exception) {}
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!ctrlMoved) addButton()
                    }
                }
                return true
            }
        }
        v.isClickable = true; ctrlView = v
        try { wm.addView(v, ctrlLp) } catch (ex: Exception) {
            Log.e(TAG, "ctrl add failed: ${ex.message}")
        }
    }

    // ── Add/remove tap buttons ─────────────────────────────────────────────

    private fun addButton() {
        val sz = BTN_SIZE
        val dm = resources.displayMetrics
        val id = nextId++

        val lp = WindowManager.LayoutParams(
            sz, sz,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels / 2 - sz / 2
            y = dm.heightPixels / 2 - sz / 2
        }

        val btn = TapBtn(id = id, lp = lp)
        buttons.add(btn)
        buildBtnView(btn)
        buildDelView(btn)
        Log.d(TAG, "addButton #$id at center")
    }

    private fun buildBtnView(btn: TapBtn) {
        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f * dp
        }
        val topP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = 10f * dp
            color = Color.WHITE
        }
        val botP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 7.5f * dp
            color = Color.argb(210, 180, 240, 255)
        }
        val numP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 7f * dp
            color = Color.argb(160, 255, 255, 255)
        }

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2.5f * dp
                val svc = MacroAccessibilityService.getInstance()

                bgP.color = when {
                    svc == null  -> Color.argb(190, 140, 0, 0)
                    btn.executing -> Color.argb(230, 220, 30, 30)
                    else          -> Color.argb(200, 0, 80, 200)
                }
                ringP.color = when {
                    svc == null  -> Color.rgb(255, 80, 80)
                    btn.executing -> Color.rgb(255, 60, 60)
                    else          -> Color.rgb(0, 200, 255)
                }
                c.drawCircle(cx, cy, r, bgP)
                c.drawCircle(cx, cy, r, ringP)

                when {
                    svc == null -> {
                        c.drawText("SERVİS", cx, cy - 3f * dp, topP)
                        c.drawText("KAPALI", cx, cy + 13f * dp, botP)
                    }
                    btn.executing -> {
                        c.drawText("◉ TIKLIYOR", cx, cy + 4f * dp, topP)
                    }
                    else -> {
                        c.drawText("BAS & TUT", cx, cy - 2f * dp, topP)
                        c.drawText("otomatik tıkla", cx, cy + 12f * dp, botP)
                    }
                }
                c.drawText("#${btn.id + 1}", cx, cy - r + 14f * dp, numP)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                handleBtnTouch(btn, e); return true
            }
        }
        v.isClickable = true; btn.view = v
        try { wm.addView(v, btn.lp) } catch (ex: Exception) {
            Log.e(TAG, "btnView add failed: ${ex.message}")
        }
    }

    private fun buildDelView(btn: TapBtn) {
        val sz = DEL_SIZE
        val dlp = WindowManager.LayoutParams(
            sz, sz,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = btn.lp.x + BTN_SIZE - sz / 2
            y = btn.lp.y - sz / 2
        }
        btn.delLp = dlp

        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(230, 200, 20, 20)
        }
        val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 15f * dp
            color = Color.WHITE; isFakeBoldText = true
        }

        val dv = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                c.drawCircle(cx, cy, cx - 1f, bgP)
                c.drawText("×", cx, cy + txtP.textSize * 0.38f, txtP)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_UP) removeBtn(btn, notify = true)
                return true
            }
        }
        dv.isClickable = true; dv.visibility = View.GONE; btn.delView = dv
        try { wm.addView(dv, dlp) } catch (ex: Exception) {
            Log.e(TAG, "delView add failed: ${ex.message}")
        }
    }

    // ── Touch handling ────────────────────────────────────────────────────

    private fun handleBtnTouch(btn: TapBtn, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                btn.rawX = e.rawX; btn.rawY = e.rawY; btn.moved = false
                hideDelete(btn)
                startTap(btn)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - btn.rawX; val dy = e.rawY - btn.rawY
                if (!btn.moved && sqrt(dx * dx + dy * dy) > DRAG_THRESH) {
                    btn.moved = true
                    stopTap(btn)
                }
                if (btn.moved) {
                    btn.lp.x = (btn.lp.x + dx.toInt()).coerceAtLeast(0)
                    btn.lp.y = (btn.lp.y + dy.toInt()).coerceAtLeast(0)
                    btn.rawX = e.rawX; btn.rawY = e.rawY
                    try { wm.updateViewLayout(btn.view, btn.lp) } catch (_: Exception) {}
                    syncDelPos(btn)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopTap(btn)
                if (!btn.moved) showDelete(btn)
            }
        }
    }

    // ── Tap control ────────────────────────────────────────────────────────

    private fun startTap(btn: TapBtn) {
        val svc = MacroAccessibilityService.getInstance() ?: run {
            Log.w(TAG, "startTap #${btn.id}: AccessibilityService kapalı")
            return
        }
        if (btn.executing) return
        btn.executing = true
        btn.view?.invalidate()

        // Tap exactly at the center of this button on screen
        val tapX = btn.lp.x.toFloat() + BTN_SIZE / 2f
        val tapY = btn.lp.y.toFloat() + BTN_SIZE / 2f
        Log.d(TAG, "startTap #${btn.id} → ($tapX, $tapY)")
        svc.startTapLoop(tapX, tapY, 50L, 50L)
    }

    private fun stopTap(btn: TapBtn) {
        if (!btn.executing) return
        btn.executing = false
        MacroAccessibilityService.getInstance()?.stopTapLoop()
        btn.view?.invalidate()
        Log.d(TAG, "stopTap #${btn.id}")
    }

    // ── Delete badge ───────────────────────────────────────────────────────

    private fun showDelete(btn: TapBtn) {
        btn.delVisible = true
        btn.delView?.visibility = View.VISIBLE
        handler.postDelayed({
            if (btn.delVisible) hideDelete(btn)
        }, 3000L)
    }

    private fun hideDelete(btn: TapBtn) {
        btn.delVisible = false
        btn.delView?.visibility = View.GONE
    }

    private fun syncDelPos(btn: TapBtn) {
        val sz = DEL_SIZE
        btn.delLp?.let { dlp ->
            dlp.x = btn.lp.x + BTN_SIZE - sz / 2
            dlp.y = btn.lp.y - sz / 2
            try { wm.updateViewLayout(btn.delView, dlp) } catch (_: Exception) {}
        }
    }

    private fun removeBtn(btn: TapBtn, notify: Boolean) {
        stopTap(btn)
        removeView(btn.view); btn.view = null
        removeView(btn.delView); btn.delView = null
        if (notify) buttons.remove(btn)
        Log.d(TAG, "removeBtn #${btn.id}")
    }

    private fun removeView(v: View?) {
        v?.let { try { wm.removeView(it) } catch (_: Exception) {} }
    }

    // ── Notification ───────────────────────────────────────────────────────

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "GG Macro Tetik", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Macro — Tetik Aktif")
            .setContentText("+ butonuna bas → yeni tetik | Tetik'e bas-tut → otomatik tıkla")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
