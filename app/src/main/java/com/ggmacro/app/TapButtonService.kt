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

/**
 * v5 — Ayrı Kontrol + Hedef mimarisi
 *
 * Sorun: Kontrol butonu ile tıklama noktası aynıyken FLAG_NOT_TOUCHABLE
 * ACTION_UP olayını iletmediği için "basılı tut → el çek → dur" UX'i çalışmıyordu.
 *
 * Çözüm: Gesture'lar HEDEF (⊕) konumuna enjekte edilir, KONTROL butonuna değil.
 * Kontrol butonu normal (dokunulabilir) kalır → ACTION_UP garantili gelir → el çekince durur.
 */
class TapButtonService : Service() {

    companion object {
        private const val NOTIF_ID = 101
        private const val CHANNEL  = "tap_btn_ch"
        @Volatile var running = false
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, TapButtonService::class.java))
        fun stop(ctx: Context)  = ctx.stopService(Intent(ctx, TapButtonService::class.java))
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager

    private val dp   by lazy { resources.displayMetrics.density }
    private val BTN  get() = (64 * dp).toInt()   // kontrol butonu
    private val TGT  get() = (52 * dp).toInt()   // hedef nişangahı
    private val DEL  get() = (36 * dp).toInt()   // silme rozeti
    private val CTRL get() = (52 * dp).toInt()   // "+" ekleme butonu
    private val TH   get() = 10f * dp             // sürükleme eşiği

    private val BASE get() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                             WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                             WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    // ── "+" ekleme butonu ──────────────────────────────────────────────────
    private var cv: View? = null; private var clp: WindowManager.LayoutParams? = null
    private var crx = 0f; private var cry = 0f; private var cm = false

    // ── Tıklama çifti (kontrol + hedef) ───────────────────────────────────
    private data class Btn(
        val id: Int,
        var v:    View? = null, var lp:  WindowManager.LayoutParams,   // kontrol
        var tv:   View? = null, var tlp: WindowManager.LayoutParams,   // hedef
        var dv:   View? = null, var dlp: WindowManager.LayoutParams? = null, // sil
        @Volatile var on: Boolean = false,
        var rx: Float = 0f, var ry: Float = 0f, var mv: Boolean = false,    // kontrol drag
        var trx: Float = 0f, var tRy: Float = 0f, var tmv: Boolean = false, // hedef drag
        var ds: Boolean = false,
        var holdR: Runnable? = null
    )
    private val list = mutableListOf<Btn>(); private var nid = 0

    // ── Yaşam döngüsü ─────────────────────────────────────────────────────
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
        list.forEach { rm(it) }; list.clear(); rmv(cv); cv = null; super.onDestroy()
    }

    // ── "+" butonu ─────────────────────────────────────────────────────────
    private fun mkCtrl() {
        val sz = CTRL; val dm = resources.displayMetrics
        clp = wlp(sz, sz, BASE).apply { x = dm.widthPixels - sz - 16; y = 220 }
        val bg = pf(Color.argb(240, 10, 140, 255)); val rg = ps(Color.argb(180, 255, 255, 255), 2.5f * dp)
        val t1 = pt(22f * dp, Color.WHITE); val t2 = pt(6f * dp, Color.argb(200, 200, 240, 255))
        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2f * dp
                c.drawCircle(cx, cy, r, bg); c.drawCircle(cx, cy, r, rg)
                c.drawText("+", cx, cy + t1.textSize * 0.3f - 2f * dp, t1)
                c.drawText("YENİ", cx, cy + r - 4f * dp, t2)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { crx = e.rawX; cry = e.rawY; cm = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - crx; val dy = e.rawY - cry
                        if (!cm && sqrt(dx * dx + dy * dy) > TH) cm = true
                        if (cm) clp?.let { lp ->
                            lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                            lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                            crx = e.rawX; cry = e.rawY
                            try { wm.updateViewLayout(cv, lp) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> { if (!cm) addBtn() }
                }; return true
            }
        }
        v.isClickable = true; cv = v; try { wm.addView(v, clp) } catch (_: Exception) {}
    }

    // ── Yeni çift oluştur ──────────────────────────────────────────────────
    private fun addBtn() {
        val dm = resources.displayMetrics; val id = nid++
        val cx = dm.widthPixels / 2; val cy = dm.heightPixels / 2
        // Kontrol butonu ekranın üst yarısında, hedef alt yarısında başlar
        val lp  = wlp(BTN, BTN, BASE).apply { x = cx - BTN / 2; y = cy - BTN / 2 - (80 * dp).toInt() }
        val tlp = wlp(TGT, TGT, BASE).apply { x = cx - TGT / 2; y = cy - TGT / 2 + (80 * dp).toInt() }
        val b = Btn(id = id, lp = lp, tlp = tlp)
        list.add(b); mkBv(b); mkTv(b); mkDv(b)
    }

    // ── Kontrol butonu görünümü ────────────────────────────────────────────
    private fun mkBv(b: Btn) {
        val bg = pf(0); val rg = ps(0, 3f * dp)
        val t1 = pt(9f * dp, Color.WHITE).also { it.isFakeBoldText = true }
        val t2 = pt(7f * dp, Color.argb(215, 180, 240, 255))
        val tn = pt(6.5f * dp, Color.argb(140, 255, 255, 255))
        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2.5f * dp
                val svc = TapService.get()
                bg.color = when { svc == null -> Color.argb(200, 120, 0, 0); b.on -> Color.argb(235, 215, 25, 25); else -> Color.argb(215, 0, 80, 205) }
                rg.color = when { svc == null -> Color.rgb(255, 70, 70); b.on -> Color.rgb(255, 50, 50); else -> Color.rgb(0, 200, 255) }
                c.drawCircle(cx, cy, r, bg); c.drawCircle(cx, cy, r, rg)
                when {
                    svc == null -> { c.drawText("ERİŞİM", cx, cy - 2f * dp, t1); c.drawText("VER →", cx, cy + 13f * dp, t2) }
                    b.on        -> { c.drawText("◉ TIKLIYOR", cx, cy - 2f * dp, t1); c.drawText("el çek → dur", cx, cy + 13f * dp, t2) }
                    else        -> { c.drawText("BAS & TUT", cx, cy - 2f * dp, t1); c.drawText("başlatmak için", cx, cy + 13f * dp, t2) }
                }
                c.drawText("#${b.id + 1}", cx, cy - r + 12f * dp, tn)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean { touchCtrl(b, e); return true }
        }
        v.isClickable = true; b.v = v; try { wm.addView(v, b.lp) } catch (_: Exception) {}
    }

    // ── Hedef nişangahı görünümü ───────────────────────────────────────────
    private fun mkTv(b: Btn) {
        val ring  = ps(Color.argb(230, 255, 220,  0), 2.5f * dp)
        val ringA = ps(Color.argb(230, 255,  60,  0), 2.5f * dp)
        val dot   = pf(Color.argb(240, 255,  60,  0))
        val lbl   = pt(6.5f * dp, Color.argb(210, 255, 255, 200))
        val tv = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 3f * dp
                val p = if (b.on) ringA else ring
                c.drawCircle(cx, cy, r, p)
                val arm = r * 0.42f
                c.drawLine(cx - r, cy, cx - r + arm, cy, p)
                c.drawLine(cx + r - arm, cy, cx + r, cy, p)
                c.drawLine(cx, cy - r, cx, cy - r + arm, p)
                c.drawLine(cx, cy + r - arm, cx, cy + r, p)
                c.drawCircle(cx, cy, 3.5f * dp, dot)
                c.drawText("⊕ HEDEF #${b.id + 1}", cx, cy + r + 10f * dp, lbl)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean { touchTarget(b, e); return true }
        }
        tv.isClickable = true; b.tv = tv; try { wm.addView(tv, b.tlp) } catch (_: Exception) {}
    }

    // ── Silme rozeti ───────────────────────────────────────────────────────
    private fun mkDv(b: Btn) {
        val sz = DEL
        val dlp = wlp(sz, sz, BASE).apply { x = b.lp.x + BTN - sz / 2; y = b.lp.y - sz / 2 }
        b.dlp = dlp
        val bg = pf(Color.argb(240, 200, 20, 20))
        val t  = pt(14f * dp, Color.WHITE).also { it.isFakeBoldText = true }
        val dv = object : View(this) {
            override fun onDraw(c: Canvas) {
                c.drawCircle(width / 2f, height / 2f, width / 2f - 1f, bg)
                c.drawText("×", width / 2f, height / 2f + t.textSize * 0.38f, t)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_UP) { rm(b); list.remove(b) }
                return true
            }
        }
        dv.isClickable = true; dv.visibility = View.GONE; b.dv = dv
        try { wm.addView(dv, dlp) } catch (_: Exception) {}
    }

    // ── Kontrol butonu dokunuşu ────────────────────────────────────────────
    private fun touchCtrl(b: Btn, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                b.rx = e.rawX; b.ry = e.rawY; b.mv = false
                if (!b.on) {
                    hideD(b)
                    // 150ms hareketsiz bas → tıklamayı başlat
                    b.holdR?.let { h.removeCallbacks(it) }
                    b.holdR = Runnable { b.holdR = null; if (!b.mv) on(b) }
                    h.postDelayed(b.holdR!!, 150L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - b.rx; val dy = e.rawY - b.ry
                if (!b.mv && sqrt(dx * dx + dy * dy) > TH) {
                    b.mv = true
                    b.holdR?.let { h.removeCallbacks(it) }; b.holdR = null
                }
                if (b.mv && !b.on) {
                    b.lp.x = (b.lp.x + dx.toInt()).coerceAtLeast(0)
                    b.lp.y = (b.lp.y + dy.toInt()).coerceAtLeast(0)
                    b.rx = e.rawX; b.ry = e.rawY
                    try { wm.updateViewLayout(b.v, b.lp) } catch (_: Exception) {}
                    b.dlp?.let { d ->
                        d.x = b.lp.x + BTN - DEL / 2; d.y = b.lp.y - DEL / 2
                        try { wm.updateViewLayout(b.dv, d) } catch (_: Exception) {}
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                b.holdR?.let { h.removeCallbacks(it) }; b.holdR = null
                when {
                    b.on -> off(b)    // ★ el çekildi → DURDUR (FLAG_NOT_TOUCHABLE YOK → ACTION_UP garantili gelir)
                    b.mv -> showD(b)
                }
            }
        }
    }

    // ── Hedef nişangahı dokunuşu ───────────────────────────────────────────
    private fun touchTarget(b: Btn, e: MotionEvent) {
        if (b.on) return  // tıklama aktifken hedefi taşıma
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { b.trx = e.rawX; b.tRy = e.rawY; b.tmv = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - b.trx; val dy = e.rawY - b.tRy
                if (!b.tmv && sqrt(dx * dx + dy * dy) > TH) b.tmv = true
                if (b.tmv) {
                    b.tlp.x = (b.tlp.x + dx.toInt()).coerceAtLeast(0)
                    b.tlp.y = (b.tlp.y + dy.toInt()).coerceAtLeast(0)
                    b.trx = e.rawX; b.tRy = e.rawY
                    try { wm.updateViewLayout(b.tv, b.tlp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP -> {}
        }
    }

    // ── Tıklama aç ────────────────────────────────────────────────────────
    private fun on(b: Btn) {
        val svc = TapService.get() ?: return
        if (b.on) return; b.on = true
        b.v?.invalidate(); b.tv?.invalidate()
        // ★ TEMEL ÇÖZÜM: Gesture HEDEF konumuna ateşlenir, kontrol butonu değil!
        //   Kontrol butonu dokunuşları almaya devam eder → el çekilince ACTION_UP gelir.
        svc.startTapping(b.tlp.x.toFloat() + TGT / 2f, b.tlp.y.toFloat() + TGT / 2f)
    }

    // ── Tıklama kapat ─────────────────────────────────────────────────────
    private fun off(b: Btn) {
        if (!b.on) return; b.on = false
        TapService.get()?.stopTapping()
        b.v?.invalidate(); b.tv?.invalidate()
    }

    private fun showD(b: Btn) {
        if (b.on) return; b.ds = true; b.dv?.visibility = View.VISIBLE; b.dv?.invalidate()
        h.postDelayed({ if (b.ds && !b.on) hideD(b) }, 3000L)
    }
    private fun hideD(b: Btn) { if (b.on) return; b.ds = false; b.dv?.visibility = View.GONE }
    private fun rm(b: Btn) { off(b); rmv(b.v); b.v = null; rmv(b.tv); b.tv = null; rmv(b.dv); b.dv = null }

    // ── Yardımcılar ───────────────────────────────────────────────────────
    private fun wlp(w: Int, h: Int, f: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, f, PixelFormat.TRANSLUCENT
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
            .setContentTitle("GG Macro Aktif")
            .setContentText("⊕ Hedefi sürükle → oyun tuşuna | Kontrol: Bas&Tut → Başlar | El çek → Durur")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
