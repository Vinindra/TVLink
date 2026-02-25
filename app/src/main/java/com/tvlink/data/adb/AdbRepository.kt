package com.tvlink.data.adb

import com.tvlink.data.adb.model.AdbDevice
import com.tvlink.data.adb.model.InstalledApp
import com.tvlink.data.adb.model.InstallProgress
import com.tvlink.data.adb.model.ShellResult
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AdbRepository {
    suspend fun connect(ip: String, port: Int = 5555): Result<AdbDevice>
    suspend fun disconnect(device: AdbDevice)
    suspend fun shell(device: AdbDevice, command: String): Result<ShellResult>
    fun installApk(device: AdbDevice, apkFile: File): Flow<InstallProgress>
    suspend fun listPackages(device: AdbDevice): Result<List<InstalledApp>>
    suspend fun uninstall(device: AdbDevice, packageName: String): Result<Unit>
    suspend fun pushFile(device: AdbDevice, localFile: File, remotePath: String): Result<Unit>
    suspend fun pullFile(device: AdbDevice, remotePath: String, localFile: File): Result<Unit>
    fun getConnectedDevices(): Flow<List<AdbDevice>>
}
