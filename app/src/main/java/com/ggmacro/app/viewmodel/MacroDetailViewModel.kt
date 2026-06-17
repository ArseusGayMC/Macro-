package com.ggmacro.app.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggmacro.app.data.model.Macro
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

    private val _macroName = MutableStateFlow("")
    val macroName: StateFlow<String> = _macroName.asStateFlow()

    private val _tapDuration = MutableStateFlow(50L)
    val tapDuration: StateFlow<Long> = _tapDuration.asStateFlow()

    private val _tapDelay = MutableStateFlow(50L)
    val tapDelay: StateFlow<Long> = _tapDelay.asStateFlow()

    private val _holdThreshold = MutableStateFlow(400L)
    val holdThreshold: StateFlow<Long> = _holdThreshold.asStateFlow()

    private val _isTriggerActive = MutableStateFlow(FloatingTriggerButtonService.isRunning)
    val isTriggerActive: StateFlow<Boolean> = _isTriggerActive.asStateFlow()

    private val _accessibilityRunning = MutableStateFlow(MacroAccessibilityService.isRunning.value)
    val accessibilityRunning: StateFlow<Boolean> = _accessibilityRunning.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage

    init {
        // Track accessibility service state
        viewModelScope.launch {
            MacroAccessibilityService.isRunning.collect { running ->
                _accessibilityRunning.value = running
            }
        }

        if (macroId > 0) {
            viewModelScope.launch {
                repository.getMacroById(macroId)?.let { m ->
                    _macro.value = m
                    _macroName.value = m.name
                    _tapDuration.value = m.tapDuration
                    _tapDelay.value = m.actionDelay.coerceAtLeast(16L)
                }
            }
        }
    }

    fun setName(name: String) { _macroName.value = name }
    fun setTapDuration(ms: Long) { _tapDuration.value = ms.coerceAtLeast(1L) }
    fun setTapDelay(ms: Long) { _tapDelay.value = ms.coerceAtLeast(16L) }
    fun setHoldThreshold(ms: Long) { _holdThreshold.value = ms.coerceIn(50L, 2000L) }

    fun saveMacro() {
        viewModelScope.launch {
            val name = _macroName.value.trim().ifBlank { "Unnamed Macro" }
            val existing = _macro.value
            if (existing != null) {
                repository.updateMacro(
                    existing.copy(
                        name = name,
                        tapDuration = _tapDuration.value,
                        actionDelay = _tapDelay.value,
                        // holdThreshold can be saved in actionDelay or a new field if added to DB, 
                        // but for now we keep it consistent with the existing model
                    )
                )
            } else {
                val id = repository.saveMacro(
                    Macro(
                        name = name,
                        tapDuration = _tapDuration.value,
                        actionDelay = _tapDelay.value
                    )
                )
                _macro.value = repository.getMacroById(id)
            }
            _snackbarMessage.emit("Kaydedildi ✓")
        }
    }

    fun startTriggerButton() {
        if (!MacroAccessibilityService.isRunning.value) {
            viewModelScope.launch {
                _snackbarMessage.emit("Önce Erişilebilirlik Servisi'ni aç!")
            }
            return
        }
        FloatingTriggerButtonService.start(
            ctx         = context,
            macroName   = _macroName.value.trim().ifBlank { "Macro" },
            tapDuration = _tapDuration.value,
            tapDelay    = _tapDelay.value
        )
        _isTriggerActive.value = true
        viewModelScope.launch {
            _snackbarMessage.emit("Ekranda 2 overlay belirdi — mavi=buton, kırmızı=hedef")
        }
    }

    fun stopTriggerButton() {
        FloatingTriggerButtonService.stop(context)
        _isTriggerActive.value = false
        viewModelScope.launch { _snackbarMessage.emit("Overlay kaldırıldı") }
    }

    override fun onCleared() {
        super.onCleared()
        // Stop recording if VM is cleared to avoid leaks or stale states
        if (recordingManager.isRecording.value) {
            recordingManager.stopRecording()
        }
    }
}
