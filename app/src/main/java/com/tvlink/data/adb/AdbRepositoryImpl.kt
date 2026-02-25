package com.tvlink.data.adb

import com.tvlink.data.adb.model.AdbDevice
import com.tvlink.data.adb.model.InstalledApp
import com.tvlink.data.adb.model.InstallProgress
import com.tvlink.data.adb.model.ShellResult
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbRepositoryImpl @Inject constructor(
    private val keyManager: AdbKeyManager
) : AdbRepository {

    private val connections = mutableMapOf<String, Dadb>()
    private val _connectedDevices = MutableStateFlow<List<AdbDevice>>(emptyList())

    override suspend fun connect(ip: String, port: Int): Result<AdbDevice> =
        withContext(Dispatchers.IO) {
            try {
                val address = "$ip:$port"

                // Close existing connection if any
                connections[address]?.close()

                val dadb = Dadb.create(ip, port, keyManager.keyPair)
                connections[address] = dadb

                // Verify connection by running a simple command
                val response = dadb.shell("echo connected")
                if (response.exitCode != 0) {
                    connections.remove(address)
                    dadb.close()
                    return@withContext Result.failure(
                        Exception("Failed to verify connection to $address")
                    )
                }

                val device = AdbDevice(
                    ip = ip,
                    port = port,
                    name = getDeviceName(dadb),
                    isConnected = true
                )

                _connectedDevices.update { devices ->
                    devices.filter { it.address != address } + device
                }

                Result.success(device)
            } catch (e: Exception) {
                Result.failure(
                    Exception("Could not connect to $ip:$port — ${e.message}", e)
                )
            }
        }

    override suspend fun disconnect(device: AdbDevice) {
        withContext(Dispatchers.IO) {
            try {
                connections[device.address]?.close()
            } catch (_: Exception) {
                // Ignore errors on disconnect
            } finally {
                connections.remove(device.address)
                _connectedDevices.update { devices ->
                    devices.map {
                        if (it.address == device.address) it.copy(isConnected = false) else it
                    }
                }
            }
        }
    }

    override suspend fun shell(device: AdbDevice, command: String): Result<ShellResult> =
        withContext(Dispatchers.IO) {
            try {
                val dadb = connections[device.address]
                    ?: return@withContext Result.failure(
                        Exception("Device ${device.address} is not connected")
                    )

                val response = dadb.shell(command)
                Result.success(
                    ShellResult(
                        output = response.allOutput.trim(),
                        exitCode = response.exitCode
                    )
                )
            } catch (e: Exception) {
                Result.failure(
                    Exception("Shell command failed: ${e.message}", e)
                )
            }
        }

    override fun installApk(device: AdbDevice, apkFile: File): Flow<InstallProgress> = flow {
        emit(InstallProgress.Installing(0))

        val dadb = connections[device.address]
            ?: throw Exception("Device ${device.address} is not connected. Please reconnect and try again.")

        emit(InstallProgress.Installing(20))

        try {
            // Push the APK to device temp storage
            val remotePath = "/data/local/tmp/tvlink_install.apk"
            dadb.push(apkFile, remotePath)

            emit(InstallProgress.Installing(60))

            // Run pm install and capture full output
            val response = dadb.shell("pm install -r -t \"$remotePath\"")
            val output = response.allOutput.trim()

            emit(InstallProgress.Installing(90))

            // Clean up remote temp file
            dadb.shell("rm -f \"$remotePath\"")

            emit(InstallProgress.Installing(100))

            // pm install outputs "Success" or "Failure [REASON]"
            if (output.contains("Success", ignoreCase = true)) {
                emit(InstallProgress.Success)
            } else {
                val reason = output
                    .lines()
                    .firstOrNull { it.contains("Failure", ignoreCase = true) || it.contains("Error", ignoreCase = true) }
                    ?.trim()
                    ?: output.ifEmpty { "Unknown install error" }
                emit(InstallProgress.Failed(reason))
            }
        } catch (e: Exception) {
            emit(InstallProgress.Failed(e.message ?: "Installation failed"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listPackages(device: AdbDevice): Result<List<InstalledApp>> =
        withContext(Dispatchers.IO) {
            try {
                val dadb = connections[device.address]
                    ?: return@withContext Result.failure(
                        Exception("Device ${device.address} is not connected")
                    )

                // Get third-party packages
                val userAppsResponse = dadb.shell("pm list packages -3")
                val userPackages = parsePackageList(userAppsResponse.allOutput, isSystem = false)

                // Get system packages
                val systemAppsResponse = dadb.shell("pm list packages -s")
                val systemPackages = parsePackageList(systemAppsResponse.allOutput, isSystem = true)

                val dedupedPackages = (userPackages + systemPackages)
                    .groupBy { it.packageName }
                    .map { (_, apps) -> apps.minBy { it.isSystemApp } }

                Result.success(dedupedPackages.sortedBy { it.label.lowercase() })
            } catch (e: Exception) {
                Result.failure(
                    Exception("Failed to list packages: ${e.message}", e)
                )
            }
        }

    /**
     * Converts a package name to a human-readable label.
     * Well-known apps get canonical names; unknown apps get a cleaned product segment.
     */
    private fun formatPackageName(packageName: String): String {
        val knownApps = mapOf(
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "com.google.android.youtube" to "YouTube",
            "com.google.android.tvlauncher" to "TV Launcher",
            "com.google.android.tvrecommendations" to "Recommendations",
            "com.google.android.tvsettings" to "TV Settings",
            "com.google.android.tv" to "Google TV",
            "com.google.android.tv.remote.service" to "TV Remote",
            "com.google.android.gms" to "Play Services",
            "com.google.android.gsf" to "Services Framework",
            "com.google.android.apps.tv.launcherx" to "Google TV Home",
            "com.google.android.inputmethod.latin" to "Gboard",
            "com.google.android.katniss" to "Google Search",
            "com.google.android.backdrop" to "Backdrop",
            "com.google.android.play.games" to "Play Games",
            "com.android.tv.settings" to "TV Settings",
            "com.android.vending" to "Play Store",
            "com.android.providers.tv" to "TV Provider",
            "com.netflix.ninja" to "Netflix",
            "com.netflix.mediaclient" to "Netflix",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.amazon.amazonvideo.livingroom.nvidia" to "Prime Video",
            "com.disney.disneyplus" to "Disney+",
            "com.disney.disneyplus.in" to "Disney+ Hotstar",
            "com.plexapp.android" to "Plex",
            "org.xbmc.kodi" to "Kodi",
            "com.spotify.tv.android" to "Spotify",
            "com.spotify.music" to "Spotify",
            "com.apple.atve.androidtv.appletv" to "Apple TV",
            "tv.twitch.android.app" to "Twitch",
            "com.hbo.hbonow" to "HBO Max",
            "in.startv.hotstar" to "Hotstar",
            "com.jio.media.jiobeats" to "JioSaavn",
            "com.jio.jioplay.tv" to "JioTV",
            "com.mxtech.videoplayer.ad" to "MX Player",
            "com.mxtech.videoplayer.pro" to "MX Player Pro",
            "com.mxtech.videoplayer.beta" to "MX Player Beta",
            "org.videolan.vlc" to "VLC",
            "com.teamsmart.videomanager.tv" to "SmartTube",
            "com.liskovsoft.smarttubetv.beta" to "SmartTube Beta",
            "com.liskovsoft.smarttubetv" to "SmartTube",
            "com.stremio.one" to "Stremio",
            "com.es.explorer" to "ES File Explorer",
            "com.solid.explorer" to "Solid Explorer",
            "com.termux" to "Termux",
            "com.brave.browser" to "Brave",
            "org.mozilla.firefox" to "Firefox",
            "com.opera.browser" to "Opera",
        )

        knownApps[packageName]?.let { return it }

        val segments = packageName.split(".")
        val ignoredSegments = setOf(
            "com", "org", "net", "io", "me", "in", "tv", "app", "de", "uk", "co", "ai",
            "android", "apps", "mobile", "global"
        )

        val candidate = segments
            .asReversed()
            .firstOrNull { segment ->
                val value = segment.lowercase()
                value.length > 1 && value !in ignoredSegments
            } ?: segments.lastOrNull().orEmpty()

        return prettifySegment(candidate)
    }

    private fun prettifySegment(segment: String): String {
        val withSpaces = segment
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .trim()

        if (withSpaces.isEmpty()) return "Unknown App"

        return withSpaces
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                if (token.length <= 4 && token.all { it.isLetter() && it.isUpperCase() }) {
                    token
                } else {
                    token.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
    }

    override suspend fun uninstall(device: AdbDevice, packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dadb = connections[device.address]
                    ?: return@withContext Result.failure(
                        Exception("Device ${device.address} is not connected")
                    )

                dadb.uninstall(packageName)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(
                    Exception("Failed to uninstall $packageName: ${e.message}", e)
                )
            }
        }

    override suspend fun pushFile(device: AdbDevice, localFile: File, remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dadb = connections[device.address]
                    ?: return@withContext Result.failure(
                        Exception("Device ${device.address} is not connected")
                    )
                dadb.push(localFile, remotePath)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(
                    Exception("Failed to push file: ${e.message}", e)
                )
            }
        }

    override suspend fun pullFile(device: AdbDevice, remotePath: String, localFile: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dadb = requireConnection(device)
                dadb.pull(localFile, remotePath)
                Result.success(Unit)
            } catch (e: Exception) {
                val isTransient = isTransientTransportError(e)
                if (!isTransient) {
                    return@withContext Result.failure(
                        Exception("Failed to pull file: ${e.message}", e)
                    )
                }

                try {
                    reconnect(device)
                    val dadb = requireConnection(device)
                    dadb.pull(localFile, remotePath)
                    Result.success(Unit)
                } catch (retryError: Exception) {
                    Result.failure(
                        Exception("Failed to pull file after reconnect: ${retryError.message}", retryError)
                    )
                }
            }
        }

    override fun getConnectedDevices(): Flow<List<AdbDevice>> = _connectedDevices.asStateFlow()

    private fun getDeviceName(dadb: Dadb): String {
        return try {
            val model = dadb.shell("getprop ro.product.model").allOutput.trim()
            val brand = dadb.shell("getprop ro.product.brand").allOutput.trim()
            if (brand.isNotEmpty() && model.isNotEmpty()) {
                "$brand $model"
            } else {
                model.ifEmpty { "Unknown Device" }
            }
        } catch (_: Exception) {
            "Unknown Device"
        }
    }

    private fun parsePackageList(output: String, isSystem: Boolean): List<InstalledApp> {
        return output.lines()
            .filter { it.startsWith("package:") }
            .map { line ->
                val packageName = line.removePrefix("package:").trim()
                InstalledApp(
                    packageName = packageName,
                    label = formatPackageName(packageName),
                    isSystemApp = isSystem
                )
            }
    }

    private fun requireConnection(device: AdbDevice): Dadb {
        return connections[device.address]
            ?: throw Exception("Device ${device.address} is not connected")
    }

    private fun reconnect(device: AdbDevice) {
        connections[device.address]?.close()
        val fresh = Dadb.create(device.ip, device.port, keyManager.keyPair)
        connections[device.address] = fresh
    }

    private fun isTransientTransportError(error: Throwable): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return message.contains("broken pipe") ||
            message.contains("connection reset") ||
            message.contains("socket closed") ||
            message.contains("eof")
    }
}
