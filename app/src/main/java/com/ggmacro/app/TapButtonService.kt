package com.ggmacro.app

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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class TapButtonService : Service() {

    companion object {
        private const val NOTIF_ID = 101
        private const val CHANNEL = "tap_btn_ch"
        @Volatile var running = false

        fun start(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, TapButtonService::class.java))
        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, TapButtonService::class.java))
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager

    private val dp by lazy { resources.displayMetrics.density }
    private val BTN get() = (72 * dp).toInt()
    private val DEL get() = (30 * dp).toInt()
    private val CTRL get() = (52 * dp).toInt()
    private val TH get() = 10f * dp

    // "+" control button
    private var cv: View? = null
    private var clp: WindowManager.LayoutParams? = null
    private var crx = 0f; private var cry = 0f; private var cm = false

    // Each tap button
    private data class Btn(
        val id: Int,
        var v: View? = null, var lp: WindowManager.LayoutParams,
        @Volatile var on: Boolean = false,
        var rx: Float = 0f, var ry: Float = 0f, var mv: Boolean = false,
        var dv: View? = null, var dlp: WindowManager.LayoutParams? = null,
        var ds: Boolean = false
    )
    private val list = mutableListOf<Btn>()
    private var nid = 0

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate(); running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        mkCh(); startForeground(NOTIF_ID, mkNot()); mkCtrl()
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() {
        running = false; h.removeCallbacksAndMessages(null)
        TapService.get()?.stopTapping()
        list.forEach { rm(it) }; list.clear()
        rmv(cv); cv = null; super.onDestroy()
    }

    // ── "+" control button ─────────────────────────────────────────────────

    private fun mkCtrl() {
        val sz = CTRL; val dm = resources.displayMetrics
        clp = wlp(sz, sz).apply { x = dm.widthPixels - sz - 16; y = 220 }

        val bg = pf(Color.argb(240, 10, 140, 255))
        val rg = ps(Color.argb(180, 255, 255, 255), 2.5f * dp)
        val t1 = pt(22f * dp, Color.WHITE)
        val t2 = pt(6.5f * dp, Color.argb(200, 200, 240, 255))

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2f * dp
                c.drawCircle(cx, cy, r, bg); c.drawCircle(cx, cy, r, rg)
                c.drawText("+", cx, cy + t1.textSize * 0.3f - 2f * dp, t1)
                c.drawText("YENİ BUTON", cx, cy + r - 4f * dp, t2)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { crx = e.rawX; cry = e.rawY; cm = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - crx; val dy = e.rawY - cry
                        if (!cm && sqrt(dx * dx + dy * dy) > TH) cm = true
                        if (cm) { clp?.let { lp ->
                            lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                            lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                            crx = e.rawX; cry = e.rawY
                            try { wm.updateViewLayout(cv, lp) } catch (_: Exception) {} } }
                    }
                    MotionEvent.ACTION_UP -> { if (!cm) addBtn() }
                }; return true
            }
        }
        v.isClickable = true; cv = v
        try { wm.addView(v, clp) } catch (_: Exception) {}
    }

    // ── Tap button ─────────────────────────────────────────────────────────

    private fun addBtn() {
        val sz = BTN; val dm = resources.displayMetrics; val id = nid++
        val lp = wlp(sz, sz).apply {
            x = dm.widthPixels / 2 - sz / 2; y = dm.heightPixels / 2 - sz / 2
        }
        val b = Btn(id = id, lp = lp); list.add(b); mkBv(b); mkDv(b)
    }

    private fun mkBv(b: Btn) {
        val bg = pf(0); val rg = ps(0, 3f * dp)
        val t1 = pt(10f * dp, Color.WHITE).also { it.isFakeBoldText = true }
        val t2 = pt(7.5f * dp, Color.argb(215, 180, 240, 255))
        val tn = pt(7f * dp, Color.argb(140, 255, 255, 255))

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2.5f * dp
                val svc = TapService.get()
                bg.color = when { svc == null -> Color.argb(200, 120, 0, 0); b.on -> Color.argb(235, 215, 25, 25); else -> Color.argb(215, 0, 80, 205) }
                rg.color = when { svc == null -> Color.rgb(255, 70, 70); b.on -> Color.rgb(255, 50, 50); else -> Color.rgb(0, 200, 255) }
                c.drawCircle(cx, cy, r, bg); c.drawCircle(cx, cy, r, rg)
                when {
                    svc == null -> { c.drawText("SERVİS", cx, cy - 3f * dp, t1); c.drawText("KAPALI", cx, cy + 14f * dp, t2) }
                    b.on -> c.drawText("◉ TIKLIYOR", cx, cy + 4f * dp, t1)
                    else -> { c.drawText("BAS & TUT", cx, cy - 2f * dp, t1); c.drawText("otomatik tıkla", cx, cy + 13f * dp, t2) }
                }
                c.drawText("#${b.id + 1}", cx, cy - r + 14f * dp, tn)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean { touch(b, e); return true }
        }
        v.isClickable = true; b.v = v
        try { wm.addView(v, b.lp) } catch (_: Exception) {}
    }

    private fun mkDv(b: Btn) {
        val sz = DEL; val dlp = wlp(sz, sz).apply { x = b.lp.x + BTN - sz / 2; y = b.lp.y - sz / 2 }
        b.dlp = dlp
        val bg = pf(Color.argb(240, 200, 20, 20))
        val t = pt(15f * dp, Color.WHITE).also { it.isFakeBoldText = true }
        val dv = object : View(this) {
            override fun onDraw(c: Canvas) {
                c.drawCircle(width / 2f, height / 2f, width / 2f - 1f, bg)
                c.drawText("×", width / 2f, height / 2f + t.textSize * 0.38f, t)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_UP) { rm(b); list.remove(b) }; return true
            }
        }
        dv.isClickable = true; dv.visibility = View.GONE; b.dv = dv
        try { wm.addView(dv, dlp) } catch (_: Exception) {}
    }

    // ── Touch ──────────────────────────────────────────────────────────────

    private fun touch(b: Btn, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { b.rx = e.rawX; b.ry = e.rawY; b.mv = false; hideD(b); on(b) }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - b.rx; val dy = e.rawY - b.ry
                if (!b.mv && sqrt(dx * dx + dy * dy) > TH) { b.mv = true; off(b) }
                if (b.mv) {
                    b.lp.x = (b.lp.x + dx.toInt()).coerceAtLeast(0)
                    b.lp.y = (b.lp.y + dy.toInt()).coerceAtLeast(0)
                    b.rx = e.rawX; b.ry = e.rawY
                    try { wm.updateViewLayout(b.v, b.lp) } catch (_: Exception) {}
                    b.dlp?.let { d -> d.x = b.lp.x + BTN - DEL / 2; d.y = b.lp.y - DEL / 2
                        try { wm.updateViewLayout(b.dv, d) } catch (_: Exception) {} }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { off(b); if (!b.mv) showD(b) }
        }
    }

    private fun on(b: Btn) {
        val svc = TapService.get() ?: return
        if (b.on) return; b.on = true; b.v?.invalidate()
        svc.startTapping(b.lp.x.toFloat() + BTN / 2f, b.lp.y.toFloat() + BTN / 2f)
    }
    private fun off(b: Btn) {
        if (!b.on) return; b.on = false
        TapService.get()?.stopTapping(); b.v?.invalidate()
    }
    private fun showD(b: Btn) {
        b.ds = true; b.dv?.visibility = View.VISIBLE
        h.postDelayed({ if (b.ds) hideD(b) }, 3000L)
    }
    private fun hideD(b: Btn) { b.ds = false; b.dv?.visibility = View.GONE }
    private fun rm(b: Btn) { off(b); rmv(b.v); b.v = null; rmv(b.dv); b.dv = null }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun wlp(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun pf(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = c; it.style = Paint.Style.FILL }
    private fun ps(c: Int, sw: Float) = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = c; it.style = Paint.Style.STROKE; it.strokeWidth = sw }
    private fun pt(sz: Float, c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).also { it.textAlign = Paint.Align.CENTER; it.textSize = sz; it.color = c }
    private fun rmv(v: View?) = v?.let { try { wm.removeView(it) } catch (_: Exception) {} }

    private fun mkCh() {
        NotificationChannel(CHANNEL, "GG Macro Tetik", NotificationManager.IMPORTANCE_LOW)
            .also { it.setShowBadge(false) }
            .let { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
    }
    private fun mkNot(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("GG Macro — Tetik Aktif")
            .setContentText("+ butonuna bas → yeni tetik | Bas-tut → otomatik tıkla")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
