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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
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
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
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

    var descriptions by remember(profileId) { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(profileId, remoteUrl, uiState.updatedProfileId, uiState.isLoading) {
        if (remoteUrl.isNotEmpty()) {
            descriptions = withContext(Dispatchers.IO) { readDescriptions(remoteUrl) }
        }
    }

    val group = groupsState.groups.firstOrNull { it.selectable }
    val running = serviceStatus == Status.Started
    val baseRows = if (running && group != null) {
        group.items.map { LocationRow(it.tag, it.displayType, it.urlTestDelay, it.tag == group.selected, descriptions[it.tag].orEmpty()) }
    } else {
        offlineNames.map { LocationRow(it, "", null, false, descriptions[it].orEmpty()) }
    }
    val allRows = if (query.isBlank()) baseRows else baseRows.filter {
        it.tag.contains(query.trim(), ignoreCase = true) || it.description.contains(query.trim(), ignoreCase = true)
    }
    // The autopilot is the first entry: it stays on top, the rest can be sorted by ping, and a
    // collapsed list shows only the autopilot (like the old app).
    var collapsed by remember { mutableStateOf(false) }
    var sortByPing by remember { mutableStateOf(false) }
    val first = allRows.firstOrNull()
    val rest = allRows.drop(1).let { others ->
        if (sortByPing) others.sortedBy { r -> r.delay?.takeIf { it in 1..65534 } ?: Int.MAX_VALUE } else others
    }
    val rows = if (first == null) emptyList() else if (collapsed && query.isBlank()) listOf(first) else listOf(first) + rest
    val context = LocalContext.current
    val openLink: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = {
                if (searchOpen) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Поиск") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                } else {
                    Text("🏔 UralVPN", fontWeight = FontWeight.Bold)
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (searchOpen) {
                        searchOpen = false
                        query = ""
                    } else {
                        drawer?.let { scope.launch { it.open() } }
                    }
                }) {
                    Icon(
                        imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Menu,
                        contentDescription = if (searchOpen) "Закрыть поиск" else "Меню",
                    )
                }
            },
            actions = {
                if (!searchOpen) {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                }
                if (profile != null && remoteUrl.isNotEmpty()) {
                    IconButton(onClick = {
                        dashboardViewModel.updateProfile(profile)
                        refreshTick++
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Обновить подписку") }
                }
                IconButton(onClick = { onOpenNewProfile(NewProfileArgs()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить подписку")
                }
            },
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
                    onToggleSort = { sortByPing = !sortByPing },
                )
                Spacer(Modifier.height(8.dp))
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
            UralLocationRow(row, enabled = running && group != null) {
                if (group != null) groupsViewModel.selectGroupItem(group.tag, row.tag)
            }
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 10.dp)) {
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
private fun UralLocationRow(row: LocationRow, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalStatusColors.current
    val container = if (row.selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The leading emoji of the name (the flag) is drawn large over both lines, like in the old app.
        val flagEnd = row.tag.indexOf(' ').let { if (it > 0) it else 0 }
        val head = row.tag.substring(0, flagEnd)
        val hasFlag = head.isNotEmpty() && head.none { it.isLetterOrDigit() }
        if (hasFlag) {
            Text(
                text = head,
                fontSize = 34.sp,
                modifier = Modifier.width(58.dp),
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
