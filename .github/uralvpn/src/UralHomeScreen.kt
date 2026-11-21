package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.bg.UralTools
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocationRow(val tag: String, val type: String, val delay: Int?, val selected: Boolean, val description: String = "")

// UralVPN home screen in the style of the old uralvpn-app: big connect button, status line,
// subscription card (traffic, expiry, panel announcement) and the list of locations.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    LaunchedEffect(uiState.profiles.size) { io.nekohasekai.sfa.bg.UralTools.ensureAutoUpdate() }
    var refreshTick by remember { mutableIntStateOf(0) }
    var info by remember(profileId) { mutableStateOf(profileId?.let { SubInfoCache.get(it) }) }
    LaunchedEffect(profileId, remoteUrl, uiState.updatedProfileId, refreshTick) {
        if (profileId != null && remoteUrl.isNotEmpty()) {
            // a failed request (network is not ready right after the start) is repeated a few times
            for (delayMs in longArrayOf(0, 2000, 5000, 15000, 30000)) {
                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                val fetched = withContext(Dispatchers.IO) { fetchSubInfo(remoteUrl) }
                if (fetched != null) {
                    SubInfoCache.put(profileId, fetched)
                    info = fetched
                    break
                }
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
            descriptions = withContext(Dispatchers.IO) { readDescriptions(remoteUrl).mapKeys { it.key.trim() } }
            subStatus = withContext(Dispatchers.IO) { readStatus(remoteUrl) }
        }
    }

    val others = uiState.profiles.filter { it.id != profileId }
    val otherInfo = remember { mutableStateMapOf<Long, SubInfo>() }
    LaunchedEffect(others.map { it.id }, uiState.updatedProfileId) {
        others.forEach { p ->
            val url = p.typed.remoteURL
            if (url.isNotEmpty()) {
                SubInfoCache.get(p.id)?.let { otherInfo[p.id] = it }
                withContext(Dispatchers.IO) { fetchSubInfo(url) }?.let {
                    SubInfoCache.put(p.id, it)
                    otherInfo[p.id] = it
                }
            }
        }
    }

    val group = groupsState.groups.firstOrNull { it.selectable }
    // The test itself can finish within one frame: keep the indicator on screen for a moment,
    // otherwise the user never sees that a check ran.
    var pingVisible by remember { mutableStateOf(false) }
    val testing = pingVisible || (group != null && group.tag in groupsState.testingGroups)
    val running = serviceStatus == Status.Started
    val baseRows = if (running && group != null) {
        group.items.map { LocationRow(it.tag, it.displayType, it.urlTestDelay, it.tag == group.selected, descriptions[it.tag.trim()].orEmpty()) }
    } else {
        offlineNames.map { LocationRow(it, "", null, false, descriptions[it.trim()].orEmpty()) }
    }
    val allRows = baseRows
    // The autopilot is the first entry: it stays on top, the rest can be sorted by ping, and a
    // collapsed list shows only the autopilot (like the old app).
    var collapsed by remember { mutableStateOf(false) }
    val uiPrefs = remember { io.nekohasekai.sfa.Application.application.getSharedPreferences("ural_ui", android.content.Context.MODE_PRIVATE) }
    var sortByPing by remember { mutableStateOf(uiPrefs.getBoolean("sort_by_ping", false)) }
    var showKillDialog by remember { mutableStateOf(false) }
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

    val listState = rememberLazyListState()
    var panelBottom by remember { mutableFloatStateOf(0f) }
    val scaffoldPadding = LocalScaffoldPadding.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = if (showStatusBar) 84.dp else 24.dp),
    ) {
        item(key = "connect") {
            UralConnectButton(
                isOn = serviceStatus == Status.Started || serviceStatus == Status.Starting,
                startTime = uiState.serviceStartTime,
                onClick = {
                    if (profile == null) {
                        onOpenNewProfile(NewProfileArgs())
                    } else if (
                        UralTools.killSwitch &&
                        (serviceStatus == Status.Started || serviceStatus == Status.Starting)
                    ) {
                        showKillDialog = true
                    } else {
                        dashboardViewModel.toggleService()
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
            if (showKillDialog) {
                AlertDialog(
                    onDismissRequest = { showKillDialog = false },
                    title = { Text("Отключить VPN?") },
                    text = { Text("Включена защита от утечек: после отключения трафик пойдёт напрямую, без VPN.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showKillDialog = false
                            dashboardViewModel.toggleService()
                        }) { Text("Отключить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showKillDialog = false }) { Text("Отмена") }
                    },
                )
            }
            val statusText = if (testing) {
                // The ping test is visible on the button too, like in Happ.
                "Идёт пинг"
            } else when (serviceStatus) {
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
            stickyHeader(key = "card") {
                UralSubscriptionCard(
                    info = value,
                    fallbackTitle = profile?.name.orEmpty(),
                    collapsed = collapsed,
                    sortByPing = sortByPing,
                    pingEnabled = running && group != null,
                    testing = testing,
                    onToggleCollapse = { collapsed = !collapsed },
                    onOpenLink = openLink,
                    onPing = { group?.let { current ->
                        pingVisible = true
                        groupsViewModel.urlTestGroup(current.tag)
                        scope.launch {
                            delay(1500)
                            pingVisible = false
                        }
                    } },
                    onToggleSort = {
                        sortByPing = !sortByPing
                        uiPrefs.edit().putBoolean("sort_by_ping", sortByPing).apply()
                    },
                )
                Spacer(Modifier.height(8.dp).onGloballyPositioned { panelBottom = it.positionInWindow().y + it.size.height })
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
            val under = remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        val top = coords.positionInWindow().y
                        val height = coords.size.height.toFloat().coerceAtLeast(1f)
                        under.floatValue = ((panelBottom - top) / height).coerceIn(0f, 1f)
                    }
                    .graphicsLayer {
                        val p = under.floatValue
                        scaleX = 1f - 0.12f * p
                        scaleY = 1f - 0.12f * p
                        alpha = (1f - 0.9f * p).coerceAtLeast(0.05f)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            ) {
            UralLocationRow(
                row,
                enabled = running && group != null,
                testing = testing,
            ) {
                if (group != null) groupsViewModel.selectGroupItem(group.tag, row.tag)
            }
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
private fun UralConnectButton(isOn: Boolean, startTime: Long?, onClick: () -> Unit) {
    // Как в версии 1.1.0: двойной контур, лаймовое свечение при подключении.
    // Выпуклость: центр светлее, к внешнему краю темнеет. Много промежуточных оттенков,
    // чтобы переход был плавным и без видимых ступенек. Центр градиента — по умолчанию (середина круга).
    val brush = if (isOn) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFB2CC77),
                0.25f to Color(0xFFA6C068),
                0.50f to Color(0xFF92AC4F),
                0.70f to Color(0xFF728F31),
                0.85f to Color(0xFF54731B),
                0.95f to Color(0xFF3C590B),
                1.00f to Color(0xFF2F4806),
            ),
        )
    } else {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF75737F),
                0.25f to Color(0xFF6A6874),
                0.50f to Color(0xFF5A5864),
                0.70f to Color(0xFF44434D),
                0.85f to Color(0xFF2D2C35),
                0.95f to Color(0xFF18171E),
                1.00f to Color(0xFF06060B),
            ),
        )
    }
    val glow = if (isOn) UralLime.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.60f)
    // Время подключения: настоящий момент запуска сервиса, тикает раз в секунду.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startTime, isOn) {
        now = System.currentTimeMillis()
        while (isOn) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val elapsedSeconds = if (isOn && startTime != null) ((now - startTime) / 1000).coerceAtLeast(0) else 0
    val elapsedText = run {
        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val sec = elapsedSeconds % 60
        if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }
    // Тёмный текст внутри кнопки в обоих состояниях: лаймовая поверхность светлая в любой теме.
    val textColor = if (isOn) Color(0xFF1C2A00) else Color(0xFFE2E0EA)
    // Внешнее кольцо: у внутреннего края темнее, к наружному — светлее.
    val ringBase = MaterialTheme.colorScheme.surfaceContainerHighest
    val ringDark = lerp(ringBase, Color.Black, 0.45f)
    val ringMid = lerp(ringBase, Color.Black, 0.15f)
    val ringLight = lerp(ringBase, Color(0xFFFFFFFF), 0.10f)
    Box(modifier = Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(196.dp)
                .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to ringDark,
                            0.70f to ringDark,
                            0.88f to ringMid,
                            1.00f to ringLight,
                        ),
                    ),
                )
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
            if (isOn) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UralDeepIcon(tint = textColor, sizeDp = 44, contentDescription = "Отключить")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Подключён",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                    if (startTime != null) {
                        Text(
                            text = elapsedText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.85f),
                        )
                    }
                }
            } else {
                UralDeepIcon(tint = textColor, sizeDp = 72, contentDescription = "Подключить")
            }
        }
    }
}

