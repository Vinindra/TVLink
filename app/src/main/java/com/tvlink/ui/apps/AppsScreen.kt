package com.tvlink.ui.apps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tvlink.data.adb.model.InstallProgress
import com.tvlink.data.adb.model.InstalledApp
import com.tvlink.ui.components.ConnectionRequiredState

private enum class AppFilter(val label: String) {
    ALL("All"),
    USER("User"),
    SYSTEM("System")
}

@Composable
fun AppsScreen(
    viewModel: AppsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    var uninstallTarget by remember { mutableStateOf<InstalledApp?>(null) }
    var selectedFilter by remember { mutableStateOf(AppFilter.USER) }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.installApk(it) }
    }

    // Purely local — all filtering is client-side with no ViewModel side effects
    val filteredApps = remember(state.apps, selectedFilter) {
        when (selectedFilter) {
            AppFilter.ALL  -> state.apps
            AppFilter.USER -> state.apps.filter { !it.isSystemApp }
            AppFilter.SYSTEM -> state.apps.filter { it.isSystemApp }
        }
    }

    LaunchedEffect(state.installProgress) {
        when (val progress = state.installProgress) {
            is InstallProgress.Success -> {
                snackbarHostState.showSnackbar("APK installed successfully")
                viewModel.clearInstallProgress()
            }
            is InstallProgress.Failed -> {
                snackbarHostState.showSnackbar("Install failed: ${progress.reason}")
                viewModel.clearInstallProgress()
            }
            else -> { }
        }
    }

    uninstallTarget?.let { app ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("Uninstall ${app.label}?") },
            text = { Text("This will remove ${app.packageName} from the device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstallApp(app.packageName)
                    uninstallTarget = null
                }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        floatingActionButton = {
            val isInstalling = state.installProgress is InstallProgress.Installing
            if (state.isConnected && !isInstalling) {
                Row(
                    modifier = Modifier
                        .padding(end = 4.dp, bottom = 4.dp) // Extended FAB offset
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = { apkPicker.launch("application/vnd.android.package-archive") })
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, lineHeight = 20.sp)
                    Text(text = "Install APK", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !state.isConnected -> DisconnectedMessage("Connect to a TV to manage applications.")
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Fixed header: search + chips are pinned and never scroll
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val installProgress = state.installProgress
                            if (installProgress is InstallProgress.Installing) {
                                InstallProgressBanner(percent = installProgress.percent)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = state.filter,
                                    onValueChange = { viewModel.setFilter(it) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    ),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (state.filter.isEmpty()) {
                                            Text("Search apps...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                                        }
                                        innerTextField()
                                    }
                                )
                                if (state.filter.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp).clickable { viewModel.setFilter("") },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))

                            FilterChipRow(
                                selected = selectedFilter,
                                onSelect = { filter ->
                                    selectedFilter = filter
                                    val needSystem = filter != AppFilter.USER
                                    if (needSystem != state.showSystemApps) {
                                        viewModel.setShowSystemApps(needSystem)
                                    }
                                },
                                userCount = state.apps.count { !it.isSystemApp },
                                systemCount = state.apps.count { it.isSystemApp }
                            )
                        }

                        // Scrollable app list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 4.dp,
                                bottom = 88.dp
                            )
                        ) {
                            if (filteredApps.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No apps found",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            items(filteredApps, key = { it.packageName }) { app ->
                                AppCard(
                                    app = app,
                                    onLaunch = { viewModel.launchApp(app.packageName) },
                                    onUninstall = { uninstallTarget = app }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    selected: AppFilter,
    onSelect: (AppFilter) -> Unit,
    userCount: Int,
    systemCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppFilter.entries.forEach { filter ->
            val isSelected = selected == filter
            val count = when (filter) {
                AppFilter.ALL -> userCount + systemCount
                AppFilter.USER -> userCount
                AppFilter.SYSTEM -> systemCount
            }
            val label = "${filter.label} ($count)"
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(999.dp))
                    .clickable(onClick = { onSelect(filter) })
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            }
        }
    }
}

@Composable
private fun InstallProgressBanner(percent: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Installing APK…",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "$percent%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun DisconnectedMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ConnectionRequiredState(
            message = message,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

// Generate a deterministic color from a string (for app icon backgrounds)
private fun iconColorForPackage(packageName: String): Color {
    val colors = listOf(
        Color(0xFF6C5CE7), // Purple
        Color(0xFF0984E3), // Blue
        Color(0xFF00B894), // Green
        Color(0xFFE17055), // Orange
        Color(0xFFFD79A8), // Pink
        Color(0xFF00CEC9), // Teal
        Color(0xFFE84393), // Magenta
        Color(0xFF6AB04C), // Lime
    )
    val index = (packageName.hashCode() and 0x7FFFFFFF) % colors.size
    return colors[index]
}

@Composable
private fun AppCard(
    app: InstalledApp,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit
) {
    val iconColor = iconColorForPackage(app.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon with color-coded background
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.13f))
                .border(1.dp, iconColor.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(iconColor.copy(alpha = 0.53f)) // 0x88 is ~53%
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = app.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val greenColor = MaterialTheme.colorScheme.tertiary
            val redColor = MaterialTheme.colorScheme.error
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(greenColor.copy(alpha = 0.09f)) // 0x18 is roughly 0.09f
                    .border(1.dp, greenColor.copy(alpha = 0.27f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onLaunch),
                contentAlignment = Alignment.Center
            ) {
                // Play solid icon from compose
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Launch",
                    modifier = Modifier.size(16.dp),
                    tint = greenColor
                )
            }
            if (!app.isSystemApp) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(redColor.copy(alpha = 0.09f))
                        .border(1.dp, redColor.copy(alpha = 0.27f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onUninstall),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Uninstall",
                        modifier = Modifier.size(14.dp),
                        tint = redColor
                    )
                }
            }
        }
    }
}
