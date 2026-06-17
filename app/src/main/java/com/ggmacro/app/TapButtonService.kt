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
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

/**
 * v6 — Tek buton, basılı tut → aktif, el çek → durur.
 *
 * Temel prensip: FLAG_NOT_TOUCHABLE Android'de sadece YENİ dokunuşları engeller.
 * Devam eden dokunuş serisi (ACTION_DOWN'dan başlayan) daima aynı pencereye iletilir
 * (Android InputDispatcher touch ownership garantisi). Bu sayede:
 *   ACTION_DOWN → pencere tutar → hold timer 100ms → on() → FLAG_NOT_TOUCHABLE set
 *   Enjekte gesture'lar → geçer alttaki uygulamaya
 *   Parmak kaldırılır → ACTION_UP hâlâ bu pencereye gelir → off()
 */
class TapButtonService : Service() {

    companion object {
        private const val TAG    = "GGMacro"
        private const val NOTIF_ID = 101
        private const val CHANNEL  = "tap_btn_ch"
        @Volatile var running = false
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, TapButtonService::class.java))
        fun stop(ctx: Context)  = ctx.stopService(Intent(ctx, TapButtonService::class.java))
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager

    private val dp   by lazy { resources.displayMetrics.density }
    private val BTN  get() = (72 * dp).toInt()
    private val DEL  get() = (38 * dp).toInt()
    private val CTRL get() = (52 * dp).toInt()
    private val TH   get() = 10f * dp

    private val BASE get() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                             WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                             WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    // ── "+" ekleme butonu ──────────────────────────────────────────────────
    private var cv: View? = null; private var clp: WindowManager.LayoutParams? = null
    private var crx = 0f; private var cry = 0f; private var cm = false

    // ── Tıklama butonu ─────────────────────────────────────────────────────
    private data class Btn(
        val id: Int,
        var v:   View? = null,
        var lp:  WindowManager.LayoutParams,
        var dv:  View? = null, var dlp: WindowManager.LayoutParams? = null,
        @Volatile var on: Boolean = false,
        var rx: Float = 0f, var ry: Float = 0f, var mv: Boolean = false,
        var ds: Boolean = false,
        var holdR: Runnable? = null,
        var safetyR: Runnable? = null   // 60s güvenlik zamanlayıcısı
    )
    private val list = mutableListOf<Btn>(); private var nid = 0

    // ── Yaşam döngüsü ─────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate(); running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        mkCh(); startForeground(NOTIF_ID, mkNot()); mkCtrl()
        Log.d(TAG, "TapButtonService started")
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() {
        running = false; h.removeCallbacksAndMessages(null)
        TapService.get()?.stopTapping()
        list.forEach { rm(it) }; list.clear(); rmv(cv); cv = null
        Log.d(TAG, "TapButtonService destroyed")
        super.onDestroy()
    }

    // ── "+" kontrol butonu ─────────────────────────────────────────────────
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

    // ── Tıklama butonu oluştur ─────────────────────────────────────────────
    private fun addBtn() {
        val dm = resources.displayMetrics; val id = nid++
        val lp = wlp(BTN, BTN, BASE).apply {
            x = dm.widthPixels / 2 - BTN / 2
            y = dm.heightPixels / 2 - BTN / 2
        }
        val b = Btn(id = id, lp = lp); list.add(b); mkBv(b); mkDv(b)
        Log.d(TAG, "Button #$id created at (${lp.x}, ${lp.y})")
    }

    private fun mkBv(b: Btn) {
        val bgp = pf(0); val rgp = ps(0, 3f * dp)
        val t1  = pt(10f * dp, Color.WHITE).also { it.isFakeBoldText = true }
        val t2  = pt(7.5f * dp, Color.argb(215, 180, 240, 255))
        val tn  = pt(7f   * dp, Color.argb(140, 255, 255, 255))
        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2.5f * dp
                val svc = TapService.get()
                bgp.color = when { svc == null -> Color.argb(200, 100, 0, 0); b.on -> Color.argb(235, 210, 20, 20); else -> Color.argb(215, 0, 80, 200) }
                rgp.color = when { svc == null -> Color.rgb(255, 80, 80);     b.on -> Color.rgb(255, 40, 40);       else -> Color.rgb(0, 200, 255) }
                c.drawCircle(cx, cy, r, bgp); c.drawCircle(cx, cy, r, rgp)
                when {
                    svc == null -> { c.drawText("❌ ERİŞİM", cx, cy - 2f * dp, t1); c.drawText("GEREKLİ →", cx, cy + 14f * dp, t2) }
                    b.on        -> { c.drawText("◉ TIKLIYOR", cx, cy - 2f * dp, t1); c.drawText("el çek → dur", cx, cy + 14f * dp, t2) }
                    else        -> { c.drawText("BAS & TUT", cx, cy - 2f * dp, t1); c.drawText("tıklamayı başlat", cx, cy + 14f * dp, t2) }
                }
                c.drawText("#${b.id + 1}", cx, cy - r + 13f * dp, tn)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean { touch(b, e); return true }
        }
        v.isClickable = true; b.v = v; try { wm.addView(v, b.lp) } catch (_: Exception) {}
    }

    private fun mkDv(b: Btn) {
        val sz = DEL
        val dlp = wlp(sz, sz, BASE).apply { x = b.lp.x + BTN - sz / 2; y = b.lp.y - sz / 2 }
        b.dlp = dlp
        val bgDel  = pf(Color.argb(240, 200, 20, 20))
        val bgStop = pf(Color.argb(240,  20, 160, 40))
        val t = pt(15f * dp, Color.WHITE).also { it.isFakeBoldText = true }
        val dv = object : View(this) {
            override fun onDraw(c: Canvas) {
                c.drawCircle(width / 2f, height / 2f, width / 2f - 1f, if (b.on) bgStop else bgDel)
                c.drawText(if (b.on) "■" else "×", width / 2f, height / 2f + t.textSize * 0.38f, t)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    Log.d(TAG, "Badge tapped, b.on=${b.on}")
                    if (b.on) off(b) else { rm(b); list.remove(b) }
                }
                return true
            }
        }
        dv.isClickable = true; dv.visibility = View.GONE; b.dv = dv
        try { wm.addView(dv, dlp) } catch (_: Exception) {}
    }

    // ── Dokunuş işleyici ──────────────────────────────────────────────────
    private fun touch(b: Btn, e: MotionEvent) {
        Log.v(TAG, "touch #${b.id} action=${e.actionMasked} on=${b.on} mv=${b.mv} rawX=${e.rawX.toInt()} rawY=${e.rawY.toInt()}")
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                b.rx = e.rawX; b.ry = e.rawY; b.mv = false
                if (!b.on) {
                    hideD(b)
                    // 100ms hareketsiz basılı tut → başlat
                    b.holdR?.let { h.removeCallbacks(it) }
                    b.holdR = Runnable {
                        b.holdR = null
                        if (!b.mv) {
                            Log.d(TAG, "Hold timer fired, starting tapping")
                            on(b)
                        }
                    }
                    h.postDelayed(b.holdR!!, 100L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - b.rx; val dy = e.rawY - b.ry
                if (!b.mv && sqrt(dx * dx + dy * dy) > TH) {
                    b.mv = true
                    b.holdR?.let { h.removeCallbacks(it) }; b.holdR = null
                    Log.d(TAG, "Drag started, hold cancelled")
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
                Log.d(TAG, "ACTION_UP received, b.on=${b.on}")
                when {
                    b.on -> off(b)   // ★ parmak kalktı → durdur
                    b.mv -> showD(b) // sürükleme bitti → × göster
                }
            }
        }
    }

    // ── Tıklama aç ────────────────────────────────────────────────────────
    private fun on(b: Btn) {
        val svc = TapService.get()
        if (svc == null) {
            Log.e(TAG, "TapService is NULL — accessibility service not running!")
            h.post {
                Toast.makeText(applicationContext,
                    "❌ Erişilebilirlik servisi kapalı!\nAyarlar → Erişilebilirlik → GG Macro → Aç",
                    Toast.LENGTH_LONG).show()
            }
            b.v?.invalidate()
            return
        }
        if (b.on) return
        b.on = true
        Log.d(TAG, "on() → tapping at (${b.lp.x + BTN/2}, ${b.lp.y + BTN/2}), FLAG_NOT_TOUCHABLE set")

        // FLAG_NOT_TOUCHABLE: Enjekte gesture'lar buradan geçer alttaki uygulamaya gider.
        // Android touch ownership garantisi: ACTION_DOWN'dan sonra devam eden dokunuş
        // serisi aynı pencereye iletilmeye devam eder — el kaldırınca ACTION_UP gelir.
        b.lp.flags = BASE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { wm.updateViewLayout(b.v, b.lp) } catch (e: Exception) {
            Log.e(TAG, "updateViewLayout failed: ${e.message}")
        }
        b.v?.invalidate()
        // Yeşil ■ stop badge (güvenlik çıkışı)
        b.ds = true; b.dv?.visibility = View.VISIBLE; b.dv?.invalidate()

        svc.startTapping(b.lp.x.toFloat() + BTN / 2f, b.lp.y.toFloat() + BTN / 2f)

        // 60 saniye güvenlik süresi (ACTION_UP gelmezse otomatik durdur)
        b.safetyR?.let { h.removeCallbacks(it) }
        b.safetyR = Runnable {
            Log.w(TAG, "Safety timeout — auto-stopping after 60s")
            off(b)
        }
        h.postDelayed(b.safetyR!!, 60_000L)
    }

    // ── Tıklama kapat ─────────────────────────────────────────────────────
    private fun off(b: Btn) {
        if (!b.on) return
        b.on = false
        b.safetyR?.let { h.removeCallbacks(it) }; b.safetyR = null
        Log.d(TAG, "off() → stopping tapping, restoring touchability")
        // Butonu tekrar dokunulabilir yap
        b.lp.flags = BASE
        try { wm.updateViewLayout(b.v, b.lp) } catch (_: Exception) {}
        TapService.get()?.stopTapping()
        b.v?.invalidate()
        b.ds = false; b.dv?.visibility = View.GONE
    }

    private fun showD(b: Btn) {
        if (b.on) return
        b.ds = true; b.dv?.visibility = View.VISIBLE; b.dv?.invalidate()
        h.postDelayed({ if (b.ds && !b.on) hideD(b) }, 3000L)
    }
    private fun hideD(b: Btn) { if (b.on) return; b.ds = false; b.dv?.visibility = View.GONE }
    private fun rm(b: Btn) { off(b); rmv(b.v); b.v = null; rmv(b.dv); b.dv = null }

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
            .setContentText("Sürükle → taşı | Bas&Tut → başlat | El çek → durur | Yeşil ■ = acil dur")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
