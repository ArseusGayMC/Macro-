package com.example.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "FloatingButtonService"
        private const val CHANNEL_ID = "autoclicker_channel"
        private const val NOTIF_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isClicking = false
    private var buttonSizePx = 0

    // WindowManager params — pozisyon buradan okunur
    private lateinit var layoutParams: WindowManager.LayoutParams

    // 600ms bekleme sonrasi auto-click baslar
    private val startClickRunnable = Runnable {
        isClicking = true
        setColor(Color.parseColor("#F44336")) // kirmizi = aktif

        // Butonun MERKEZ koordinati = tiklama hedefi
        val cx = layoutParams.x + buttonSizePx / 2f
        val cy = layoutParams.y + buttonSizePx / 2f
        AutoClickerService.instance?.start(cx, cy)
        Log.d(TAG, "Click basladi: cx=$cx cy=$cy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buttonSizePx = dp(64)
        createFloatingButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(startClickRunnable)
        AutoClickerService.instance?.stop()
        if (::floatView.isInitialized) runCatching { windowManager.removeView(floatView) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }

    // ─── Floating buton ───────────────────────────────────────────────────

    private fun createFloatingButton() {
        floatView = View(this).apply {
            background = makeCircle(Color.parseColor("#6200EE"))
        }

        layoutParams = WindowManager.LayoutParams(
            buttonSizePx, buttonSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 400
        }

        var startX = 0;  var startY = 0
        var startRawX = 0f; var startRawY = 0f

        floatView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    // 600ms sonra tiklama baslar
                    mainHandler.postDelayed(startClickRunnable, 600)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    // 10px den fazla hareket = surukleme, long-press iptal
                    if (dx * dx + dy * dy > 100) {
                        mainHandler.removeCallbacks(startClickRunnable)
                        if (isClicking) {
                            isClicking = false
                            AutoClickerService.instance?.stop()
                            setColor(Color.parseColor("#6200EE"))
                        }
                    }
                    layoutParams.x = startX + dx
                    layoutParams.y = startY + dy
                    windowManager.updateViewLayout(floatView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(startClickRunnable)
                    if (isClicking) {
                        isClicking = false
                        AutoClickerService.instance?.stop()
                        setColor(Color.parseColor("#6200EE"))
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatView, layoutParams)
    }

    private fun setColor(color: Int) {
        mainHandler.post { floatView.background = makeCircle(color) }
    }

    private fun makeCircle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AutoClicker", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingButtonService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoClicker Aktif")
            .setContentText("Butonu tiklamak istedigin yere gotur, uzun bas")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_pause, "Kapat", stop)
            .setOngoing(true)
            .build()
    }
}
