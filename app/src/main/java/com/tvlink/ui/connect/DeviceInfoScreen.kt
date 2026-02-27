package com.tvlink.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DeviceInfoScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.connectedDevice) {
        if (state.connectedDevice != null) viewModel.loadDeviceInfo()
    }

    if (state.connectedDevice == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("No Device Connected", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text("Connect to a TV to view device info.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    if (state.isLoadingDeviceInfo || state.deviceInfo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                Text("Loading device info…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        val info = state.deviceInfo!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Header card ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 28.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Tv, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primaryContainer)
                }
                Text(
                    info.model.ifEmpty { "Unknown Device" },
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${info.manufacturer} • Android ${info.androidVersion}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }

            // ── System ───────────────────────────────────────────────
            InfoSection(title = "System", icon = Icons.Outlined.Smartphone) {
                InfoRow("Model", info.model)
                InfoRow("Manufacturer", info.manufacturer)
                InfoRow("Android Version", info.androidVersion)
                InfoRow("API Level", info.apiLevel)
                InfoRow("Build", info.buildNumber)
                if (info.securityPatch.isNotEmpty()) InfoRow("Security Patch", info.securityPatch)
                if (info.kernelVersion.isNotEmpty()) InfoRow("Kernel", info.kernelVersion)
                if (info.serialNumber.isNotEmpty()) InfoRow("Serial", info.serialNumber)
            }

            // ── Hardware ─────────────────────────────────────────────
            InfoSection(title = "Hardware", icon = Icons.Outlined.DeveloperBoard) {
                if (info.cpuArch.isNotEmpty()) InfoRow("CPU Architecture", info.cpuArch)
                InfoRow("Resolution", info.resolution)
                InfoRow("Density", "${info.density} dpi")
            }

            // ── Memory ───────────────────────────────────────────────
            InfoSection(title = "Memory", icon = Icons.Outlined.Memory) {
                InfoRow("Total RAM", info.totalRam)
                InfoRow("Available RAM", info.availRam)
                val usedFraction = if (info.totalRamBytes > 0)
                    ((info.totalRamBytes - info.availRamBytes).toFloat() / info.totalRamBytes).coerceIn(0f, 1f)
                else 0f
                Spacer(Modifier.height(4.dp))
                UsageBar(label = "Usage", fraction = usedFraction)
            }

            // ── Storage ──────────────────────────────────────────────
            InfoSection(title = "Storage", icon = Icons.Outlined.Storage) {
                InfoRow("Total", info.totalStorage)
                InfoRow("Used", info.usedStorage)
                val storageFraction = if (info.totalStorageBytes > 0)
                    (info.usedStorageBytes.toFloat() / info.totalStorageBytes).coerceIn(0f, 1f)
                else 0f
                Spacer(Modifier.height(4.dp))
                UsageBar(label = "Usage", fraction = storageFraction)
            }


            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun UsageBar(label: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(fraction * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun InfoSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value.ifEmpty { "—" },
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}
