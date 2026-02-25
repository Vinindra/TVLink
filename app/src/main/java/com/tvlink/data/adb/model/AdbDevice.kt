package com.tvlink.data.adb.model

data class AdbDevice(
    val ip: String,
    val port: Int = 5555,
    val name: String = "",
    val isConnected: Boolean = false
) {
    val address: String get() = "$ip:$port"
}
