package com.ggmacro.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggmacro.app.data.model.Macro
import com.ggmacro.app.data.repository.MacroRepository
import com.ggmacro.app.service.FloatingOverlayService
import com.ggmacro.app.service.MacroAccessibilityService
import com.ggmacro.app.utils.MacroImportExport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MacroRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val macros: StateFlow<List<Macro>> = repository.getAllMacros()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var activeMacroId: Long = -1L

    fun deleteMacro(macro: Macro) {
        viewModelScope.launch {
            repository.deleteMacro(macro.id)
            _snackbarMessage.emit("\"${macro.name}\" deleted")
        }
    }

    fun duplicateMacro(macro: Macro) {
        viewModelScope.launch {
            repository.duplicateMacro(macro)
            _snackbarMessage.emit("\"${macro.name}\" duplicated")
        }
    }

    fun playMacro(macro: Macro) {
        val service = MacroAccessibilityService.getInstance()
        if (service == null) {
            viewModelScope.launch {
                _snackbarMessage.emit("Enable Accessibility Service first")
            }
            return
        }
        val actions = repository.getMacroActions(macro)
        if (actions.isEmpty()) {
            viewModelScope.launch { _snackbarMessage.emit("No recorded actions in this macro") }
            return
        }
        _isPlaying.value = true
        activeMacroId = macro.id
        service.playMacro(actions, macro.loopCount, macro.playbackSpeed) {
            _isPlaying.value = false
            activeMacroId = -1L
        }
    }

    fun stopPlayback() {
        MacroAccessibilityService.getInstance()?.stopPlayback()
        _isPlaying.value = false
        activeMacroId = -1L
    }

    fun openOverlay(macro: Macro) {
        FloatingOverlayService.start(context, macro)
    }

    fun exportMacros(uri: Uri) {
        viewModelScope.launch {
            val allMacros = macros.value
            val result = MacroImportExport.exportMacros(context, allMacros, uri)
            if (result.isSuccess) {
                _snackbarMessage.emit("Exported ${allMacros.size} macro(s)")
            } else {
                _snackbarMessage.emit("Export failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun importMacros(uri: Uri) {
        viewModelScope.launch {
            val result = MacroImportExport.importMacros(context, uri)
            if (result.isSuccess) {
                result.getOrNull()?.forEach { macro ->
                    repository.saveMacro(macro)
                }
                _snackbarMessage.emit("Imported ${result.getOrNull()?.size ?: 0} macro(s)")
            } else {
                _snackbarMessage.emit("Import failed: invalid file")
            }
        }
    }
}
