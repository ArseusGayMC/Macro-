package com.ggmacro.app.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggmacro.app.data.model.ActionType
import com.ggmacro.app.data.model.Macro
import com.ggmacro.app.data.model.MacroAction
import com.ggmacro.app.data.repository.MacroRepository
import com.ggmacro.app.service.FloatingTriggerButtonService
import com.ggmacro.app.service.MacroAccessibilityService
import com.ggmacro.app.service.TouchRecordingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MacroDetailViewModel @Inject constructor(
    private val repository: MacroRepository,
    private val recordingManager: TouchRecordingManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val macroId: Long = savedStateHandle.get<Long>("macroId") ?: -1L

    private val _macro = MutableStateFlow<Macro?>(null)
    val macro: StateFlow<Macro?> = _macro.asStateFlow()

    private val _actions = MutableStateFlow<List<MacroAction>>(emptyList())
    val actions: StateFlow<List<MacroAction>> = _actions.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isTriggerActive = MutableStateFlow(false)
    val isTriggerActive: StateFlow<Boolean> = _isTriggerActive.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage

    private val _macroName = MutableStateFlow("")
    val macroName: StateFlow<String> = _macroName.asStateFlow()

    private val _loopCount = MutableStateFlow(1)
    val loopCount: StateFlow<Int> = _loopCount.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _tapDuration = MutableStateFlow(50L)
    val tapDuration: StateFlow<Long> = _tapDuration.asStateFlow()

    private val _actionDelay = MutableStateFlow(0L)
    val actionDelay: StateFlow<Long> = _actionDelay.asStateFlow()

    init {
        if (macroId > 0) {
            viewModelScope.launch {
                val existing = repository.getMacroById(macroId)
                existing?.let { m ->
                    _macro.value = m
                    _macroName.value = m.name
                    _loopCount.value = m.loopCount
                    _playbackSpeed.value = m.playbackSpeed
                    _tapDuration.value = m.tapDuration
                    _actionDelay.value = m.actionDelay
                    _actions.value = repository.getMacroActions(m)
                }
            }
        }
    }

    fun setName(name: String) { _macroName.value = name }
    fun setLoopCount(count: Int) { _loopCount.value = count }
    fun setPlaybackSpeed(speed: Float) { _playbackSpeed.value = speed }
    fun setTapDuration(ms: Long) { _tapDuration.value = ms }
    fun setActionDelay(ms: Long) { _actionDelay.value = ms }

    fun addManualAction(type: ActionType, x: Float, y: Float, endX: Float = 0f, endY: Float = 0f) {
        val action = MacroAction(
            type = type,
            x = x, y = y,
            endX = endX, endY = endY,
            duration = _tapDuration.value,
            delayBefore = _actionDelay.value
        )
        _actions.value = _actions.value + action
    }

    fun removeAction(action: MacroAction) {
        _actions.value = _actions.value.filter { it.id != action.id }
    }

    fun reorderActions(from: Int, to: Int) {
        val list = _actions.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _actions.value = list
        }
    }

    fun saveMacro() {
        viewModelScope.launch {
            val name = _macroName.value.trim().ifBlank { "Unnamed Macro" }
            val actionsJson = repository.serializeActions(_actions.value)
            val existing = _macro.value
            if (existing != null) {
                repository.updateMacro(
                    existing.copy(
                        name = name,
                        actionsJson = actionsJson,
                        loopCount = _loopCount.value,
                        playbackSpeed = _playbackSpeed.value,
                        tapDuration = _tapDuration.value,
                        actionDelay = _actionDelay.value
                    )
                )
            } else {
                val id = repository.saveMacro(
                    Macro(
                        name = name,
                        actionsJson = actionsJson,
                        loopCount = _loopCount.value,
                        playbackSpeed = _playbackSpeed.value,
                        tapDuration = _tapDuration.value,
                        actionDelay = _actionDelay.value
                    )
                )
                _macro.value = repository.getMacroById(id)
            }
            _snackbarMessage.emit("Macro saved")
        }
    }

    fun playMacro() {
        val service = MacroAccessibilityService.getInstance()
        if (service == null) {
            viewModelScope.launch { _snackbarMessage.emit("Enable Accessibility Service first") }
            return
        }
        val currentActions = _actions.value
        if (currentActions.isEmpty()) {
            viewModelScope.launch { _snackbarMessage.emit("Add actions before playing") }
            return
        }
        _isPlaying.value = true
        service.playMacro(currentActions, _loopCount.value, _playbackSpeed.value) {
            _isPlaying.value = false
        }
    }

    fun stopPlayback() {
        MacroAccessibilityService.getInstance()?.stopPlayback()
        _isPlaying.value = false
    }

    fun clearActions() {
        _actions.value = emptyList()
    }

    fun startTriggerButton() {
        val currentActions = _actions.value
        if (currentActions.isEmpty()) {
            viewModelScope.launch { _snackbarMessage.emit("Add actions first, then place the trigger button") }
            return
        }
        FloatingTriggerButtonService.start(
            context = context,
            actions = currentActions,
            macroName = _macroName.value.ifBlank { "Macro" },
            loopCount = _loopCount.value,
            speed = _playbackSpeed.value
        )
        _isTriggerActive.value = true
        viewModelScope.launch { _snackbarMessage.emit("Trigger button placed — hold it to run the macro") }
    }

    fun stopTriggerButton() {
        FloatingTriggerButtonService.stop(context)
        _isTriggerActive.value = false
        viewModelScope.launch { _snackbarMessage.emit("Trigger button removed") }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isTriggerActive.value) {
            FloatingTriggerButtonService.stop(context)
        }
    }
}
