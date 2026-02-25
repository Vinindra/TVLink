package com.tvlink.ui.apps

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvlink.data.adb.AdbRepository
import com.tvlink.data.adb.ConnectionManager
import com.tvlink.data.adb.model.AdbDevice
import com.tvlink.data.adb.model.InstalledApp
import com.tvlink.data.adb.model.InstallProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class AppsUiState(
    val apps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val showSystemApps: Boolean = false,
    val filter: String = "",
    val error: String? = null,
    val installProgress: InstallProgress? = null
)

private data class AppMetadata(
    val iconUrl: String?,
    val label: String?
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val adbRepository: AdbRepository,
    private val connectionManager: ConnectionManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    private var allApps: List<InstalledApp> = emptyList()

    // In-memory app metadata cache to avoid re-fetching on filter changes
    private val metadataCache = mutableMapOf<String, AppMetadata?>()

    init {
        viewModelScope.launch {
            connectionManager.connectedDevice.collect { device ->
                val connected = device != null
                _uiState.update { it.copy(isConnected = connected) }
                if (connected) {
                    loadApps()
                } else {
                    allApps = emptyList()
                    _uiState.update { it.copy(apps = emptyList(), isLoading = false) }
                }
            }
        }
    }

    private fun getDevice(): AdbDevice? = connectionManager.connectedDevice.value

    fun loadApps() {
        val device = getDevice() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            adbRepository.listPackages(device).onSuccess { apps ->
                allApps = apps
                _uiState.update {
                    it.copy(isLoading = false, apps = filterApps(apps, it.filter, it.showSystemApps))
                }
                fetchAppMetadata(apps)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(query: String) {
        _uiState.update {
            it.copy(filter = query, apps = filterApps(allApps, query, it.showSystemApps))
        }
    }

    fun toggleSystemApps() {
        _uiState.update {
            val newShow = !it.showSystemApps
            it.copy(showSystemApps = newShow, apps = filterApps(allApps, it.filter, newShow))
        }
    }

    fun setShowSystemApps(show: Boolean) {
        _uiState.update {
            it.copy(showSystemApps = show, apps = filterApps(allApps, it.filter, show))
        }
    }

    fun launchApp(packageName: String) {
        val device = getDevice() ?: return
        viewModelScope.launch {
            adbRepository.shell(device, "monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        }
    }

    fun uninstallApp(packageName: String) {
        val device = getDevice() ?: return
        viewModelScope.launch {
            adbRepository.uninstall(device, packageName).onSuccess {
                loadApps()
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun installApk(uri: Uri) {
        val device = getDevice() ?: run {
            _uiState.update { it.copy(error = "Not connected to any device") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(installProgress = InstallProgress.Installing(0)) }
            try {
                val cacheFile = withContext(Dispatchers.IO) {
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot read the selected file")
                    val dest = File(appContext.cacheDir, "install_temp.apk")
                    input.use { it.copyTo(dest.outputStream()) }
                    dest
                }

                adbRepository.installApk(device, cacheFile).collect { progress ->
                    _uiState.update { it.copy(installProgress = progress) }
                    if (progress is InstallProgress.Success) loadApps()
                    if (progress is InstallProgress.Failed) {
                        _uiState.update { it.copy(error = progress.reason) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(installProgress = InstallProgress.Failed(e.message ?: "Install failed"), error = e.message)
                }
            }
        }
    }

    fun clearInstallProgress() {
        _uiState.update { it.copy(installProgress = null) }
    }

    /**
     * Fetches app metadata in parallel (up to 6 concurrent requests) with an in-memory cache.
     */
    private fun fetchAppMetadata(apps: List<InstalledApp>) {
        viewModelScope.launch(Dispatchers.IO) {
            val semaphore = Semaphore(6)
            val nonSystemApps = apps.filter { !it.isSystemApp }

            val updates = nonSystemApps.map { app ->
                async {
                    // Check cache first
                    if (metadataCache.containsKey(app.packageName)) {
                        return@async app.packageName to metadataCache[app.packageName]
                    }

                    val metadata = semaphore.withPermit {
                        extractAppMetadata(app.packageName)
                    }
                    metadataCache[app.packageName] = metadata
                    app.packageName to metadata
                }
            }.awaitAll()
                .mapNotNull { (packageName, metadata) ->
                    metadata?.let { packageName to it }
                }
                .toMap()

            updateAppMetadata(updates)
        }
    }

    private fun updateAppMetadata(updates: Map<String, AppMetadata>) {
        if (updates.isEmpty()) return

        allApps = allApps.map { app ->
            val metadata = updates[app.packageName] ?: return@map app
            app.copy(
                iconUrl = metadata.iconUrl ?: app.iconUrl,
                label = metadata.label?.takeIf { it.isNotBlank() } ?: app.label
            )
        }
        _uiState.update { state ->
            state.copy(apps = filterApps(allApps, state.filter, state.showSystemApps))
        }
    }

    private fun extractAppMetadata(packageName: String): AppMetadata? {
        return try {
            val connection = URL("https://play.google.com/store/apps/details?id=$packageName")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.connectTimeout = 2500
            connection.readTimeout = 2500

            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val iconUrl = Regex("""property="og:image"\s+content="([^"]+)"""")
                    .find(html)
                    ?.groupValues
                    ?.getOrNull(1)

                val rawTitle = Regex("""property="og:title"\s+content="([^"]+)"""")
                    .find(html)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()

                val label = normalizePlayTitle(rawTitle)
                if (iconUrl == null && label == null) null else AppMetadata(iconUrl = iconUrl, label = label)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizePlayTitle(rawTitle: String?): String? {
        val title = rawTitle?.trim().orEmpty()
        if (title.isEmpty()) return null

        val separator = title.lastIndexOf(" - ")
        if (separator > 0) {
            val suffix = title.substring(separator + 3)
            if (suffix.contains("Google Play", ignoreCase = true)) {
                return title.substring(0, separator).trim()
            }
        }
        return title
    }

    private fun filterApps(
        apps: List<InstalledApp>,
        query: String,
        showSystem: Boolean
    ): List<InstalledApp> {
        return apps.filter { app ->
            (showSystem || !app.isSystemApp) &&
                    (query.isEmpty() || app.packageName.contains(query, ignoreCase = true) ||
                            app.label.contains(query, ignoreCase = true))
        }
    }
}
