package com.tvlink.data.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.tvlink.data.adb.model.AdbDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _discoveredDevices = MutableStateFlow<List<AdbDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<AdbDevice>> = _discoveredDevices.asStateFlow()

    private val SERVICE_TYPE = "_adb._tcp."
    private var isDiscovering = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d("NSD", "Discovery started")
            isDiscovering = true
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d("NSD", "Service found: ${service.serviceName}")
            if (service.serviceType.contains("_adb._tcp")) {
                nsdManager?.resolveService(service, resolveListener)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d("NSD", "Service lost: ${service.serviceName}")
            _discoveredDevices.update { devices ->
                devices.filterNot { it.name == service.serviceName }
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d("NSD", "Discovery stopped")
            isDiscovering = false
            _discoveredDevices.update { emptyList() }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("NSD", "Discovery start failed: Error code: $errorCode")
            nsdManager?.stopServiceDiscovery(this)
            isDiscovering = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("NSD", "Discovery stop failed: Error code: $errorCode")
            nsdManager?.stopServiceDiscovery(this)
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("NSD", "Resolve failed: Error code: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d("NSD", "Resolve succeeded: ${serviceInfo.serviceName}")
            val host = serviceInfo.host ?: return
            val ipAddress = host.hostAddress ?: return

            val device = AdbDevice(
                ip = ipAddress,
                port = serviceInfo.port,
                name = serviceInfo.serviceName,
                isConnected = false
            )

            _discoveredDevices.update { devices ->
                val filtered = devices.filterNot { it.address == device.address }
                filtered + device
            }
        }
    }

    fun startDiscovery() {
        if (isDiscovering || nsdManager == null) return
        
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NSD", "Failed to start discovery", e)
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering || nsdManager == null) return
        
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e("NSD", "Failed to stop discovery", e)
        }
    }
}
