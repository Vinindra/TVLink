package com.tvlink.ui.connect

import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tvlink.data.adb.model.AdbDevice
import com.tvlink.ui.theme.*

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onNavigateToRemote: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPairSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // File picker for Upload to Downloads
    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadToDownloads(it) }
    }

    LaunchedEffect(state.connectedDevice) {
        if (state.connectedDevice != null) {
            val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(haptic)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHostState.showSnackbar(it); viewModel.clearToast() }
    }
    LaunchedEffect(state.connectedDevice) {
        if (state.connectedDevice != null && showPairSheet) {
            scope.launch { sheetState.hide() }; showPairSheet = false
        }
    }

    if (showPairSheet) {
        PairDeviceSheet(
            sheetState = sheetState, isConnecting = state.isConnecting, error = state.error,
            onConnect = { ip, port -> viewModel.connect(ip, port) },
            onDismiss = { showPairSheet = false; viewModel.clearError() }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.connectedDevice == null && !state.isConnecting,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                FloatingActionButton(
                    onClick = { showPairSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Rounded.Add, "Add Device") }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCard(connectedDevice = state.connectedDevice, isConnecting = state.isConnecting)

                if (state.connectedDevice == null && !state.isConnecting) {
                    InfoTile(
                        modifier = Modifier.fillMaxWidth(),
                        label = "ADB SERVICE",
                        value = "Running"
                    )
                }

                when {
                    state.isConnecting -> ConnectingState()
                    state.connectedDevice != null -> ConnectedState(
                        isRecording = state.isRecording,
                        mediaInfo = state.mediaInfo,
                        isUploading = state.isUploading,
                        onDisconnect = { viewModel.disconnect() },
                        onRemote = onNavigateToRemote,
                        onFiles = onNavigateToFiles,
                        onScreenshot = { viewModel.takeScreenshot() },
                        onToggleRecord = { viewModel.toggleRecording() },
                        onQuickCommand = { viewModel.executeQuickCommand(it) },
                        onScreensaver = { viewModel.launchScreensaver() },
                        onUpload = { uploadPicker.launch("*/*") },
                        onMediaKey = { viewModel.sendMediaKey(it) }
                    )
                    else -> DisconnectedState(
                        savedDevices = state.savedDevices,
                        discoveredDevices = state.discoveredDevices,
                        isScanning = state.isScanning,
                        onDeviceClick = { viewModel.connect(it.ip, it.port) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Connection Card ──────────────────────────────────────────────────────────

@Composable
private fun StatusCard(connectedDevice: AdbDevice?, isConnecting: Boolean) {
    val isConnected = connectedDevice != null && !isConnecting

    val colorScheme = MaterialTheme.colorScheme
    // Border: AccentDim when connected, BorderColor when not
    val borderColor by animateColorAsState(if (isConnected) colorScheme.secondary else colorScheme.outline, tween(500), label = "borderColor")
    
    // Resolve colors outside drawBehind as it is not a @Composable context
    val primaryContainer = colorScheme.primaryContainer
    val secondaryContainer = colorScheme.secondaryContainer
    val primary = colorScheme.primary
    val surface = colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .drawBehind {
                if (isConnected) {
                    // Linear gradient AccentBgMid -> AccentBg
                    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(primaryContainer, secondaryContainer),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                    )
                    drawRect(brush)
                    // Radial glow overlay at top
                    val glowBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.12f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
                        radius = size.width / 1.5f
                    )
                    drawRect(glowBrush)
                } else {
                    drawRect(surface)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 28.dp, horizontal = 20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TV icon in a 72.dp circle with 2.dp border
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) colorScheme.primary.copy(alpha = 0.13f) else colorScheme.surfaceVariant)
                    .border(2.dp, if (isConnected) colorScheme.primary else colorScheme.outline, CircleShape)
                    .drawBehind {
                        if (isConnected) {
                            drawCircle(
                                color = primary.copy(alpha = 0.2f),
                                radius = size.width / 2 + 12.dp.toPx()
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Tv,
                    null,
                    modifier = Modifier.size(34.dp),
                    tint = if (isConnected) colorScheme.primary else colorScheme.outlineVariant
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (isConnected && connectedDevice != null) connectedDevice.name.ifEmpty { connectedDevice.address } else "Not Connected",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isConnected && connectedDevice != null) "${connectedDevice.address} · ADB"
                       else "Ensure TV and phone are on the same WiFi",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            if (isConnected) {
                Spacer(modifier = Modifier.height(14.dp))
                LiveIndicator()
            }
        }
    }
}

@Composable
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse), label = "alpha")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)))
        Text(
            text = "LIVE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
            letterSpacing = 1.sp
        )
    }
}

