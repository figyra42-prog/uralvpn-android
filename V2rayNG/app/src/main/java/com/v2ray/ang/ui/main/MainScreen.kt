package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.QRCodeDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val selectedGuid = uiState.selectedGuid
    val selectedGroup = groups.firstOrNull { it.id == uiState.selectedGroupId }
        ?: groups.firstOrNull()
    val selectedGroupServersFlow: StateFlow<List<ServersCache>> =
        remember(selectedGroup?.id, mainViewModel) {
            selectedGroup?.let { mainViewModel.serversForGroup(it.id) }
                ?: MutableStateFlow(emptyList<ServersCache>())
        }
    val selectedGroupServers by selectedGroupServersFlow.collectAsStateWithLifecycle()
    val selectedServerName = selectedGroupServers
        .firstOrNull { it.guid == selectedGuid }
        ?.profile?.remarks
        ?.takeIf { it.isNotBlank() }
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val sortByPing = uiState.sortByPing
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showImportMenu by rememberSaveable { mutableStateOf(false) }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var showStopConfirm by rememberSaveable { mutableStateOf(false) }
    var updatedBannerText by remember { mutableStateOf<String?>(null) }
    val resources = LocalContext.current.resources
    LaunchedEffect(uiState.updatedLocationsEvent) {
        if (uiState.updatedLocationsEvent == 0L) return@LaunchedEffect
        val count = uiState.updatedLocationsCount
        if (count <= 0) return@LaunchedEffect
        updatedBannerText = resources.getQuantityString(R.plurals.main_updated_locations, count, count)
        delay(1500)
        updatedBannerText = null
    }
    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) showStopConfirm = false
    }

    val connectionHeader: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectButton(
                isRunning = isRunning,
                startTime = uiState.serviceStartTime,
                onClick = {
                    if (isRunning && uiState.killSwitch) {
                        showStopConfirm = true
                    } else {
                        onAction(MainAction.ToggleService)
                    }
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            val statusText = when (
                mainStatusTextKind(
                    isTesting = uiState.isTesting,
                    isRunning = isRunning,
                    hasGroups = groups.isNotEmpty(),
                    selectedServerName = selectedServerName,
                )
            ) {
                MainStatusText.Testing -> stringResource(R.string.main_status_testing)
                MainStatusText.ConnectedWith ->
                    stringResource(R.string.main_status_connected_with, selectedServerName.orEmpty())
                MainStatusText.Connected -> stringResource(R.string.main_status_connected)
                MainStatusText.NoSubscription -> stringResource(R.string.main_status_no_subscription)
                MainStatusText.Disconnected -> stringResource(R.string.main_status_disconnected)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isRunning) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.main_speed_format,
                        uiState.downlink.toSpeedString(),
                        uiState.uplink.toSpeedString()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (showStopConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_kill_switch_title),
            message = stringResource(R.string.dialog_kill_switch_message),
            confirmText = stringResource(R.string.action_disconnect),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { onAction(MainAction.ToggleService) },
            onDismiss = { showStopConfirm = false }
        )
    }

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                startOnBoot = uiState.startOnBoot,
                onStartOnBootChange = { onAction(MainAction.SetStartOnBoot(it)) },
                onAddSubscription = {
                    scope.launch { drawerState.close() }
                    showImportMenu = true
                },
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                MainTopBar(
                    isLoading = isLoading,
                    showImportMenu = showImportMenu,
                    onImportMenuChange = { showImportMenu = it },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onAction = onAction
                )
            },
            floatingActionButton = {},
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (selectedGroup != null) {
                    GroupPagerPage(
                        groupId = selectedGroup.id,
                        mainViewModel = mainViewModel,
                        selectedGuid = selectedGuid,
                        locateTarget = uiState.locateTarget,
                        doubleColumnDisplay = doubleColumnDisplay,
                        searchQuery = "",
                        lazyListStates = lazyListStates,
                        lazyGridStates = lazyGridStates,
                        onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                        onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                        onShareServer = { guid, profile ->
                            shareTarget = Triple(guid, profile, false)
                        },
                        onMoreServer = { guid, profile ->
                            shareTarget = Triple(guid, profile, true)
                        },
                        onRemoveServer = removeServer,
                        usageInfo = selectedGroup.usageInfo,
                        groupTitle = selectedGroup.remarks,
                        otherGroups = groups.filter { it.id != selectedGroup.id },
                        onSelectGroup = { onAction(MainAction.SelectGroup(it)) },
                        onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                        sortByPing = sortByPing,
                        onToggleSortByPing = { onAction(MainAction.ToggleSortByPing) },
                        isTesting = uiState.isTesting,
                        updatedBannerText = updatedBannerText,
                        connectionHeader = connectionHeader,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 0.dp,
                            end = 12.dp,
                            bottom = 80.dp
                        )
                    )
                } else {
                    connectionHeader()
                }
            }
        }
    }
}
