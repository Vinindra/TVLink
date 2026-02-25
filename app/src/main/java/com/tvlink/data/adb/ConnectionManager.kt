package com.tvlink.data.adb

import com.tvlink.data.adb.model.AdbDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor() {

    private val _connectedDevice = MutableStateFlow<AdbDevice?>(null)
    val connectedDevice: StateFlow<AdbDevice?> = _connectedDevice.asStateFlow()

    fun setConnected(device: AdbDevice?) {
        _connectedDevice.value = device
    }
}
