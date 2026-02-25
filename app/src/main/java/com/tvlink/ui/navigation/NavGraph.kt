package com.tvlink.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tvlink.ui.apps.AppsScreen
import com.tvlink.ui.connect.ConnectScreen
import com.tvlink.ui.connect.DeviceInfoScreen
import com.tvlink.ui.connect.FileBrowserScreen
import com.tvlink.ui.connect.RemoteScreen
import com.tvlink.ui.installer.InstallerScreen
import com.tvlink.ui.terminal.TerminalScreen

private val slideInFromRight = AnimatedContentTransitionScope.SlideDirection.Left
private val slideOutToRight = AnimatedContentTransitionScope.SlideDirection.Right

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Connect.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(120)) },
        exitTransition = { fadeOut(animationSpec = tween(120)) }
    ) {
        // ── Main tabs ────────────────────────────────────────────────────
        composable(route = Screen.Connect.route) {
            ConnectScreen(
                onNavigateToRemote = { navController.navigate(Screen.Remote.route) },
                onNavigateToFiles = { navController.navigate(Screen.Files.route) }
            )
        }

        composable(route = Screen.Apps.route) { AppsScreen() }
        composable(route = Screen.Terminal.route) { TerminalScreen() }
        composable(route = Screen.DeviceInfo.route) { DeviceInfoScreen(onBack = { navController.popBackStack() }) }
        composable(route = Screen.Installer.route) { InstallerScreen() }

        // ── Tool screens (slide in from right) ──────────────────────────
        composable(
            route = Screen.Remote.route,
            enterTransition = { slideIntoContainer(slideInFromRight, tween(180)) },
            exitTransition = { slideOutOfContainer(slideOutToRight, tween(180)) },
            popEnterTransition = { slideIntoContainer(slideOutToRight, tween(180)) },
            popExitTransition = { slideOutOfContainer(slideOutToRight, tween(180)) }
        ) { RemoteScreen(onBack = { navController.popBackStack() }) }

        composable(
            route = Screen.Files.route,
            enterTransition = { slideIntoContainer(slideInFromRight, tween(180)) },
            exitTransition = { slideOutOfContainer(slideOutToRight, tween(180)) },
            popEnterTransition = { slideIntoContainer(slideOutToRight, tween(180)) },
            popExitTransition = { slideOutOfContainer(slideOutToRight, tween(180)) }
        ) { FileBrowserScreen(onBack = { navController.popBackStack() }) }
    }
}
