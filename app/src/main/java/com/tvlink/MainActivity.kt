package com.tvlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tvlink.ui.navigation.BottomNavItem
import com.tvlink.ui.navigation.NavGraph
import com.tvlink.ui.navigation.Screen
import com.tvlink.ui.settings.SettingsViewModel
import com.tvlink.ui.settings.ThemePickerSheet
import com.tvlink.ui.theme.TVLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()

            TVLinkTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TVLinkMainScreen(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TVLinkMainScreen(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showThemePicker by remember { mutableStateOf(false) }
    val themeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themeMode by settingsViewModel.themeMode.collectAsState()

    if (showThemePicker) {
        ThemePickerSheet(
            current = themeMode,
            sheetState = themeSheetState,
            onSelect = { settingsViewModel.setThemeMode(it) },
            onDismiss = { showThemePicker = false }
        )
    }

    // Bottom bar visible on main tab screens only
    val showBottomBar = currentRoute in listOf(
        Screen.Connect.route,
        Screen.Apps.route,
        Screen.Terminal.route,
        Screen.DeviceInfo.route
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showBottomBar) {
                TVLinkTopBar(onSettingsClick = { showThemePicker = true })
            }
        },
        bottomBar = {
            if (showBottomBar) {
                TVLinkBottomBar(
                    currentRoute = currentRoute,
                    onItemClick = { item ->
                        navController.navigate(item.screen.route) {
                            popUpTo(Screen.Connect.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun TVLinkTopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 52.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TVLink",
            style = MaterialTheme.typography.titleLarge.copy(
                brush = Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.primary)
                )
            ),
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            fontSize = 26.sp
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Theme Settings",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TVLinkBottomBar(
    currentRoute: String?,
    onItemClick: (BottomNavItem) -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidth
                )
            }
            .padding(top = 8.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem.entries.forEach { item ->
            val isSelected = currentRoute == item.screen.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { onItemClick(item) }
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 30.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(19.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
