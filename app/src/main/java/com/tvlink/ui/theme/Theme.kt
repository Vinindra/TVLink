package com.tvlink.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TVLinkDarkScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = Color(0xFF1E130F), // Dark contrasting color for text on primary
    primaryContainer = AccentBgMid,
    onPrimaryContainer = TextPrimary,
    
    secondary = AccentDim,
    onSecondary = Color(0xFF1E130F),
    secondaryContainer = AccentBg,
    onSecondaryContainer = TextPrimary,
    
    tertiary = StatusGreen,
    onTertiary = Color(0xFF1A3324),
    tertiaryContainer = StatusGreenBg,
    onTertiaryContainer = StatusGreen,
    
    background = BgPrimary,
    onBackground = TextPrimary,
    
    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextMuted,
    
    surfaceContainerLowest = BgPrimary,
    surfaceContainerLow = SurfacePrimary,
    surfaceContainer = SurfacePrimary,
    surfaceContainerHigh = SurfaceAlt,
    surfaceContainerHighest = BorderColor,
    
    error = StatusRed,
    onError = Color(0xFF1A0A0A),
    errorContainer = StatusRedBg,
    onErrorContainer = StatusRed,
    
    outline = BorderColor,
    outlineVariant = TextDim
)

private val TVLinkLightScheme = lightColorScheme(
    primary = AccentPrimaryLight,
    onPrimary = Color(0xFFFFFFFF), 
    primaryContainer = AccentBgMidLight,
    onPrimaryContainer = TextPrimaryLight,
    
    secondary = AccentDimLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = AccentBgLight,
    onSecondaryContainer = TextPrimaryLight,
    
    tertiary = StatusGreenLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = StatusGreenBgLight,
    onTertiaryContainer = StatusGreenLight,
    
    background = BgPrimaryLight,
    onBackground = TextPrimaryLight,
    
    surface = SurfacePrimaryLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceAltLight,
    onSurfaceVariant = TextMutedLight,
    
    surfaceContainerLowest = BgPrimaryLight,
    surfaceContainerLow = SurfacePrimaryLight,
    surfaceContainer = SurfacePrimaryLight,
    surfaceContainerHigh = SurfaceAltLight,
    surfaceContainerHighest = BorderColorLight,
    
    error = StatusRedLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = StatusRedBgLight,
    onErrorContainer = StatusRedLight,
    
    outline = BorderColorLight,
    outlineVariant = TextDimLight
)

@Composable
fun TVLinkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = if (darkTheme) TVLinkDarkScheme else TVLinkLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
