package com.tvlink.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tvlink.data.adb.model.AdbDevice
import com.tvlink.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_prefs")

@Singleton
class DevicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val SAVED_DEVICES = stringSetPreferencesKey("saved_devices")
        private val LAST_CONNECTED = stringPreferencesKey("last_connected_device")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val savedDevices: Flow<List<AdbDevice>> = context.dataStore.data.map { prefs ->
        val deviceSet = prefs[SAVED_DEVICES] ?: emptySet()
        deviceSet.map { entry ->
            val parts = entry.split(":")
            AdbDevice(
                ip = parts.getOrElse(0) { "" },
                port = parts.getOrElse(1) { "5555" }.toIntOrNull() ?: 5555,
                name = parts.getOrElse(2) { "" }
            )
        }
    }

    suspend fun saveDevice(device: AdbDevice) {
        context.dataStore.edit { prefs ->
            val current = prefs[SAVED_DEVICES]?.toMutableSet() ?: mutableSetOf()
            // Remove old entry for same IP:port if exists
            current.removeAll { it.startsWith("${device.ip}:${device.port}:") }
            current.add("${device.ip}:${device.port}:${device.name}")
            prefs[SAVED_DEVICES] = current
        }
    }

    suspend fun removeDevice(device: AdbDevice) {
        context.dataStore.edit { prefs ->
            val current = prefs[SAVED_DEVICES]?.toMutableSet() ?: mutableSetOf()
            current.removeAll { it.startsWith("${device.ip}:${device.port}:") }
            prefs[SAVED_DEVICES] = current
        }
    }

    suspend fun saveLastConnected(device: AdbDevice) {
        context.dataStore.edit { prefs ->
            prefs[LAST_CONNECTED] = "${device.ip}:${device.port}:${device.name}"
        }
    }

    suspend fun clearLastConnected() {
        context.dataStore.edit { prefs ->
            prefs.remove(LAST_CONNECTED)
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK"  -> ThemeMode.DARK
            else    -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }

    val lastConnectedDevice: Flow<AdbDevice?> = context.dataStore.data.map { prefs ->
        val entry = prefs[LAST_CONNECTED] ?: return@map null
        val parts = entry.split(":")
        AdbDevice(
            ip = parts.getOrElse(0) { "" },
            port = parts.getOrElse(1) { "5555" }.toIntOrNull() ?: 5555,
            name = parts.getOrElse(2) { "" }
        )
    }
}
