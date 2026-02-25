package com.tvlink.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Connect : Screen("connect")
    object Apps : Screen("apps")
    object Terminal : Screen("terminal")
    object Installer : Screen("installer")
    object Remote : Screen("remote")
    object Files : Screen("files")
    object DeviceInfo : Screen("device_info")
}

enum class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
) {
    Connect(Screen.Connect, "Connect", Icons.Rounded.Wifi),
    Apps(Screen.Apps, "Apps", Icons.Rounded.Apps),
    Terminal(Screen.Terminal, "Terminal", Icons.Rounded.Terminal),
    DeviceInfo(Screen.DeviceInfo, "Info", Icons.Rounded.Info)
}
