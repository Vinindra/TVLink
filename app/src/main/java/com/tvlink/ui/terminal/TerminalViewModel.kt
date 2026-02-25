package com.tvlink.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvlink.data.adb.AdbRepository
import com.tvlink.data.adb.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalEntry(
    val text: String,
    val type: EntryType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class EntryType {
    SYSTEM, COMMAND, OUTPUT, ERROR
}

data class TerminalUiState(
    val history: List<TerminalEntry> = listOf(
        TerminalEntry("TVLink Terminal initialized.", EntryType.SYSTEM),
        TerminalEntry("Type a command to execute on the remote device.", EntryType.SYSTEM)
    ),
    val currentInput: String = "",
    val isRunning: Boolean = false,
    val isConnected: Boolean = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val adbRepository: AdbRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TerminalUiState(isConnected = connectionManager.connectedDevice.value != null)
    )
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.connectedDevice.collect { device ->
                _uiState.update { it.copy(isConnected = device != null) }
            }
        }
    }

    fun clearHistory() {
        _uiState.update {
            it.copy(history = listOf(TerminalEntry("Terminal cleared.", EntryType.SYSTEM)))
        }
    }

    fun updateInput(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun executeCommand() {
        val command = _uiState.value.currentInput.trim()
        if (command.isEmpty()) return

        val device = connectionManager.connectedDevice.value ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    history = it.history + TerminalEntry("$ $command", EntryType.COMMAND),
                    currentInput = "",
                    isRunning = true
                )
            }

            adbRepository.shell(device, command).onSuccess { result ->
                _uiState.update {
                    it.copy(
                        history = it.history + TerminalEntry(
                            result.output.ifEmpty { "(no output)" },
                            if (result.isError) EntryType.ERROR else EntryType.OUTPUT
                        ),
                        isRunning = false
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        history = it.history + TerminalEntry(
                            "Error: ${e.message}",
                            EntryType.ERROR
                        ),
                        isRunning = false
                    )
                }
            }
        }
    }
}
