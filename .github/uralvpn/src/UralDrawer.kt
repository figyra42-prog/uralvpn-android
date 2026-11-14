package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.UralAutostart
import io.nekohasekai.sfa.compose.navigation.Screen
import io.nekohasekai.sfa.update.UpdateState
import kotlinx.coroutines.launch

// UralVPN: side menu like in the old app (burger on the home screen) instead of the bottom tabs.
val LocalUralDrawer = staticCompositionLocalOf<DrawerState?> { null }

// "Add subscription" in the side menu opens the same menu as the "+" button on the home screen.
object UralAddRequest {
    var pending by mutableStateOf(false)
}

private data class DrawerEntry(val icon: ImageVector, val label: String, val route: String)

// the entries above the autostart switch and below it
private val drawerTop = listOf(
    DrawerEntry(Icons.Default.Home, "Главная", Screen.Dashboard.route),
    DrawerEntry(Icons.Default.Add, "Добавить подписку", "ural/add"),
    DrawerEntry(Icons.Default.Apps, "Приложения (обход VPN)", "settings/profile_override"),
    DrawerEntry(Icons.Default.Description, "Логи", Screen.Log.route),
    DrawerEntry(Icons.Default.Settings, "Настройки", Screen.Settings.route),
)

@Composable
fun UralDrawerHost(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var autostart by remember { mutableStateOf(UralAutostart.enabled) }
    val hasUpdate by UpdateState.hasUpdate
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.75f)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(36.dp))
                    Text(
                        text = "🏔UralVPN",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    drawerTop.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                when (entry.route) {
                                    "ural/add" -> {
                                        onNavigate(Screen.Dashboard.route)
                                        UralAddRequest.pending = true
                                    }
                                    else -> onNavigate(entry.route)
                                }
                            },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                    // autostart: a switch right in the menu (the VPN starts by itself after the phone restarts)
                    NavigationDrawerItem(
                        label = { Text("Автозапуск") },
                        selected = false,
                        onClick = {
                            autostart = !autostart
                            UralAutostart.enabled = autostart
                        },
                        icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) },
                        badge = {
                            Switch(
                                checked = autostart,
                                onCheckedChange = {
                                    autostart = it
                                    UralAutostart.enabled = it
                                },
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    // updates: the screen with the update settings (the dot shows a new version)
                    NavigationDrawerItem(
                        label = { Text("Обновления") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigate("settings/app")
                        },
                        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                        badge = if (hasUpdate) {
                            { Badge(containerColor = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    // notifications: the system settings of the app (includes the warning about the end of the subscription)
                    NavigationDrawerItem(
                        label = { Text("Уведомления") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    NavigationDrawerItem(
                        label = { Text("О программе") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showAbout = true
                        },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    Spacer(Modifier.height(16.dp))
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
