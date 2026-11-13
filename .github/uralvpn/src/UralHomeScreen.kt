package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compose.component.qr.QRScanSheet
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.screen.qrscan.QRScanResult
import io.nekohasekai.sfa.database.Profile
import androidx.compose.runtime.mutableStateMapOf
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.theme.LocalStatusColors
import io.nekohasekai.sfa.compose.theme.UralLime
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocationRow(val tag: String, val type: String, val delay: Int?, val selected: Boolean, val description: String = "")

// UralVPN home screen in the style of the old uralvpn-app: big connect button, status line,
// subscription card (traffic, expiry, panel announcement) and the list of locations.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UralHomeScreen(
    serviceStatus: Status,
    showStatusBar: Boolean,
    onOpenNewProfile: (NewProfileArgs) -> Unit,
    dashboardViewModel: DashboardViewModel,
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val drawer = LocalUralDrawer.current
    val scope = rememberCoroutineScope()
    var addMenuOpen by remember { mutableStateOf(false) }
    // the side menu item "Добавить подписку" opens this same menu
    LaunchedEffect(UralAddRequest.pending) {
        if (UralAddRequest.pending) {
            UralAddRequest.pending = false
            addMenuOpen = true
        }
    }
    var showScan by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    val groupsViewModel: GroupsViewModel = viewModel(
        key = "ural-home-groups",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupsViewModel(dashboardViewModel.commandClient) as T
        },
    )
    val groupsState by groupsViewModel.uiState.collectAsState()
    LaunchedEffect(serviceStatus) { groupsViewModel.updateServiceStatus(serviceStatus) }

    val profile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId } ?: uiState.profiles.firstOrNull()
    val profileId = profile?.id
    val remoteUrl = profile?.typed?.remoteURL.orEmpty()
    val profilePath = profile?.typed?.path.orEmpty()

    var refreshTick by remember { mutableIntStateOf(0) }
    var info by remember(profileId) { mutableStateOf(profileId?.let { SubInfoCache.map[it] }) }
    LaunchedEffect(profileId, remoteUrl, uiState.updatedProfileId, refreshTick) {
        if (profileId != null && remoteUrl.isNotEmpty()) {
            val fetched = withContext(Dispatchers.IO) { fetchSubInfo(remoteUrl) }
            if (fetched != null) {
                SubInfoCache.map[profileId] = fetched
                info = fetched
            }
        }
    }
    var offlineNames by remember(profileId) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(profileId, profilePath, uiState.updatedProfileId, uiState.isLoading) {
        if (profilePath.isNotEmpty()) {
            offlineNames = withContext(Dispatchers.IO) { readSelectorNames(profilePath) }
        }
    }

    // "Updated N locations" pop-up message after the subscription refresh (like in the old app).
    // A Toast disappears by itself (about 3.5 s); the key is not reset, so nothing cancels it.
    val toastContext = LocalContext.current
    LaunchedEffect(uiState.updatedProfileId) {
        val id = uiState.updatedProfileId ?: return@LaunchedEffect
        val path = uiState.profiles.firstOrNull { it.id == id }?.typed?.path.orEmpty()
        if (path.isEmpty()) return@LaunchedEffect
        val count = (withContext(Dispatchers.IO) { readSelectorNames(path) }.size - 1).coerceAtLeast(0)
        android.widget.Toast.makeText(toastContext, ural_updatedText(count), android.widget.Toast.LENGTH_LONG).show()
    }

    var descriptions by remember(profileId) { mutableStateOf(emptyMap<String, String>()) }
    var subStatus by remember(profileId) { mutableStateOf("") }
    LaunchedEffect(profileId, remoteUrl, uiState.updatedProfileId, uiState.isLoading) {
        if (remoteUrl.isNotEmpty()) {
            descriptions = withContext(Dispatchers.IO) { readDescriptions(remoteUrl) }
            subStatus = withContext(Dispatchers.IO) { readStatus(remoteUrl) }
        }
    }

    val others = uiState.profiles.filter { it.id != profileId }
    val otherInfo = remember { mutableStateMapOf<Long, SubInfo>() }
    LaunchedEffect(others.map { it.id }, uiState.updatedProfileId) {
        others.forEach { p ->
            val url = p.typed.remoteURL
            if (url.isNotEmpty()) {
                SubInfoCache.map[p.id]?.let { otherInfo[p.id] = it }
                withContext(Dispatchers.IO) { fetchSubInfo(url) }?.let {
                    SubInfoCache.map[p.id] = it
                    otherInfo[p.id] = it
                }
            }
        }
    }

    val group = groupsState.groups.firstOrNull { it.selectable }
    val running = serviceStatus == Status.Started
    val baseRows = if (running && group != null) {
        group.items.map { LocationRow(it.tag, it.displayType, it.urlTestDelay, it.tag == group.selected, descriptions[it.tag].orEmpty()) }
    } else {
        offlineNames.map { LocationRow(it, "", null, false, descriptions[it].orEmpty()) }
    }
    val allRows = baseRows
    // The autopilot is the first entry: it stays on top, the rest can be sorted by ping, and a
    // collapsed list shows only the autopilot (like the old app).
    var collapsed by remember { mutableStateOf(false) }
    val uiPrefs = remember { io.nekohasekai.sfa.Application.application.getSharedPreferences("ural_ui", android.content.Context.MODE_PRIVATE) }
    var sortByPing by remember { mutableStateOf(uiPrefs.getBoolean("sort_by_ping", false)) }
    val first = allRows.firstOrNull()
    val rest = allRows.drop(1).let { others ->
        if (sortByPing) others.sortedBy { r -> r.delay?.takeIf { it in 1..65534 } ?: Int.MAX_VALUE } else others
    }
    val rows = if (first == null) emptyList() else if (collapsed) listOf(first) else listOf(first) + rest
    val context = LocalContext.current
    val openLink: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text("🏔 UralVPN", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = { drawer?.let { scope.launch { it.open() } } }) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Меню")
                }
            },
            actions = {
                if (profile != null && remoteUrl.isNotEmpty()) {
                    IconButton(onClick = {
                        dashboardViewModel.updateProfile(profile)
                        refreshTick++
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Обновить подписку") }
                }
                Box {
                    IconButton(onClick = { addMenuOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить подписку")
                    }
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Вставить из буфера обмена") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                val link = readClipboardLink(context)
                                if (link == null) {
                                    android.widget.Toast.makeText(context, "В буфере обмена нет ссылки на подписку", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    onOpenNewProfile(NewProfileArgs(importName = "UralVPN", importUrl = link))
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Сканировать QR-код") },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                showScan = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Ввести ссылку вручную") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                onOpenNewProfile(NewProfileArgs(importName = "UralVPN", importUrl = ""))
                            },
                        )
                    }
                }
            },
        )
    }

    if (showScan) {
        QRScanSheet(
            onDismiss = { showScan = false },
            onScanResult = { result ->
                showScan = false
                when (result) {
                    is QRScanResult.RemoteProfile -> runCatching {
                        val parsed = Libbox.parseRemoteProfileImportLink(result.uri.toString())
                        onOpenNewProfile(NewProfileArgs(importName = parsed.name, importUrl = parsed.url))
                    }
                    is QRScanResult.QRSData -> onOpenNewProfile(NewProfileArgs(qrsData = result.data))
                }
            },
        )
    }
    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить подписку?") },
            text = { Text(otherInfo[target.id]?.title ?: target.name) },
            confirmButton = {
                TextButton(onClick = {
                    dashboardViewModel.deleteProfile(target)
                    toDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }

    val scaffoldPadding = LocalScaffoldPadding.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = if (showStatusBar) 84.dp else 24.dp),
    ) {
        item(key = "connect") {
            UralConnectButton(
                isOn = serviceStatus == Status.Started || serviceStatus == Status.Starting,
                onClick = {
                    if (profile == null) {
                        onOpenNewProfile(NewProfileArgs())
                    } else {
                        dashboardViewModel.toggleService()
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
            val statusText = when (serviceStatus) {
                Status.Started -> if (group != null && group.selected.isNotEmpty()) "Подключено · ${group.selected}" else "Подключено"
                Status.Starting -> "Подключение…"
                Status.Stopping -> "Отключение…"
                else -> if (profile == null) "Добавьте подписку" else "Отключено"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
        }
        info?.let { value ->
            item(key = "card") {
                UralSubscriptionCard(
                    info = value,
                    fallbackTitle = profile?.name.orEmpty(),
                    collapsed = collapsed,
                    sortByPing = sortByPing,
                    pingEnabled = running && group != null,
                    onToggleCollapse = { collapsed = !collapsed },
                    onOpenLink = openLink,
                    onPing = { group?.let { groupsViewModel.urlTestGroup(it.tag) } },
                    onToggleSort = {
                        sortByPing = !sortByPing
                        uiPrefs.edit().putBoolean("sort_by_ping", sortByPing).apply()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (subStatus.isNotEmpty()) {
            item(key = "status") {
                Text(
                    text = subStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = if (profile == null) "Нажмите «+» и вставьте ссылку на подписку" else "Список серверов появится после обновления подписки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(rows, key = { it.tag }) { row ->
            UralLocationRow(
                row,
                enabled = running && group != null,
            ) {
                if (group != null) groupsViewModel.selectGroupItem(group.tag, row.tag)
            }
        }
        items(others, key = { "profile-${it.id}" }) { other ->
            Spacer(Modifier.height(10.dp))
            UralOtherProfileCard(
                title = otherInfo[other.id]?.title ?: other.name,
                info = otherInfo[other.id],
                onSelect = { dashboardViewModel.selectProfile(other.id) },
                onDelete = { toDelete = other },
            )
        }
    }
}

@Composable
private fun UralConnectButton(isOn: Boolean, onClick: () -> Unit) {
    // Same look as the old app: double contour, lime glow when connected.
    val brush = if (isOn) {
        Brush.radialGradient(colors = listOf(Color(0xFFE8FF8C), Color(0xFF4E7A12)), center = Offset(0.5f, 0.5f))
    } else {
        Brush.radialGradient(colors = listOf(Color(0xFF7A7985), Color(0xFF050508)), center = Offset(0.5f, 0.5f))
    }
    val glow = if (isOn) UralLime.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.60f)
    Box(modifier = Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(196.dp)
                .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Box(
            Modifier
                .size(172.dp)
                .shadow(if (isOn) 22.dp else 16.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .clip(CircleShape)
                .background(brush)
                .then(if (isOn) Modifier else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (isOn) "Отключить" else "Подключить",
                tint = if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun UralSubscriptionCard(
    info: SubInfo,
    fallbackTitle: String,
    collapsed: Boolean,
    sortByPing: Boolean,
    pingEnabled: Boolean,
    onToggleCollapse: () -> Unit,
    onOpenLink: (String) -> Unit,
    onPing: () -> Unit,
    onToggleSort: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 9.dp,
        border = BorderStroke(1.dp, raisedRim()),
    ) {
        Column(Modifier.background(raisedBrush(MaterialTheme.colorScheme.surfaceContainerHighest)).padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleCollapse, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (collapsed) "Показать все локации" else "Свернуть до автопилота",
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = info.title ?: fallbackTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                info.supportUrl?.let { url ->
                    IconButton(onClick = { onOpenLink(url) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SupportAgent, contentDescription = "Поддержка", modifier = Modifier.size(22.dp))
                    }
                }
                info.webUrl?.let { url ->
                    IconButton(onClick = { onOpenLink(url) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Страница подписки", modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = onPing, enabled = pingEnabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = "Проверить пинг всех", modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onToggleSort, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Сортировать по пингу",
                        modifier = Modifier.size(22.dp),
                        tint = if (sortByPing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp)) {
                val low = (info.fraction ?: 0f) >= 0.9f
                val expired = info.expireSeconds > 0 && info.expireSeconds * 1000 <= System.currentTimeMillis()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Трафик: ${formatTraffic(info.used)} / ${if (info.total > 0) formatTraffic(info.total) else "∞"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "До: ${if (info.expireSeconds > 0) formatExpire(info.expireSeconds) else "∞"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                info.fraction?.let { fraction ->
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                info.announce?.let { announce ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = announce,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun UralLocationRow(row: LocationRow, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalStatusColors.current
    // every location is a slightly lighter card (like in Happ); the selected one is the accent color
    val container = if (row.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (row.selected) 0f else 0.35f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The leading emoji of the name (the flag) is drawn large over both lines, like in the old app.
        // The flag is everything before the first letter or digit (some names have no space after it).
        val flagEnd = row.tag.indexOfFirst { it.isLetterOrDigit() }.let { if (it > 0) it else 0 }
        val head = row.tag.substring(0, flagEnd).trim()
        val hasFlag = head.isNotEmpty()
        if (hasFlag) {
            Text(
                text = head,
                fontSize = 35.7.sp,
                modifier = Modifier.width(61.dp),
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = if (hasFlag) row.tag.substring(flagEnd).trim() else row.tag,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Normal,
                color = if (row.selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.description.isNotEmpty()) {
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val delay = row.delay
        if (delay != null) {
            val failed = delay >= 65535
            Text(
                text = if (delay <= 0) "—" else if (failed) "×" else "$delay ms",
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    delay <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    failed || delay >= 800 -> colors.bad
                    delay >= 400 -> colors.medium
                    else -> colors.good
                },
            )
        }
    }
}

// A link to a subscription from the clipboard (the first http(s) word), or null.
private fun readClipboardLink(context: android.content.Context): String? {
    val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    return text.trim().split(Regex("\\s+")).firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
}

// Another subscription: shown below the active one (like in Happ); a tap makes it the active one.
@Composable
private fun UralOtherProfileCard(modifier: Modifier = Modifier, title: String, info: SubInfo?, onSelect: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = if (info == null) {
                    "Нажмите, чтобы переключиться"
                } else {
                    "Трафик: ${formatTraffic(info.used)} / ${if (info.total > 0) formatTraffic(info.total) else "∞"}" +
                        if (info.expireSeconds > 0) " · до ${formatExpire(info.expireSeconds)}" else ""
                }
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить подписку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Volume of the subscription card: light top edge, darker bottom.
@Composable
private fun raisedRim(): Brush =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.25f)))

private fun raisedBrush(base: Color): Brush =
    Brush.verticalGradient(
        listOf(
            Color(
                red = base.red + (1f - base.red) * 0.07f,
                green = base.green + (1f - base.green) * 0.07f,
                blue = base.blue + (1f - base.blue) * 0.07f,
                alpha = base.alpha,
            ),
            Color(red = base.red * 0.93f, green = base.green * 0.93f, blue = base.blue * 0.93f, alpha = base.alpha),
        ),
    )

private fun ural_updatedText(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "Обновлена $n локация"
        m10 in 2..4 && m100 !in 12..14 -> "Обновлены $n локации"
        else -> "Обновлено $n локаций"
    }
}