// Значок «утоплен» в кнопку: тёмная тень сверху-слева и светлый отблеск снизу-справа.
@Composable
private fun UralDeepIcon(tint: Color, sizeDp: Int, contentDescription: String) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            tint = Color(0x73FFFFFF),
            modifier = Modifier.size(sizeDp.dp).offset(x = 1.5.dp, y = 2.dp),
        )
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            tint = Color(0x8C000000),
            modifier = Modifier.size(sizeDp.dp).offset(x = (-1).dp, y = (-1.5).dp),
        )
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(sizeDp.dp),
        )
    }
}

@Composable
private fun UralSubscriptionCard(
    info: SubInfo,
    fallbackTitle: String,
    collapsed: Boolean,
    sortByPing: Boolean,
    pingEnabled: Boolean,
    testing: Boolean = false,
    onToggleCollapse: () -> Unit,
    onOpenLink: (String) -> Unit,
    onPing: () -> Unit,
    onToggleSort: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 18.dp,
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
                IconButton(onClick = onPing, enabled = pingEnabled && !testing, modifier = Modifier.size(36.dp)) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = "Проверить пинг всех", modifier = Modifier.size(22.dp))
                    }
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
private fun UralLocationRow(row: LocationRow, enabled: Boolean, testing: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalStatusColors.current
    // Карточка локации: флаг в плитке, название с подписью, справа пинг.
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .shadow(
                elevation = 9.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.55f),
            )
            .clip(shape)
            .background(
                // непрозрачный фон: полупрозрачная заливка пропускала тень и давала полосу на светлой теме
                SolidColor(
                    if (row.selected) accent.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            .then(
                if (row.selected) Modifier.border(1.5.dp, accent.copy(alpha = 0.7f), shape)
                else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Флаг — картинка (если есть PNG), иначе эмодзи из названия.
        val flagEnd = row.tag.indexOfFirst { it.isLetterOrDigit() }.let { if (it > 0) it else 0 }
        val head = row.tag.substring(0, flagEnd).trim()
        val hasFlag = head.isNotEmpty()
        if (hasFlag) {
            val flagContext = LocalContext.current
            val flagCodes = head.codePoints().toArray()
            val flagRes = if (flagCodes.size == 2 && flagCodes.all { it in 0x1F1E6..0x1F1FF }) {
                val code = String(CharArray(2) { ('a'.code + flagCodes[it] - 0x1F1E6).toChar() })
                flagContext.resources.getIdentifier("flag_$code", "drawable", flagContext.packageName)
            } else {
                0
            }
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (flagRes != 0) {
                    Image(
                        painter = painterResource(flagRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(text = head, fontSize = 21.sp, lineHeight = 25.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = if (hasFlag) row.tag.substring(flagEnd).trim() else row.tag,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
            Spacer(Modifier.width(8.dp))
            if (testing) {
                // Happ-style: while the ping test runs, a location without an answer shows a spinner.
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else Text(
                text = when {
                    delay <= 0 -> "—"
                    delay >= 65535 -> "н/д"
                    else -> "$delay ms"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    delay <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    delay >= 65535 || delay >= 800 -> colors.bad
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
    Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x66000000)))

private fun raisedBrush(base: Color): Brush =
    Brush.verticalGradient(
        listOf(
            Color(
                red = base.red + (1f - base.red) * 0.12f,
                green = base.green + (1f - base.green) * 0.12f,
                blue = base.blue + (1f - base.blue) * 0.12f,
                alpha = base.alpha,
            ),
            Color(red = base.red * 0.88f, green = base.green * 0.88f, blue = base.blue * 0.88f, alpha = base.alpha),
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
