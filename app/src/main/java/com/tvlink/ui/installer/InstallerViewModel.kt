package com.tvlink.ui.installer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvlink.data.adb.AdbRepository
import com.tvlink.data.adb.ConnectionManager
import com.tvlink.data.adb.model.AdbDevice
import com.tvlink.data.adb.model.InstallProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class InstallRecord(
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean
)

data class InstallerUiState(
    val selectedFileName: String? = null,
    val selectedFileSize: Long = 0L,
    val installProgress: InstallProgress? = null,
    val installHistory: List<InstallRecord> = emptyList(),
    val isConnected: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InstallerViewModel @Inject constructor(
    private val adbRepository: AdbRepository,
    private val connectionManager: ConnectionManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallerUiState())
    val uiState: StateFlow<InstallerUiState> = _uiState.asStateFlow()

    private var selectedUri: Uri? = null

    init {
        viewModelScope.launch {
            connectionManager.connectedDevice.collect { device ->
                _uiState.update { it.copy(isConnected = device != null) }
            }
        }
    }

    private fun getDevice(): AdbDevice? = connectionManager.connectedDevice.value

    fun selectFile(uri: Uri) {
        selectedUri = uri

        // Resolve file name and size from content provider
        val cursor = appContext.contentResolver.query(uri, null, null, null, null)
        var name = "Unknown.apk"
        var size = 0L
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = it.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = it.getLong(sizeIndex)
            }
        }

        _uiState.update {
            it.copy(
                selectedFileName = name,
                selectedFileSize = size,
                installProgress = null,
                error = null
            )
        }
    }

    fun clearSelection() {
        selectedUri = null
        _uiState.update {
            it.copy(
                selectedFileName = null,
                selectedFileSize = 0L,
                installProgress = null,
                error = null
            )
        }
    }

    fun install() {
        val uri = selectedUri ?: return
        val device = getDevice() ?: run {
            _uiState.update { it.copy(error = "Not connected to any device") }
            return
        }
        val fileName = _uiState.value.selectedFileName ?: "Unknown.apk"

        viewModelScope.launch {
            _uiState.update { it.copy(installProgress = InstallProgress.Installing(0), error = null) }
            try {
                // Copy content URI to cache dir on IO thread
                val cacheFile = withContext(Dispatchers.IO) {
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot read selected file — try selecting it again.")
                    val dest = File(appContext.cacheDir, "install_temp.apk")
                    input.use { it.copyTo(dest.outputStream()) }
                    dest
                }

                adbRepository.installApk(device, cacheFile).collect { progress ->
                    _uiState.update { it.copy(installProgress = progress) }

                    when (progress) {
                        is InstallProgress.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    installHistory = listOf(InstallRecord(fileName = fileName, success = true)) + state.installHistory
                                )
                            }
                        }
                        is InstallProgress.Failed -> {
                            _uiState.update { state ->
                                state.copy(
                                    installHistory = listOf(InstallRecord(fileName = fileName, success = false)) + state.installHistory,
                                    error = progress.reason
                                )
                            }
                        }
                        else -> { }
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Install failed"
                _uiState.update {
                    it.copy(
                        installProgress = InstallProgress.Failed(msg),
                        error = msg,
                        installHistory = listOf(InstallRecord(fileName = fileName, success = false)) + it.installHistory
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
