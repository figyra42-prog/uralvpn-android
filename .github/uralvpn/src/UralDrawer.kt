package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.compose.navigation.ProfileRoutes
import io.nekohasekai.sfa.compose.navigation.Screen
import kotlinx.coroutines.launch

// UralVPN: side menu like in the old app (burger on the home screen) instead of the bottom tabs.
val LocalUralDrawer = staticCompositionLocalOf<DrawerState?> { null }

private data class DrawerEntry(val icon: ImageVector, val label: String, val route: String)

private val drawerEntries = listOf(
    DrawerEntry(Icons.Default.Home, "Главная", Screen.Dashboard.route),
    DrawerEntry(Icons.Default.Add, "Добавить подписку", ProfileRoutes.NewProfile),
    DrawerEntry(Icons.Default.Apps, "Приложения", "settings/profile_override"),
    DrawerEntry(Icons.Default.Description, "Логи", Screen.Log.route),
    DrawerEntry(Icons.Default.Settings, "Настройки", Screen.Settings.route),
    DrawerEntry(Icons.Default.Info, "О программе", "ural/about"),
)

@Composable
fun UralDrawerHost(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.75f)) {
                Column {
                    Spacer(Modifier.height(36.dp))
                    Text(
                        text = "🏔UralVPN",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    drawerEntries.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (entry.route == "ural/about") showAbout = true else onNavigate(entry.route)
                            },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalUralDrawer provides drawerState) { content() }
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("🏔UralVPN") },
            text = { Text("Версия ${BuildConfig.VERSION_NAME}\nЯдро UralVPN: прямые серверы, при их недоступности — CDN.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } },
        )
    }
}
