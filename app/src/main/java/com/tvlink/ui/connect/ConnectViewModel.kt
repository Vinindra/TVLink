package com.tvlink.ui.connect

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvlink.data.adb.AdbRepository
import com.tvlink.data.adb.ConnectionManager
import com.tvlink.data.adb.DeviceDiscoveryManager
import com.tvlink.data.adb.model.AdbDevice
import com.tvlink.data.prefs.DevicePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.os.Environment
import java.io.File
import java.util.Date
import javax.inject.Inject

data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val path: String
)

data class MediaInfo(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val packageName: String = "",
    val artworkUrl: String? = null
)

data class DeviceInfo(
    val model: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val apiLevel: String = "",
    val buildNumber: String = "",
    val serialNumber: String = "",
    val securityPatch: String = "",
    val kernelVersion: String = "",
    val cpuArch: String = "",
    val bluetoothName: String = "",
    val resolution: String = "",
    val density: String = "",
    val totalRam: String = "",
    val availRam: String = "",
    val totalRamBytes: Long = 0,
    val availRamBytes: Long = 0,
    val totalStorage: String = "",
    val usedStorage: String = "",
    val totalStorageBytes: Long = 0,
    val usedStorageBytes: Long = 0,
    val ipAddress: String = "",
    val uptime: String = "",
    val macAddress: String = ""
)

