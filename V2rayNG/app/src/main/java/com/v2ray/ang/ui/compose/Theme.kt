package com.v2ray.ang.ui.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF4E6B00), // Dark lime (brand --acc, readable on light)
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFE6FF9E), // Pale lime
    onPrimaryContainer = Color(0xFF1A2600), // Dark olive
    secondary = Color(0xFFB3178A), // Deep pink (brand --acc2)
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFFFD6F0), // Pale pink
    onSecondaryContainer = Color(0xFF3B0030), // Dark plum
    tertiary = Color(0xFF0059C8), // Blue
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFD5E3FF), // Light blue
    onTertiaryContainer = Color(0xFF001B3D), // Dark navy
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFAFAF8), // Off-white
    onBackground = Color(0xFF1B1A1F), // Near Black
    surface = Color(0xFFFAFAF8), // Off-white
    onSurface = Color(0xFF1B1A1F), // Near Black
    surfaceVariant = Color(0xFFE7E5EA), // Light Gray
    onSurfaceVariant = Color(0xFF49454F), // Dark Gray
    outline = Color(0xFF7A757F), // Medium Gray
    outlineVariant = Color(0xFFCAC4D0), // Light Gray
    inverseSurface = Color(0xFF313033), // Dark Gray
    inverseOnSurface = Color(0xFFF4EFF4), // Very Light Gray
    inversePrimary = Color(0xFFC6FF3D), // Lime
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF4E6B00), // Dark lime
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF7F7F7), // Very Light Gray
    surfaceContainer = Color(0xFFF1F1F1), // Light Gray
    surfaceContainerHigh = Color(0xFFEBEBEB), // Light Gray
    surfaceContainerHighest = Color(0xFFE5E5E5), // Light Gray
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFC6FF3D), // Lime (site --acc)
    onPrimary = Color(0xFF07101A), // Dark ink (site --acc-ink)
    primaryContainer = Color(0xFF2A3310), // Dim lime
    onPrimaryContainer = Color(0xFFE6FF9E), // Pale lime
    secondary = Color(0xFFFF3DC6), // Pink (site --acc2)
    onSecondary = Color(0xFF2B0022), // Dark plum
    secondaryContainer = Color(0xFF4A1038), // Plum
    onSecondaryContainer = Color(0xFFFFD6F0), // Pale pink
    tertiary = Color(0xFF5EA0FF), // Blue
    onTertiary = Color(0xFF001B3D), // Dark navy
    tertiaryContainer = Color(0xFF00317A), // Navy
    onTertiaryContainer = Color(0xFFCFE1FF), // Pale blue
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF07060B), // Near black (site bg)
    onBackground = Color(0xFFF2F2F7), // Light gray (site text)
    surface = Color(0xFF131217), // Dark surface
    onSurface = Color(0xFFF2F2F7), // Light gray
    surfaceVariant = Color(0xFF26252A), // Dark gray
    onSurfaceVariant = Color(0xFF98989D), // Medium gray
    outline = Color(0xFF69696E), // Gray
    outlineVariant = Color(0xFF26252A), // Dark gray
    inverseSurface = Color(0xFFF2F2F7), // Light gray
    inverseOnSurface = Color(0xFF07060B), // Near black
    inversePrimary = Color(0xFFC6FF3D), // Lime
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFC6FF3D), // Lime
    surfaceContainerLowest = Color(0xFF07060B), // Near black
    surfaceContainerLow = Color(0xFF0F0E13), // Near black
    surfaceContainer = Color(0xFF131217), // Dark surface
    surfaceContainerHigh = Color(0xFF1B1A1F), // Dark gray
    surfaceContainerHighest = Color(0xFF26252A), // Dark gray
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFFFF3DC6) // Pink (brand --acc2)
val colorFabActive = Color(0xFFC6FF3D) // Lime (brand --acc)
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColor else LightColor
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