// ─── ADB Banner ───────────────────────────────────────────────────────────────

@Composable
private fun InfoTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val tertiary = colorScheme.tertiary
    val tertiaryContainer = colorScheme.tertiaryContainer
    val onSurface = colorScheme.onSurface
    val onSurfaceVariant = colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tertiaryContainer)
            .border(1.dp, tertiary.copy(0.27f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tertiary.copy(0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tertiary)
                    .drawBehind {
                        drawCircle(tertiary.copy(alpha = 0.5f), radius = size.width / 2 + 4.dp.toPx())
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tertiary, letterSpacing = 0.08.sp)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
            }
            Text("Port 5555", fontSize = 11.sp, color = onSurfaceVariant)
        }
    }
}

// ─── Disconnected State ───────────────────────────────────────────────────────

@Composable
private fun DisconnectedState(
    savedDevices: List<AdbDevice>,
    discoveredDevices: List<AdbDevice>,
    isScanning: Boolean,
    onDeviceClick: (AdbDevice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (discoveredDevices.isNotEmpty() || isScanning) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionRow("Discovered on Network", isScanning)
                val fresh = discoveredDevices.filter { d -> savedDevices.none { it.address == d.address } }
                fresh.forEach { DeviceRow(it, isNew = true) { onDeviceClick(it) } }
                if (fresh.isEmpty() && !isScanning) {
                    Text("No new devices found.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionRow("Available Devices", false)
            if (savedDevices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Text("No saved devices.\nTap + to add one.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp)
                }
            } else {
                savedDevices.forEach { DeviceRow(it) { onDeviceClick(it) } }
            }
        }
    }
}

@Composable
private fun SectionRow(title: String, scanning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp, color = MaterialTheme.colorScheme.primary)
        AnimatedVisibility(visible = scanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Text("SCANNING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: AdbDevice, isNew: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Tv, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(device.name.ifEmpty { "Android Device" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(device.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.outlineVariant, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Connect", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─── Connecting State ─────────────────────────────────────────────────────────

@Composable
private fun ConnectingState() {
    val transition = rememberInfiniteTransition(label = "ripple")
    val rippleScale by transition.animateFloat(1f, 2f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "scale")
    val rippleAlpha by transition.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "alpha")
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(28.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(64.dp).scale(rippleScale).drawBehind { drawCircle(primaryColor.copy(alpha = rippleAlpha), size.minDimension / 2) })
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Smartphone, null, modifier = Modifier.size(32.dp), tint = primaryColor)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Connecting...", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text("Accept the USB debugging prompt on your TV.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ─── Connected State ──────────────────────────────────────────────────────────

@Composable
private fun ConnectedState(
    isRecording: Boolean,
    mediaInfo: MediaInfo?,
    isUploading: Boolean,
    onDisconnect: () -> Unit,
    onRemote: () -> Unit,
    onFiles: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleRecord: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onScreensaver: () -> Unit,
    onUpload: () -> Unit,
    onMediaKey: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("QUICK TOOLS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.08.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard(Modifier.weight(1f), Icons.Outlined.Gamepad, "Remote", onClick = onRemote)
                ToolCard(Modifier.weight(1f), Icons.Outlined.Folder, "Files", onClick = onFiles)
                ToolCard(
                    Modifier.weight(1f),
                    if (isRecording) Icons.Outlined.StopCircle else Icons.Outlined.Videocam,
                    if (isRecording) "Stop" else "Record",
                    onClick = onToggleRecord,
                    isDestructive = isRecording
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard(Modifier.weight(1f), Icons.Outlined.CameraAlt, "Screenshot", onClick = onScreenshot)
                ToolCard(Modifier.weight(1f), Icons.Outlined.DarkMode, "Screen\nSaver", onClick = onScreensaver)
                ToolCard(
                    Modifier.weight(1f),
                    Icons.Outlined.Upload,
                    if (isUploading) "Uploading" else "Upload",
                    onClick = onUpload
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("POWER & MAINTENANCE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.08.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolCard(Modifier.weight(1f), Icons.Outlined.CleaningServices, "Clear\nCache", onClick = { onQuickCommand("pm trim-caches 999999999999999") })
                ToolCard(Modifier.weight(1f), Icons.Outlined.NightsStay, "Sleep", onClick = { onQuickCommand("input keyevent 26") })
                ToolCard(Modifier.weight(1f), Icons.Outlined.RestartAlt, "Reboot", onClick = { onQuickCommand("reboot") })
                ToolCard(Modifier.weight(1f), Icons.Rounded.PowerSettingsNew, "Power\nOff", onClick = { onQuickCommand("reboot -p") }, isDestructive = true)
            }
        }

        AnimatedVisibility(visible = isRecording) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.RadioButtonChecked, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Text("Screen recording in progress…", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // Now Playing bar
        AnimatedVisibility(
            visible = mediaInfo != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            mediaInfo?.let { NowPlayingBar(it, onMediaKey) }
        }

        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                .clickable(onClick = onDisconnect)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Disconnect", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
        }
    }
}

// ─── Now Playing Bar ──────────────────────────────────────────────────────────

@Composable
private fun NowPlayingBar(
    mediaInfo: MediaInfo,
    onMediaKey: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (!mediaInfo.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = mediaInfo.artworkUrl,
                    contentDescription = "Now playing artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Title & artist
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = mediaInfo.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (mediaInfo.artist.isNotBlank()) {
                Text(
                    text = mediaInfo.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Play/Pause
        IconButton(
            onClick = { onMediaKey(85) }, // MEDIA_PLAY_PAUSE
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = if (mediaInfo.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (mediaInfo.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(Modifier.width(4.dp))

        // Next
        IconButton(
            onClick = { onMediaKey(87) }, // MEDIA_NEXT
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Rounded.SkipNext, "Next",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1f, label = "scale")
    
    val colorScheme = MaterialTheme.colorScheme
    val defaultBg = if (isDestructive) colorScheme.errorContainer else colorScheme.surface
    val pressedBg = if (isDestructive) colorScheme.errorContainer else colorScheme.secondaryContainer
    val defaultBorder = if (isDestructive) colorScheme.error.copy(alpha = 0.4f) else colorScheme.outline
    val pressedBorder = if (isDestructive) colorScheme.error.copy(alpha = 0.4f) else colorScheme.secondary
    val defaultTint = if (isDestructive) colorScheme.error else colorScheme.outlineVariant
    val pressedTint = if (isDestructive) colorScheme.error else colorScheme.primary

    val bgColor by animateColorAsState(if (isPressed) pressedBg else defaultBg, tween(200), label = "bg")
    val borderColor by animateColorAsState(if (isPressed) pressedBorder else defaultBorder, tween(200), label = "border")
    val tintColor by animateColorAsState(if (isPressed) pressedTint else defaultTint, tween(200), label = "tint")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Generic padding across all tools
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
        ) {
            Icon(icon, label, modifier = Modifier.size(22.dp), tint = tintColor)
            Spacer(modifier = Modifier.height(10.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tintColor, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}