data class ConnectUiState(
    val savedDevices: List<AdbDevice> = emptyList(),
    val discoveredDevices: List<AdbDevice> = emptyList(),
    val connectedDevice: AdbDevice? = null,
    val isConnecting: Boolean = false,
    val isScanning: Boolean = true,
    val isRecording: Boolean = false,
    val fileList: List<FileEntry> = emptyList(),
    val currentFilePath: String = "/sdcard",
    val isLoadingFiles: Boolean = false,
    val wifiSsid: String = "Unknown",
    val isOnWifi: Boolean = false,
    val deviceInfo: DeviceInfo? = null,
    val isLoadingDeviceInfo: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val isUploading: Boolean = false,
    val toast: String? = null,
    val error: String? = null
)

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val adbRepository: AdbRepository,
    private val devicePreferences: DevicePreferences,
    private val connectionManager: ConnectionManager,
    private val discoveryManager: DeviceDiscoveryManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val remoteTextSyncMutex = Mutex()

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var mediaPollingJob: Job? = null

    init {
        loadSavedDevices()
        observeConnectedDevices()
        observeDiscoveredDevices()
        discoveryManager.startDiscovery()
        refreshNetworkInfo()
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
        mediaPollingJob?.cancel()
    }

    private fun autoReconnectLastDevice() {
        viewModelScope.launch {
            val lastDevice = devicePreferences.lastConnectedDevice.firstOrNull() ?: return@launch
            // Only auto-reconnect if not already connected
            if (_uiState.value.connectedDevice != null) return@launch
            // Let networking settle, then retry a few times before giving up.
            val retryDelaysMs = listOf(500L, 1500L, 3000L)
            for ((index, waitMs) in retryDelaysMs.withIndex()) {
                delay(waitMs)
                try {
                    val result = adbRepository.connect(lastDevice.ip, lastDevice.port)
                    if (result.isSuccess) {
                        val device = result.getOrThrow()
                        devicePreferences.saveDevice(device)
                        devicePreferences.saveLastConnected(device)
                        _uiState.update { state ->
                            if (state.connectedDevice == device) state else state.copy(connectedDevice = device)
                        }
                        connectionManager.setConnected(device)
                        startMediaPolling()
                        return@launch
                    }
                } catch (_: Exception) {
                    // Ignore and retry.
                }

                // Stop retrying if user already connected manually.
                if (_uiState.value.connectedDevice != null) return@launch

                // Last attempt failed; keep lastConnected so next app launch can retry again.
                if (index == retryDelaysMs.lastIndex) {
                    _uiState.update { state ->
                        if (state.isConnecting) state.copy(isConnecting = false) else state
                    }
                }
            }
        }
    }

    private fun refreshNetworkInfo() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val ssid = if (onWifi) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val raw = wm?.connectionInfo?.ssid ?: ""
            raw.removePrefix("\"").removeSuffix("\"").takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "Wi-Fi"
        } else "Not on Wi-Fi"

        _uiState.update { state ->
            if (state.wifiSsid == ssid && state.isOnWifi == onWifi) state
            else state.copy(wifiSsid = ssid, isOnWifi = onWifi)
        }
    }

    private fun observeDiscoveredDevices() {
        viewModelScope.launch {
            discoveryManager.discoveredDevices.collect { devices ->
                _uiState.update { state ->
                    if (state.connectedDevice != null || state.discoveredDevices == devices) state
                    else state.copy(discoveredDevices = devices)
                }
            }
        }
    }

    private fun loadSavedDevices() {
        viewModelScope.launch {
            devicePreferences.savedDevices.collect { devices ->
                _uiState.update { state ->
                    if (state.savedDevices == devices) state else state.copy(savedDevices = devices)
                }
            }
        }
        viewModelScope.launch {
            delay(3000)
            _uiState.update { state ->
                if (!state.isScanning) state else state.copy(isScanning = false)
            }
        }
    }

    private fun observeConnectedDevices() {
        viewModelScope.launch {
            adbRepository.getConnectedDevices().collect { devices ->
                val connected = devices.firstOrNull { it.isConnected }
                val wasConnected = _uiState.value.connectedDevice != null
                _uiState.update { state ->
                    if (state.connectedDevice == connected) state else state.copy(connectedDevice = connected)
                }
                connectionManager.setConnected(connected)
                // Start/stop media polling based on connection state
                if (connected != null && !wasConnected) {
                    startMediaPolling()
                } else if (connected == null && wasConnected) {
                    stopMediaPolling()
                }
            }
        }
    }

    fun connect(ip: String, port: Int = 5555) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            val result = adbRepository.connect(ip, port)
            result.onSuccess { device ->
                devicePreferences.saveDevice(device)
                devicePreferences.saveLastConnected(device)
                _uiState.update { it.copy(isConnecting = false, connectedDevice = device) }
                connectionManager.setConnected(device)
                startMediaPolling()
            }.onFailure { e ->
                _uiState.update { it.copy(isConnecting = false, error = e.message) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            stopRecording()
            stopMediaPolling()
            adbRepository.disconnect(device)
            devicePreferences.clearLastConnected()
            _uiState.update { it.copy(connectedDevice = null, mediaInfo = null) }
            connectionManager.setConnected(null)
        }
    }

    fun deleteDevice(device: AdbDevice) {
        viewModelScope.launch {
            if (device.isConnected) {
                adbRepository.disconnect(device)
                connectionManager.setConnected(null)
            }
            devicePreferences.removeDevice(device)
        }
    }

    fun executeQuickCommand(command: String) {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            adbRepository.shell(device, command)
                .onSuccess { result ->
                    if (result.isError) {
                        val reason = result.output.ifBlank { "Command failed (exit ${result.exitCode})" }
                        _uiState.update { it.copy(error = reason) }
                    } else if (command.startsWith("pm trim-caches")) {
                        showToast("Cache cleanup command sent successfully")
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Command failed") }
                }
        }
    }

    fun sendKey(keycode: Int) {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            adbRepository.shell(device, "input keyevent $keycode")
        }
    }

    fun takeScreenshot() {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            val timestamp = System.currentTimeMillis()
            val remotePath = "/data/local/tmp/tvlink_screenshot_$timestamp.png"

            val result = adbRepository.shell(device, "screencap -p \"$remotePath\"")
            if (result.isFailure) {
                _uiState.update { it.copy(error = "Screenshot failed") }
                return@launch
            }

            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val localFile = File(downloadsDir, "TVLink_Screenshot_$timestamp.png")
                val pullResult = adbRepository.pullFile(device, remotePath, localFile)
                if (pullResult.isSuccess) {
                    showToast("Screenshot saved to phone Downloads")
                } else {
                    _uiState.update { it.copy(error = "Failed to save screenshot to phone") }
                }
            } finally {
                adbRepository.shell(device, "rm -f \"$remotePath\"")
            }
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val device = _uiState.value.connectedDevice ?: return
        _uiState.update { it.copy(isRecording = true) }
        val timestamp = System.currentTimeMillis()
        val remotePath = "/data/local/tmp/tvlink_record_$timestamp.mp4"
        
        // Store the remote path in a tag or variable so stopRecording knows what to pull
        // Using a simple state variable for the current recording path
        currentRecordingRemotePath = remotePath
        
        recordingJob = viewModelScope.launch {
            adbRepository.shell(device, "screenrecord \"$remotePath\"")
        }
    }
    
    private var currentRecordingRemotePath: String? = null

    fun stopRecording() {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            adbRepository.shell(device, "pkill -2 screenrecord")
            withTimeoutOrNull(4000) { recordingJob?.join() }
            _uiState.update { it.copy(isRecording = false) }
            
            val remotePath = currentRecordingRemotePath
            if (remotePath != null) {
                val timestamp = System.currentTimeMillis()
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val localFile = File(downloadsDir, "TVLink_Recording_$timestamp.mp4")
                
                try {
                    waitForRecordingFile(device, remotePath)

                    var pullResult = adbRepository.pullFile(device, remotePath, localFile)
                    if (pullResult.isFailure) {
                        delay(1200)
                        pullResult = adbRepository.pullFile(device, remotePath, localFile)
                    }
                    if (pullResult.isFailure) {
                        delay(1200)
                        pullResult = adbRepository.pullFile(device, remotePath, localFile)
                    }

                    if (pullResult.isSuccess && localFile.exists() && localFile.length() > 0L) {
                        showToast("Recording saved to phone Downloads")
                    } else {
                        val reason = pullResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                        _uiState.update { it.copy(error = reason ?: "Failed to save recording to phone") }
                    }
                } finally {
                    adbRepository.shell(device, "rm -f \"$remotePath\"")
                    currentRecordingRemotePath = null
                }
            }
        }
    }

    private suspend fun waitForRecordingFile(device: AdbDevice, remotePath: String) {
        repeat(10) {
            val check = adbRepository.shell(device, "ls -l \"$remotePath\"")
            if (check.isSuccess) {
                val output = check.getOrNull()?.output.orEmpty()
                if (output.contains(remotePath) && !output.contains("No such file", ignoreCase = true)) {
                    return
                }
            }
            delay(400)
        }
    }

    fun loadFiles(path: String = "/sdcard") {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            _uiState.update { it.copy(isLoadingFiles = true, currentFilePath = path) }
            val result = adbRepository.shell(device, "ls -p \"$path\"")
            result.onSuccess { shellResult ->
                val entries = shellResult.output
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { name ->
                        val isDir = name.endsWith("/")
                        FileEntry(name = name.trimEnd('/'), isDirectory = isDir, path = "$path/${name.trimEnd('/')}")
                    }
                    .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                _uiState.update { it.copy(fileList = entries, isLoadingFiles = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoadingFiles = false, error = "Could not list files") }
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentFilePath
        if (current == "/sdcard" || current == "/") return
        loadFiles(current.substringBeforeLast("/").ifEmpty { "/" })
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            adbRepository.shell(device, "input text \"${escapeForAdbInputText(text)}\"")
        }
    }

    fun sendTextAsKeyboard(previous: String, current: String) {
        if (previous == current) return
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            remoteTextSyncMutex.withLock {
                val commonPrefix = previous.commonPrefixWith(current).length
                val toDelete = previous.length - commonPrefix
                val toAdd = current.substring(commonPrefix)

                repeat(toDelete) {
                    adbRepository.shell(device, "input keyevent 67")
                }
                if (toAdd.isNotEmpty()) {
                    adbRepository.shell(device, "input text \"${escapeForAdbInputText(toAdd)}\"")
                }
            }
        }
    }

    private fun escapeForAdbInputText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace(" ", "%s")
    }

    fun loadDeviceInfo() {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            _uiState.update { it.copy(isLoadingDeviceInfo = true) }

            try {
                suspend fun prop(key: String): String {
                    val r = adbRepository.shell(device, "getprop $key")
                    return r.getOrNull()?.output?.trim() ?: ""
                }

                suspend fun sh(cmd: String): String {
                    val r = adbRepository.shell(device, cmd)
                    return r.getOrNull()?.output?.trim() ?: ""
                }

                val model = prop("ro.product.model")
                val manufacturer = prop("ro.product.manufacturer")
                val androidVersion = prop("ro.build.version.release")
                val apiLevel = prop("ro.build.version.sdk")
                val buildNumber = prop("ro.build.display.id")
                val serial = prop("ro.serialno").ifEmpty { prop("ro.boot.serialno") }
                val securityPatch = prop("ro.build.version.security_patch")
                val kernelVersion = sh("uname -r")
                val cpuArch = prop("ro.product.cpu.abi")
                val bluetoothName = sh("settings get secure bluetooth_name")
                    .takeIf { it != "null" && it.isNotBlank() } ?: ""

                // Screen resolution & density
                val wmSize = sh("wm size").substringAfter(":").trim()
                val wmDensity = sh("wm density").substringAfter(":").trim()

                // RAM from /proc/meminfo
                val memInfo = sh("cat /proc/meminfo")
                val totalKb = memInfo.lines().firstOrNull { it.startsWith("MemTotal") }
                    ?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0
                val availKb = memInfo.lines().firstOrNull { it.startsWith("MemAvailable") }
                    ?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0
                val totalRamBytes = totalKb * 1024
                val availRamBytes = availKb * 1024

                // Storage from df
                val dfOutput = sh("df /data")
                val dfParts = dfOutput.lines().lastOrNull()?.trim()?.split(Regex("\\s+"))
            val totalBlocks = dfParts?.getOrNull(1)?.toLongOrNull() ?: 0
                val usedBlocks = dfParts?.getOrNull(2)?.toLongOrNull() ?: 0
                val totalStorageBytes = totalBlocks * 1024
                val usedStorageBytes = usedBlocks * 1024

                // IP — try multiple methods
            val ipAddr = run {
                // Method 1: ifconfig wlan0
                val ifconfig = sh("ifconfig wlan0 2>/dev/null")
                val ifconfigIp = Regex("inet addr:(\\S+)").find(ifconfig)?.groupValues?.get(1)
                    ?: Regex("inet (\\S+)").find(ifconfig)?.groupValues?.get(1)
                if (!ifconfigIp.isNullOrBlank() && ifconfigIp != "0.0.0.0") return@run ifconfigIp

                // Method 2: ip addr show wlan0
                val ipAddrOut = sh("ip -f inet addr show wlan0 2>/dev/null")
                val ipMatch = Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)").find(ipAddrOut)
                if (ipMatch != null) return@run ipMatch.groupValues[1]

                // Method 3: ip route
                val ipRoute = sh("ip route get 1 2>/dev/null")
                val routeMatch = Regex("src (\\d+\\.\\d+\\.\\d+\\.\\d+)").find(ipRoute)
                if (routeMatch != null) return@run routeMatch.groupValues[1]

                // Fallback: use the device address we connected to
                device.ip
            }

            // MAC — try multiple sources
            val macAddr = run {
                // Method 1: /sys/class/net
                val sysNet = sh("cat /sys/class/net/wlan0/address 2>/dev/null")
                if (sysNet.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"))) return@run sysNet

                // Method 2: ip link show
                val ipLink = sh("ip link show wlan0 2>/dev/null")
                val macMatch = Regex("link/ether ([0-9a-fA-F:]{17})").find(ipLink)
                if (macMatch != null) return@run macMatch.groupValues[1]

                // Method 3: ifconfig
                val ifcfg = sh("ifconfig wlan0 2>/dev/null")
                val hwMatch = Regex("HWaddr ([0-9a-fA-F:]{17})").find(ifcfg)
                    ?: Regex("ether ([0-9a-fA-F:]{17})").find(ifcfg)
                if (hwMatch != null) return@run hwMatch.groupValues[1]

                ""
            }

                // Uptime
                val uptime = sh("uptime -p").removePrefix("up ").ifEmpty {
                    sh("cat /proc/uptime").split(" ").firstOrNull()?.toDoubleOrNull()?.let {
                        val hrs = (it / 3600).toInt()
                        val mins = ((it % 3600) / 60).toInt()
                        "${hrs}h ${mins}m"
                    } ?: ""
                }

                _uiState.update {
                    it.copy(
                        deviceInfo = DeviceInfo(
                            model = model,
                            manufacturer = manufacturer.replaceFirstChar { c -> c.uppercase() },
                            androidVersion = androidVersion,
                            apiLevel = apiLevel,
                            buildNumber = buildNumber,
                            serialNumber = serial,
                            securityPatch = securityPatch,
                            kernelVersion = kernelVersion,
                            cpuArch = cpuArch,
                            bluetoothName = bluetoothName,
                            resolution = wmSize,
                            density = wmDensity,
                            totalRam = formatBytes(totalRamBytes),
                            availRam = formatBytes(availRamBytes),
                            totalRamBytes = totalRamBytes,
                            availRamBytes = availRamBytes,
                            totalStorage = formatBytes(totalStorageBytes),
                            usedStorage = formatBytes(usedStorageBytes),
                            totalStorageBytes = totalStorageBytes,
                            usedStorageBytes = usedStorageBytes,
                            ipAddress = ipAddr,
                            uptime = uptime,
                            macAddress = macAddr
                        ),
                        isLoadingDeviceInfo = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDeviceInfo = false, error = "Could not load device info") }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    // ─── Now Playing / Media ────────────────────────────────────────────

    private fun startMediaPolling() {
        mediaPollingJob?.cancel()
        mediaPollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val device = _uiState.value.connectedDevice ?: break
                try {
                    val result = adbRepository.shell(device, "dumpsys media_session")
                    val output = result.getOrNull()?.output ?: ""
                    val media = withContext(Dispatchers.Default) {
                        parseMediaSession(output)
                    }
                    // Only update if we got valid data; keep last known info otherwise
                    if (media != null) {
                        _uiState.update { state ->
                            if (state.mediaInfo == media) state else state.copy(mediaInfo = media)
                        }
                    }
                } catch (_: Exception) { /* ignore polling errors */ }
                delay(3000)
            }
        }
    }

    private fun stopMediaPolling() {
        mediaPollingJob?.cancel()
        mediaPollingJob = null
    }

    private fun parseMediaSession(dump: String): MediaInfo? {
        val lines = dump.lines()

        var title = ""
        var artist = ""
        var isPlaying = false
        var packageName = ""
        var artworkUrl: String? = null

        // Strategy 1: Look for metadata in the Sessions Stack
        // The first session in the stack is typically the active one
        var inSessionsStack = false
        var foundFirstSession = false
        var inMetadata = false

        for (line in lines) {
            val trimmed = line.trim()

            // Enter Sessions Stack section
            if (trimmed.startsWith("Sessions Stack")) {
                inSessionsStack = true
                continue
            }

            // Detect first session entry (package name in the stack)
            if (inSessionsStack && !foundFirstSession && trimmed.contains("/") && !trimmed.startsWith("--")) {
                // First entry in sessions stack is the active/priority session
                // Format: "com.google.android.youtube.tv/..." or similar
                packageName = trimmed.substringBefore("/").trim()
                foundFirstSession = true
                continue
            }

            if (!foundFirstSession) continue

            // Detect metadata section
            if (trimmed.startsWith("metadata:")) {
                inMetadata = true
                // metadata line may include description: "metadata:size=5, description=Video Title"
                val descMatch = Regex("description=(.+?)(?:,|$)").find(trimmed)
                if (descMatch != null) {
                    val desc = descMatch.groupValues[1].trim()
                    if (desc.isNotBlank() && title.isBlank()) title = desc
                }
                continue
            }

            // Parse metadata key-value pairs
            // Format: "string: android.media.metadata.TITLE=My Video Title"
            if (inMetadata && (trimmed.startsWith("string:") || trimmed.startsWith("uri:"))) {
                val keyValue = trimmed
                    .removePrefix("string:")
                    .removePrefix("uri:")
                    .trim()
                when {
                    keyValue.contains("metadata.DISPLAY_TITLE=") -> {
                        val v = keyValue.substringAfter("metadata.DISPLAY_TITLE=").trim()
                        if (v.isNotBlank()) title = v
                    }
                    keyValue.contains("metadata.TITLE=") -> {
                        val v = keyValue.substringAfter("metadata.TITLE=").trim()
                        if (v.isNotBlank() && title.isBlank()) title = v
                    }
                    keyValue.contains("metadata.ARTIST=") -> {
                        val v = keyValue.substringAfter("metadata.ARTIST=").trim()
                        if (v.isNotBlank()) artist = v
                    }
                    keyValue.contains("metadata.DISPLAY_SUBTITLE=") -> {
                        val v = keyValue.substringAfter("metadata.DISPLAY_SUBTITLE=").trim()
                        if (v.isNotBlank() && artist.isBlank()) artist = v
                    }
                    artworkUrl == null && keyValue.contains("metadata.DISPLAY_ICON_URI=") -> {
                        val v = keyValue.substringAfter("metadata.DISPLAY_ICON_URI=").trim()
                        if (v.startsWith("http://") || v.startsWith("https://")) artworkUrl = v
                    }
                    artworkUrl == null && keyValue.contains("metadata.ART_URI=") -> {
                        val v = keyValue.substringAfter("metadata.ART_URI=").trim()
                        if (v.startsWith("http://") || v.startsWith("https://")) artworkUrl = v
                    }
                    artworkUrl == null && keyValue.contains("metadata.ALBUM_ART_URI=") -> {
                        val v = keyValue.substringAfter("metadata.ALBUM_ART_URI=").trim()
                        if (v.startsWith("http://") || v.startsWith("https://")) artworkUrl = v
                    }
                }
                continue
            }

            // Parse playback state — ONLY match the PlaybackState line
            if (foundFirstSession && trimmed.contains("PlaybackState")) {
                val pbMatch = Regex("""PlaybackState\s*\{state=(\d+)""").find(trimmed)
                if (pbMatch != null) {
                    val stateCode = pbMatch.groupValues[1].toIntOrNull() ?: 0
                    isPlaying = stateCode == 3
                }
            }

            // Stop at next session or blank after metadata
            if (foundFirstSession && inMetadata && trimmed.isBlank()) {
                inMetadata = false
            }
            // If we encounter another session entry, stop
            if (foundFirstSession && trimmed.startsWith("com.") && trimmed.contains("/") && packageName.isNotEmpty()
                && !trimmed.startsWith(packageName)) {
                break
            }
        }

        if (title.isBlank() && artist.isBlank()) return null

        return MediaInfo(
            title = title.ifBlank { "Unknown" },
            artist = artist,
            isPlaying = isPlaying,
            packageName = packageName,
            artworkUrl = artworkUrl
        )
    }

    fun sendMediaKey(keycode: Int) {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            // Optimistically toggle play/pause state for instant UI feedback
            if (keycode == 85) { // MEDIA_PLAY_PAUSE
                _uiState.update { state ->
                    state.mediaInfo?.let { media ->
                        state.copy(mediaInfo = media.copy(isPlaying = !media.isPlaying))
                    } ?: state
                }
            }
            adbRepository.shell(device, "input keyevent $keycode")
            // Wait for the TV to update its state, then re-poll
            delay(1500)
            try {
                val result = adbRepository.shell(device, "dumpsys media_session")
                val output = result.getOrNull()?.output ?: ""
                val media = parseMediaSession(output)
                if (media != null) {
                    _uiState.update { state ->
                        if (state.mediaInfo == media) state else state.copy(mediaInfo = media)
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // ─── Screensaver ────────────────────────────────────────────────────

    fun launchScreensaver() {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            // Try service call first (most reliable), then cmd dreams
            val result = adbRepository.shell(device, "service call dreams 1")
            if (result.isFailure || result.getOrNull()?.isError == true) {
                adbRepository.shell(device, "cmd dreams start-dreaming")
            }
        }
    }

    // ─── Upload to Downloads ────────────────────────────────────────────

    fun uploadToDownloads(uri: Uri) {
        viewModelScope.launch {
            val device = _uiState.value.connectedDevice ?: return@launch
            _uiState.update { it.copy(isUploading = true) }

            try {
                // Resolve file name from URI
                val fileName = getFileNameFromUri(uri) ?: "uploaded_file"
                val remotePath = "/sdcard/Download/$fileName"

                // Copy URI content to a temp file
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not read file")

                // Push to device
                val result = adbRepository.pushFile(device, tempFile, remotePath)
                tempFile.delete()

                result.onSuccess {
                    showToast("Uploaded $fileName to TV Downloads")
                }.onFailure { e ->
                    _uiState.update { it.copy(error = "Upload failed: ${e.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Upload failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun showToast(message: String) = _uiState.update { it.copy(toast = message) }
    fun clearToast() = _uiState.update { it.copy(toast = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
