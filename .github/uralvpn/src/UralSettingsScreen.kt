package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.UralTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.nekohasekai.sfa.bg.UralBackup
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Shield
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar

// UralVPN: short settings list like in the old app (the SFA tools for Tailscale, remote control,
// privileges and the core tuning are not shown). Autostart, updates and notifications are in the side menu.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text("Настройки") },
        )
    }

    val context = LocalContext.current
    var simBlock by remember { mutableStateOf(UralTools.simulateBlock) }
    var killSwitch by remember { mutableStateOf(UralTools.killSwitch) }
    var devMode by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableStateOf(0) }
    var alwaysOn by remember { mutableStateOf(false to false) }
    var dnsMode by remember { mutableStateOf(UralTools.dnsMode) }
    var dnsCustom by remember { mutableStateOf(UralTools.dnsCustom) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var routingTarget by remember { mutableStateOf<String?>(null) }
    var routingText by remember { mutableStateOf("") }
    var batteryIgnored by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val batteryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) {
        batteryIgnored = io.nekohasekai.sfa.bg.UralTools.batteryOptimizationIgnored(context)
    }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.IO) {
                        val data = UralBackup.build().toByteArray()
                        val out = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IllegalStateException("нет доступа к выбранному файлу")
                        out.use { it.write(data) }
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: data.size.toLong()
                    }
                    if (bytes <= 0) throw IllegalStateException("файл не записался")
                    UralTools.toast("Резервная копия сохранена (${bytes} байт)")
                }.onFailure { UralTools.toast("Не удалось сохранить: ${it.message}") }
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (text.isBlank()) throw IllegalArgumentException("файл пустой или недоступен")
                    val added = withContext(Dispatchers.IO) { UralBackup.restore(text) }
                    UralTools.toast("Восстановлено профилей: $added")
                }.onFailure { UralTools.toast("Не удалось восстановить: ${it.message}") }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        batteryIgnored = io.nekohasekai.sfa.bg.UralTools.batteryOptimizationIgnored(context)
        alwaysOn = UralTools.alwaysOnState(context)
    }
    val scaffoldPadding = LocalScaffoldPadding.current

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 8.dp,
            ),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column {
                if (devMode) {
                ListItem(
                    headlineContent = { Text("Имитировать блокировку прямых", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Проверка автопилота: прямые серверы отключаются, работают только CDN. После проверки выключите.") },
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    leadingContent = {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Switch(
                            checked = simBlock,
                            onCheckedChange = { value ->
                                simBlock = value
                                UralTools.simulateBlock = value
                                UralTools.toast(if (value) "Блокировка включена, обновляю подписку…" else "Блокировка выключена, обновляю подписку…")
                                scope.launch {
                                    val ok = UralTools.refreshNow()
                                    UralTools.toast(if (ok) "Готово. Отключите и снова включите VPN." else "Не удалось обновить подписку: проверьте сеть")
                                }
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                }
                ListItem(
                    headlineContent = { Text("Оптимизация батареи", style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.clip(if (devMode) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    supportingContent = {
                        Text(if (batteryIgnored) "Разрешение выдано: система не усыпляет VPN" else "Разрешите работу в фоне, иначе Android может отключать VPN")
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        if (!batteryIgnored) {
                            androidx.compose.material3.TextButton(onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    batteryLauncher.launch(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            android.net.Uri.parse("package:" + context.packageName),
                                        ),
                                    )
                                }
                            }) { Text("Разрешить") }
                        } else {
                            Text("Включено", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    headlineContent = { Text("Защита от утечек (kill switch)", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text(
                            if (alwaysOn.first && alwaysOn.second) {
                                "Системная защита включена: без VPN интернет заблокирован"
                            } else {
                                "Спрашивать подтверждение перед отключением VPN. Для полной защиты включите в настройках Android «Постоянная VPN» и «Блокировать соединения без VPN»"
                            },
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Switch(
                            checked = killSwitch,
                            onCheckedChange = { value ->
                                killSwitch = value
                                UralTools.killSwitch = value
                            },
                        )
                    },
                    modifier = Modifier.clickable { UralTools.openVpnSettings(context) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    headlineContent = { Text("Постоянная VPN (Always-On)", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text(
                            when {
                                alwaysOn.first && alwaysOn.second ->
                                    "Включена: Android сам поднимает VPN, без него интернет заблокирован"
                                alwaysOn.first ->
                                    "Включена: Android сам поднимает VPN после сбоя и перезагрузки"
                                else ->
                                    "Пусть Android поднимает VPN сам. Включите «Постоянная VPN» и, при желании, «Блокировать соединения без VPN»"
                            },
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        TextButton(onClick = { UralTools.openVpnSettings(context) }) { Text("Настроить") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    headlineContent = { Text("Приложения (обход VPN)", style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = {
                        Icon(Icons.Outlined.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clickable { navController.navigate("settings/profile_override") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        ListItem(
            headlineContent = { Text("DNS", style = MaterialTheme.typography.bodyLarge) },
            supportingContent = {
                Text(
                    when (dnsMode) {
                        "google" -> "Google 8.8.8.8"
                        "quad9" -> "Quad9 9.9.9.9"
                        "adguard" -> "AdGuard с блокировкой рекламы"
                        "custom" -> dnsCustom.ifEmpty { "свой сервер" }
                        else -> "Cloudflare 1.1.1.1 (по умолчанию)"
                    },
                )
            },
            leadingContent = {
                Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).clickable { showDnsDialog = true },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        if (showDnsDialog) {
            AlertDialog(
                onDismissRequest = { showDnsDialog = false },
                title = { Text("DNS для иностранных сайтов") },
                text = {
                    Column {
                        listOf(
                            "cloudflare" to "Cloudflare (1.1.1.1)",
                            "google" to "Google (8.8.8.8)",
                            "quad9" to "Quad9 (9.9.9.9)",
                            "adguard" to "AdGuard — с блокировкой рекламы",
                            "custom" to "Свой сервер",
                        ).forEach { (value, label) ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth().clickable { dnsMode = value },
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = dnsMode == value, onClick = { dnsMode = value })
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (dnsMode == "custom") {
                            OutlinedTextField(
                                value = dnsCustom,
                                onValueChange = { dnsCustom = it },
                                label = { Text("Адрес (1.1.1.1, tls://dns.example, https://…)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        UralTools.dnsMode = dnsMode
                        UralTools.dnsCustom = dnsCustom
                        showDnsDialog = false
                        scope.launch {
                            val ok = UralTools.refreshNow()
                            UralTools.toast(if (ok) "DNS сохранён, обновите подписку" else "DNS сохранён")
                        }
                    }) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { showDnsDialog = false }) { Text("Отмена") } },
            )
        }

        ListItem(
            headlineContent = { Text("Маршрутизация", style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text("Свои сайты напрямую или всегда через VPN") },
            leadingContent = {
                Icon(Icons.Outlined.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).clickable {
                routingTarget = "direct"
                routingText = UralTools.directDomains
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        if (routingTarget != null) {
            val target = routingTarget!!
            val title = if (target == "direct") "Сайты напрямую" else "Сайты только через VPN"
            AlertDialog(
                onDismissRequest = { routingTarget = null },
                title = { Text(title) },
                text = {
                    Column {
                        Text("По одному домену в строке, например: example.com", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = routingText,
                            onValueChange = { routingText = it },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        TextButton(onClick = {
                            routingTarget = if (target == "direct") "proxy" else "direct"
                            routingText = if (routingTarget == "direct") UralTools.directDomains else UralTools.proxyDomains
                        }) {
                            Text(if (target == "direct") "Указать сайты только через VPN →" else "← Указать сайты напрямую")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (target == "direct") UralTools.directDomains = routingText else UralTools.proxyDomains = routingText
                        routingTarget = null
                        scope.launch {
                            val ok = UralTools.refreshNow()
                            UralTools.toast(if (ok) "Сохранено, подписка обновлена" else "Сохранено")
                        }
                    }) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { routingTarget = null }) { Text("Отмена") } },
            )
        }

        Text(
            text = "Резервная копия",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Сохранить копию", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Подписки и настройки в один файл — пригодится после переустановки") },
                    leadingContent = {
                        Icon(Icons.Outlined.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable { exportLauncher.launch("uralvpn-backup.json") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    headlineContent = { Text("Восстановить из копии", style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = {
                        Icon(Icons.Outlined.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clickable { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Text(
            text = "О программе",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("🏔UralVPN", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Версия ${BuildConfig.VERSION_NAME}") },
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable {
                            versionTaps += 1
                            if (versionTaps >= 7) {
                                devMode = true
                                UralTools.toast("Режим разработчика включён")
                            }
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    headlineContent = { Text("Исходный код", style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = {
                        Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data = android.net.Uri.parse("https://github.com/figyra42-prog/uralvpn-android")
                            context.startActivity(intent)
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
