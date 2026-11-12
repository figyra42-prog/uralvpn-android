package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// UralVPN: color schemes copied from uralvpn-app (ui/compose/Theme.kt)
private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF4E6B00),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE6FF9E),
        onPrimaryContainer = Color(0xFF1A2600),
        secondary = Color(0xFFB3178A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD6F0),
        onSecondaryContainer = Color(0xFF3B0030),
        tertiary = Color(0xFF0059C8),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD5E3FF),
        onTertiaryContainer = Color(0xFF001B3D),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFAFAF8),
        onBackground = Color(0xFF1B1A1F),
        surface = Color(0xFFFAFAF8),
        onSurface = Color(0xFF1B1A1F),
        surfaceVariant = Color(0xFFE7E5EA),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF7A757F),
        outlineVariant = Color(0xFFCAC4D0),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF4EFF4),
        inversePrimary = Color(0xFFC6FF3D),
        scrim = Color(0xFF000000),
        surfaceTint = Color(0xFF4E6B00),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F7F7),
        surfaceContainer = Color(0xFFF1F1F1),
        surfaceContainerHigh = Color(0xFFEBEBEB),
        surfaceContainerHighest = Color(0xFFE5E5E5),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFC6FF3D),
        onPrimary = Color(0xFF07101A),
        primaryContainer = Color(0xFF2A3310),
        onPrimaryContainer = Color(0xFFE6FF9E),
        secondary = Color(0xFFFF3DC6),
        onSecondary = Color(0xFF2B0022),
        secondaryContainer = Color(0xFF4A1038),
        onSecondaryContainer = Color(0xFFFFD6F0),
        tertiary = Color(0xFF5EA0FF),
        onTertiary = Color(0xFF001B3D),
        tertiaryContainer = Color(0xFF00317A),
        onTertiaryContainer = Color(0xFFCFE1FF),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF07060B),
        onBackground = Color(0xFFF2F2F7),
        surface = Color(0xFF131217),
        onSurface = Color(0xFFF2F2F7),
        surfaceVariant = Color(0xFF26252A),
        onSurfaceVariant = Color(0xFF98989D),
        outline = Color(0xFF69696E),
        outlineVariant = Color(0xFF26252A),
        inverseSurface = Color(0xFFF2F2F7),
        inverseOnSurface = Color(0xFF07060B),
        inversePrimary = Color(0xFFC6FF3D),
        scrim = Color(0xFF000000),
        surfaceTint = Color(0xFFC6FF3D),
        surfaceContainerLowest = Color(0xFF07060B),
        surfaceContainerLow = Color(0xFF0F0E13),
        surfaceContainer = Color(0xFF131217),
        surfaceContainerHigh = Color(0xFF1B1A1F),
        surfaceContainerHighest = Color(0xFF26252A),
    )

@Immutable
data class StatusColors(
    val good: Color,
    val medium: Color,
    val bad: Color,
    val info: Color,
)

// UralVPN: ping colors from uralvpn-app (green / pink-red)
private val DarkStatusColors =
    StatusColors(
        good = Color(0xFFC6FF3D),
        medium = Color(0xFFD3A45E),
        bad = Color(0xFFFF0099),
        info = Color(0xFF5EA0FF),
    )

private val LightStatusColors =
    StatusColors(
        good = Color(0xFF009966),
        medium = Color(0xFFA8742F),
        bad = Color(0xFFFF0099),
        info = Color(0xFF0059C8),
    )

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // UralVPN: brand colors always, Material You dynamic color is ignored
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
