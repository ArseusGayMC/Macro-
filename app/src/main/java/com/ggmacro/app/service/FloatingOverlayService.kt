package com.ggmacro.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ggmacro.app.MainActivity
import com.ggmacro.app.R
import com.ggmacro.app.data.model.Macro
import com.ggmacro.app.data.model.MacroAction
import com.ggmacro.app.data.repository.MacroRepository
import com.ggmacro.app.ui.overlay.FloatingOverlayContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ggmacro.app.ui.theme.GGMacroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@AndroidEntryPoint
class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject
    lateinit var repository: MacroRepository

    @Inject
    lateinit var recordingManager: TouchRecordingManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "gg_macro_overlay"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_MACRO_NAME = "macro_name"

        private val _overlayState = MutableStateFlow(OverlayState.IDLE)
        val overlayState: StateFlow<OverlayState> = _overlayState

        fun start(context: Context, macro: Macro) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                putExtra(EXTRA_MACRO_ID, macro.id)
                putExtra(EXTRA_MACRO_NAME, macro.name)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }

    enum class OverlayState { IDLE, RECORDING, PLAYING, PAUSED }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var touchRecordView: View? = null

    private var activeMacroId: Long = -1L
    private var activeMacroName: String = ""
    private var pendingActions: List<MacroAction> = emptyList()

    private val _currentState = MutableStateFlow(OverlayState.IDLE)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        activeMacroId = intent?.getLongExtra(EXTRA_MACRO_ID, -1L) ?: -1L
        activeMacroName = intent?.getStringExtra(EXTRA_MACRO_NAME) ?: ""
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel() // Cancel all ongoing coroutines
        removeOverlayView()
        removeTouchRecordView()
        _overlayState.value = OverlayState.IDLE
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingButton() {
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                GGMacroTheme {
                    FloatingOverlayContent(
                        macroName = activeMacroName,
                        state = _currentState,
                        onRecord = { toggleRecording() },
                        onPlay = { playMacro() },
                        onStop = { stopAll() },
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun showTouchRecordOverlay() {
        val recordView = object : View(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                recordingManager.onTouchEvent(event)
                return false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        )

        touchRecordView = recordView
        windowManager.addView(recordView, params)
    }

    private fun toggleRecording() {
        if (_currentState.value == OverlayState.RECORDING) {
            val recorded = recordingManager.stopRecording()
            pendingActions = recorded
            
            // Save to database if we have an active macro
            if (activeMacroId != -1L && recorded.isNotEmpty()) {
                serviceScope.launch {
                    repository.getMacroById(activeMacroId)?.let { macro ->
                        repository.updateMacro(macro.copy(actionsJson = repository.serializeActions(recorded)))
                    }
                }
            }
            
            removeTouchRecordView()
            _currentState.value = OverlayState.IDLE
            _overlayState.value = OverlayState.IDLE
        } else {
            recordingManager.startRecording()
            showTouchRecordOverlay()
            _currentState.value = OverlayState.RECORDING
            _overlayState.value = OverlayState.RECORDING
        }
    }

    private fun playMacro() {
        val service = MacroAccessibilityService.getInstance() ?: return
        
        serviceScope.launch {
            val actions = if (pendingActions.isNotEmpty()) {
                pendingActions
            } else if (activeMacroId != -1L) {
                repository.getMacroById(activeMacroId)?.let { repository.getMacroActions(it) } ?: emptyList()
            } else {
                emptyList()
            }

            if (actions.isEmpty()) return@launch

            _currentState.value = OverlayState.PLAYING
            _overlayState.value = OverlayState.PLAYING
            service.playMacro(actions, 1, 1.0f) {
                _currentState.value = OverlayState.IDLE
                _overlayState.value = OverlayState.IDLE
            }
        }
    }

    private fun stopAll() {
        MacroAccessibilityService.getInstance()?.stopPlayback()
        if (_currentState.value == OverlayState.RECORDING) {
            recordingManager.stopRecording()
            removeTouchRecordView()
        }
        _currentState.value = OverlayState.IDLE
        _overlayState.value = OverlayState.IDLE
    }

    private fun removeOverlayView() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun removeTouchRecordView() {
        touchRecordView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        touchRecordView = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
