package com.ggmacro.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ggmacro.app.MainActivity

/**
 * Thin foreground service — keeps the process alive and shows a persistent
 * notification. All overlay drawing and gesture dispatch is delegated to
 * MacroAccessibilityService which uses TYPE_ACCESSIBILITY_OVERLAY (works in
 * Xiaomi Game Mode / HyperOS, unlike TYPE_APPLICATION_OVERLAY).
 */
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

    private var macroName     = "Macro"
    private var tapDuration   = 50L
    private var tapDelay      = 50L
    private var holdThreshold = 350L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        macroName     = intent?.getStringExtra(EXTRA_MACRO_NAME)?.ifBlank { "Macro" } ?: "Macro"
        tapDuration   = (intent?.getLongExtra(EXTRA_TAP_DURATION,   50L) ?: 50L).coerceAtLeast(1L)
        tapDelay      = (intent?.getLongExtra(EXTRA_TAP_DELAY,      50L) ?: 50L).coerceAtLeast(16L)
        holdThreshold = (intent?.getLongExtra(EXTRA_HOLD_THRESHOLD, 350L) ?: 350L).coerceIn(50L, 2000L)

        // Show the overlay via AccessibilityService (TYPE_ACCESSIBILITY_OVERLAY)
        MacroAccessibilityService.getInstance()
            ?.showTriggerOverlay(macroName, tapDuration, tapDelay, holdThreshold)

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        MacroAccessibilityService.getInstance()?.hideTriggerOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "GG Macro Trigger", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
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
