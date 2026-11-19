package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// UralVPN: side menu like in the old app (burger on the home screen) instead of the bottom tabs.
val LocalUralDrawer = staticCompositionLocalOf<DrawerState?> { null }

// "Add subscription" in the side menu opens the same menu as the "+" button on the home screen.
object UralAddRequest {
    var pending by mutableStateOf(false)
}

private const val SITE_URL = "https://servisbot.staryi.org"

private data class DrawerEntry(val icon: ImageVector, val label: String, val route: String)

// the entries above the autostart switch and below it
private val drawerTop = listOf(
    DrawerEntry(Icons.Default.Home, "Главная", Screen.Dashboard.route),
    DrawerEntry(Icons.Default.Add, "Добавить подписку", "ural/add"),
    DrawerEntry(Icons.Default.Apps, "Приложения (обход VPN)", "settings/profile_override"),
    DrawerEntry(Icons.Default.Description, "Логи", Screen.Log.route),
    DrawerEntry(Icons.Default.Speed, "Статистика", "ural/stats"),
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
    var showSub by remember { mutableStateOf(false) }
    var subLinks by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var subFailed by remember { mutableStateOf<String?>(null) }
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
                    // the link of the subscription (to copy it or send it to another device)
                    NavigationDrawerItem(
                        label = { Text("Моя подписка") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                // the profile database must be read off the main thread
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        ProfileManager.list()
                                            .filter { it.typed.remoteURL.isNotBlank() } // the same condition as the home screen
                                            .map { it.name to it.typed.remoteURL }
                                    }
                                }
                                subFailed = result.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message }
                                subLinks = result.getOrDefault(emptyList())
                                showSub = true
                            }
                        },
                        icon = { Icon(Icons.Default.Link, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
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
                    // the UralVPN site (personal account, tariffs, apps)
                    NavigationDrawerItem(
                        label = { Text("Сайт UralVPN") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SITE_URL))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        icon = { Icon(Icons.Default.Language, contentDescription = null) },
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
    if (showSub) {
        AlertDialog(
            onDismissRequest = { showSub = false },
            title = { Text("Моя подписка") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (subFailed != null) {
                        Text("Не удалось прочитать список подписок: $subFailed")
                    } else if (subLinks.isEmpty()) {
                        Text("Подписка ещё не добавлена. Нажмите «Добавить подписку» в меню.")
                    }
                    subLinks.forEachIndexed { index, (name, url) ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        if (subLinks.size > 1) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                        }
                        SelectionContainer {
                            Text(url, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            TextButton(onClick = {
                                runCatching {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UralVPN", url))
                                    android.widget.Toast.makeText(context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Копировать") }
                            TextButton(onClick = {
                                runCatching {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, url)
                                    context.startActivity(
                                        android.content.Intent.createChooser(send, "Поделиться ссылкой")
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }) { Text("Поделиться") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Не показывайте ссылку посторонним: по ней можно пользоваться вашей подпиской.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSub = false }) { Text("Закрыть") } },
        )
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
