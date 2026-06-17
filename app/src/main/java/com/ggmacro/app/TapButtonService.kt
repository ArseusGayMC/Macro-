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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * v7 — Tetikleyici + Hedef mimarisi, FLAG_NOT_TOUCHABLE YOK
 *
 * Temel kural: Buton penceresi ASLA FLAG_NOT_TOUCHABLE almaz.
 *   → Overlay ekranı engellemez.
 *   → ACTION_DOWN/UP/CANCEL her zaman güvenle gelir.
 *
 * Gesture'lar butonun ALTINA değil, ayrı hedef noktasına enjekte edilir.
 * Bu sayede gesture ile buton dokunuşu arasında çakışma olmaz.
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

    private val dp by lazy { resources.displayMetrics.density }

    // Pencere bayrakları — FLAG_NOT_TOUCHABLE ASLA eklenmez
    private val WIN_FLAGS get() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    // ── Boyutlar ───────────────────────────────────────────────────────────
    private val SZ_BTN  get() = (62 * dp).toInt()   // tetikleyici buton
    private val SZ_TGT  get() = (48 * dp).toInt()   // hedef nişangahı
    private val SZ_ADD  get() = (50 * dp).toInt()   // "+" ekleme butonu
    private val DRAG_TH get() = 8f * dp              // sürükleme eşiği

    // ── "+" ekleme butonu ──────────────────────────────────────────────────
    private var addView: View? = null
    private var addLp: WindowManager.LayoutParams? = null
    private var addRx = 0f; private var addRy = 0f; private var addMoved = false

    // ── Makro birimi ───────────────────────────────────────────────────────
    private inner class MacroUnit(val id: Int) {
        // Tetikleyici buton
        var btnView: View? = null
        var btnLp = makeLp(SZ_BTN, SZ_BTN)

        // Hedef nişangahı
        var tgtView: View? = null
        var tgtLp   = makeLp(SZ_TGT, SZ_TGT)

        // Durum
        @Volatile var active = false
        var btnRx = 0f; var btnRy = 0f; var btnMoved = false
        var tgtRx = 0f; var tgtRy = 0f; var tgtMoved = false

        // Hedef koordinatı (merkez nokta, ekran koordinatı)
        val tapX get() = tgtLp.x.toFloat() + SZ_TGT / 2f
        val tapY get() = tgtLp.y.toFloat() + SZ_TGT / 2f
    }

    private val units = mutableListOf<MacroUnit>()
    private var nextId = 0

    // ── Yaşam döngüsü ─────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        mkChannel()
        startForeground(NOTIF_ID, buildNotif())
        mkAddButton()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        TapService.get()?.stopTapping()
        units.forEach { destroyUnit(it) }
        units.clear()
        removeView(addView)
        addView = null
        super.onDestroy()
    }

    // ── "+" ekleme butonu ──────────────────────────────────────────────────
    private fun mkAddButton() {
        val sz = SZ_ADD
        val dm = resources.displayMetrics
        val lp = makeLp(sz, sz).apply { x = dm.widthPixels - sz - 12; y = 200 }
        addLp = lp

        val bgPaint  = makeFillPaint(Color.argb(235, 10, 130, 255))
        val rimPaint = makeStrokePaint(Color.argb(160, 255, 255, 255), 2f * dp)
        val plusPaint = makeTextPaint(21f * dp, Color.WHITE)
        val lblPaint  = makeTextPaint(6f * dp, Color.argb(190, 200, 235, 255))

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 2f * dp
                c.drawCircle(cx, cy, r, bgPaint)
                c.drawCircle(cx, cy, r, rimPaint)
                c.drawText("+", cx, cy + plusPaint.textSize * 0.35f, plusPaint)
                c.drawText("YENİ", cx, cy + r - 3f * dp, lblPaint)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN  -> { addRx = e.rawX; addRy = e.rawY; addMoved = false }
                    MotionEvent.ACTION_MOVE  -> {
                        val dx = e.rawX - addRx; val dy = e.rawY - addRy
                        if (!addMoved && dist(dx, dy) > DRAG_TH) addMoved = true
                        if (addMoved) {
                            lp.x = (lp.x + dx.toInt()).coerceAtLeast(0)
                            lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                            addRx = e.rawX; addRy = e.rawY
                            try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> { if (!addMoved) spawnUnit() }
                }
                return true
            }
        }
        v.isClickable = true
        addView = v
        try { wm.addView(v, lp) } catch (_: Exception) {}
    }

    // ── Yeni makro birimi ─────────────────────────────────────────────────
    private fun spawnUnit() {
        val dm = resources.displayMetrics
        val unit = MacroUnit(nextId++)

        // Tetikleyici butonu ekranın ortasına yerleştir
        unit.btnLp.x = dm.widthPixels / 2 - SZ_BTN / 2
        unit.btnLp.y = dm.heightPixels / 2 - SZ_BTN / 2 - (70 * dp).toInt()

        // Hedefi biraz aşağıya
        unit.tgtLp.x = dm.widthPixels / 2 - SZ_TGT / 2
        unit.tgtLp.y = dm.heightPixels / 2 - SZ_TGT / 2 + (70 * dp).toInt()

        units.add(unit)
        mkBtnView(unit)
        mkTgtView(unit)
    }

    // ── Tetikleyici buton görünümü ─────────────────────────────────────────
    private fun mkBtnView(u: MacroUnit) {
        val bgP  = makeFillPaint(0)
        val rimP = makeStrokePaint(0, 3f * dp)
        val mainT = makeTextPaint(9.5f * dp, Color.WHITE).also { it.isFakeBoldText = true }
        val subT  = makeTextPaint(7f   * dp, Color.argb(210, 180, 235, 255))
        val numT  = makeTextPaint(6.5f * dp, Color.argb(130, 255, 255, 255))
        val delT  = makeTextPaint(8.5f * dp, Color.argb(200, 255, 130, 130))

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - 3f * dp
                val svcOk = TapService.get() != null

                bgP.color  = when { !svcOk -> Color.argb(200, 100, 0, 0); u.active -> Color.argb(230, 200, 15, 15); else -> Color.argb(210, 0, 75, 195) }
                rimP.color = when { !svcOk -> Color.rgb(255, 80, 80);      u.active -> Color.rgb(255, 30, 30);       else -> Color.rgb(0, 190, 255) }

                c.drawCircle(cx, cy, r, bgP)
                c.drawCircle(cx, cy, r, rimP)

                when {
                    !svcOk -> {
                        c.drawText("ERİŞİM", cx, cy - 1f * dp, mainT)
                        c.drawText("VER →", cx, cy + 13f * dp, subT)
                    }
                    u.active -> {
                        c.drawText("◉ TIKLIYOR", cx, cy - 1f * dp, mainT)
                        c.drawText("el çek → dur", cx, cy + 13f * dp, subT)
                    }
                    else -> {
                        c.drawText("BAS & TUT", cx, cy - 1f * dp, mainT)
                        c.drawText("başlatmak için", cx, cy + 13f * dp, subT)
                    }
                }
                c.drawText("#${u.id + 1}", cx, cy - r + 12f * dp, numT)
                // Sil butonu (sağ üst küçük ×)
                c.drawText("×", cx + r - 5f * dp, cy - r + 14f * dp, delT)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                // × bölgesine dokunuldu mu? (sağ üst köşe, ~20dp alan)
                val r = SZ_BTN / 2f
                val delZone = 20f * dp
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    val dx = abs(e.x - (r + r - 5f * dp))
                    val dy = abs(e.y - (r - r + 14f * dp))
                    if (dx < delZone && dy < delZone && !u.active) {
                        destroyUnit(u); units.remove(u); return true
                    }
                }
                touchBtn(u, e)
                return true
            }
        }
        v.isClickable = true
        u.btnView = v
        try { wm.addView(v, u.btnLp) } catch (_: Exception) {}
    }

    // ── Hedef nişangahı görünümü ───────────────────────────────────────────
    private fun mkTgtView(u: MacroUnit) {
        val ringI  = makeStrokePaint(Color.argb(220, 255, 200,  0), 2f * dp)
        val ringA  = makeStrokePaint(Color.argb(220, 255,  40,  0), 2.5f * dp)
        val dotP   = makeFillPaint(Color.argb(230, 255, 60, 0))
        val lblP   = makeTextPaint(6f * dp, Color.argb(200, 255, 245, 180))

        val v = object : View(this) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                val r  = cx - 4f * dp
                val rp = if (u.active) ringA else ringI
                // Dış halka
                c.drawCircle(cx, cy, r, rp)
                // Çarpı çizgiler
                val arm = r * 0.38f
                c.drawLine(cx - r, cy, cx - arm, cy, rp)
                c.drawLine(cx + arm, cy, cx + r, cy, rp)
                c.drawLine(cx, cy - r, cx, cy - arm, rp)
                c.drawLine(cx, cy + arm, cx, cy + r, rp)
                // Merkez nokta
                c.drawCircle(cx, cy, 3f * dp, dotP)
                // Etiket
                c.drawText("HEDEF #${u.id + 1}", cx, cy + r + 9f * dp, lblP)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (!u.active) touchTarget(u, e)
                return true
            }
        }
        v.isClickable = true
        u.tgtView = v
        try { wm.addView(v, u.tgtLp) } catch (_: Exception) {}
    }

    // ── Tetikleyici dokunuş ───────────────────────────────────────────────
    private fun touchBtn(u: MacroUnit, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                u.btnRx = e.rawX; u.btnRy = e.rawY; u.btnMoved = false
                if (!u.active) startMacro(u)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - u.btnRx; val dy = e.rawY - u.btnRy
                if (!u.btnMoved && dist(dx, dy) > DRAG_TH) {
                    u.btnMoved = true
                    // Sürükleme başladı → makroyu durdur
                    if (u.active) stopMacro(u)
                }
                if (u.btnMoved && !u.active) {
                    u.btnLp.x = (u.btnLp.x + dx.toInt()).coerceAtLeast(0)
                    u.btnLp.y = (u.btnLp.y + dy.toInt()).coerceAtLeast(0)
                    u.btnRx = e.rawX; u.btnRy = e.rawY
                    try { wm.updateViewLayout(u.btnView, u.btnLp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Parmak kalktı → makroyu durdur
                if (u.active) stopMacro(u)
            }
        }
    }

    // ── Hedef sürükleme ───────────────────────────────────────────────────
    private fun touchTarget(u: MacroUnit, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { u.tgtRx = e.rawX; u.tgtRy = e.rawY; u.tgtMoved = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - u.tgtRx; val dy = e.rawY - u.tgtRy
                if (!u.tgtMoved && dist(dx, dy) > DRAG_TH) u.tgtMoved = true
                if (u.tgtMoved) {
                    u.tgtLp.x = (u.tgtLp.x + dx.toInt()).coerceAtLeast(0)
                    u.tgtLp.y = (u.tgtLp.y + dy.toInt()).coerceAtLeast(0)
                    u.tgtRx = e.rawX; u.tgtRy = e.rawY
                    try { wm.updateViewLayout(u.tgtView, u.tgtLp) } catch (_: Exception) {}
                }
            }
            MotionEvent.ACTION_UP -> {}
        }
    }

    // ── Makro başlat ──────────────────────────────────────────────────────
    private fun startMacro(u: MacroUnit) {
        val svc = TapService.get()
        if (svc == null) {
            h.post {
                Toast.makeText(applicationContext,
                    "Erişilebilirlik servisi kapalı!\nAyarlar → Erişilebilirlik → GG Macro → Aç",
                    Toast.LENGTH_LONG).show()
            }
            u.btnView?.invalidate()
            return
        }
        if (u.active) return
        u.active = true
        u.btnView?.invalidate()
        u.tgtView?.invalidate()
        // Gesture hedef nişangahının merkezine gönderilir — butonun konumuna DEĞİL
        svc.startTapping(u.tapX, u.tapY)
    }

    // ── Makro durdur ──────────────────────────────────────────────────────
    private fun stopMacro(u: MacroUnit) {
        if (!u.active) return
        u.active = false
        TapService.get()?.stopTapping()
        u.btnView?.invalidate()
        u.tgtView?.invalidate()
    }

    private fun destroyUnit(u: MacroUnit) {
        stopMacro(u)
        removeView(u.btnView); u.btnView = null
        removeView(u.tgtView); u.tgtView = null
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────
    private fun makeLp(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WIN_FLAGS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun dist(dx: Float, dy: Float) = sqrt(dx * dx + dy * dy)

    private fun makeFillPaint(color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color; it.style = Paint.Style.FILL }

    private fun makeStrokePaint(color: Int, sw: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color; it.style = Paint.Style.STROKE; it.strokeWidth = sw }

    private fun makeTextPaint(size: Float, color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).also { it.textAlign = Paint.Align.CENTER; it.textSize = size; it.color = color }

    private fun removeView(v: View?) =
        v?.let { try { wm.removeView(it) } catch (_: Exception) {} }

    private fun mkChannel() {
        val ch = NotificationChannel(CHANNEL, "GG Macro", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("GG Macro Aktif")
            .setContentText("HEDEF'i oyunun tuşuna sürükle → BAS&TUT başlatır, el çek durdurur")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
