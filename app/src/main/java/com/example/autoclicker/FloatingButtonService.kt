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

        // MainActivity'den set edilir
        var targetX = 540f
        var targetY = 960f
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isClicking = false

    // 600ms sonra auto-click baslar
    private val startClickRunnable = Runnable {
        isClicking = true
        setButtonColor(Color.parseColor("#F44336")) // kirmizi = aktif
        AutoClickerService.instance?.start(targetX, targetY)
        Log.d(TAG, "Auto-click basladi: $targetX, $targetY")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
        Log.d(TAG, "Servis basladi")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(startClickRunnable)
        AutoClickerService.instance?.stop()
        if (::floatView.isInitialized) {
            runCatching { windowManager.removeView(floatView) }
        }
        Log.d(TAG, "Servis durduruldu")
    }

    // ─── Floating button olustur ───────────────────────────────────────────

    private fun createFloatingButton() {
        floatView = View(this).apply {
            background = makeCircle(Color.parseColor("#6200EE"))
        }

        val sizePx = dp(64)

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        var startParamX = 0
        var startParamY = 0
        var startRawX = 0f
        var startRawY = 0f
        var dragged = false

        floatView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startParamX = params.x
                    startParamY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    dragged = false
                    // 600ms sonra tiklama baslasin
                    mainHandler.postDelayed(startClickRunnable, 600)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    // 8px'den fazla hareket = surukleme, long-press iptal
                    if (dx * dx + dy * dy > 64) {
                        dragged = true
                        mainHandler.removeCallbacks(startClickRunnable)
                        if (isClicking) {
                            isClicking = false
                            AutoClickerService.instance?.stop()
                            setButtonColor(Color.parseColor("#6200EE"))
                        }
                    }
                    params.x = startParamX + dx
                    params.y = startParamY + dy
                    windowManager.updateViewLayout(floatView, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(startClickRunnable)
                    if (isClicking) {
                        isClicking = false
                        AutoClickerService.instance?.stop()
                        setButtonColor(Color.parseColor("#6200EE"))
                        Log.d(TAG, "Auto-click durduruldu")
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatView, params)
    }

    // ─── Yardimcilar ──────────────────────────────────────────────────────

    private fun setButtonColor(color: Int) {
        mainHandler.post { floatView.background = makeCircle(color) }
    }

    private fun makeCircle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AutoClicker", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingButtonService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoClicker Aktif")
            .setContentText("Butona uzun bas → tiklama baslar, birakinca durur")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_pause, "Kapat", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
