package com.v2ray.ang.ui.main

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.pingGoodColor
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String, String) -> Unit,
    usageInfo: SubscriptionUsageInfo?,
    groupTitle: String,
    otherGroups: List<GroupMapItem>,
    onSelectGroup: (String) -> Unit,
    connectionHeader: @Composable () -> Unit,
    onOpenLink: (String) -> Unit,
    sortByPing: Boolean,
    onToggleSortByPing: () -> Unit,
    isTesting: Boolean,
    updatedBannerText: String?,
    contentPadding: PaddingValues
) {
    val groupStateFlow = remember(groupId) {
        mainViewModel.serverGroupState(groupId)
    }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    var collapsed by remember { mutableStateOf(false) }
    // Автопилот is the first server in the default (unsorted) subscription order. Pin it to the
    // top in both states; when collapsed, show only it.
    val autopilotGuid = groupState.servers.firstOrNull()?.guid
    val visibleRows = if (autopilotGuid == null) {
        groupState.rows
    } else {
        val (autopilotRows, otherRows) = groupState.rows.partition { it.guid == autopilotGuid }
        if (collapsed) autopilotRows else autopilotRows + otherRows
    }
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()
    val actions = remember(
        onSelectServer,
        onEditServer,
        onShareServer,
        onMoreServer,
        onRemoveServer,
    ) {
        ServerRowActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
        )
    }
    ServerListPage(
        rows = visibleRows,
        autopilotGuid = autopilotGuid,
        selectedGuid = selectedGuid,
        locateTarget = locateTarget?.takeIf { it.groupId == groupId },
        canReorder = canReorder,
        doubleColumnDisplay = doubleColumnDisplay,
        groupId = groupId,
        lazyListStates = lazyListStates,
        lazyGridStates = lazyGridStates,
        actions = actions,
        onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled) },
        onMoveServer = { fromIndex, toIndex ->
            mainViewModel.moveServer(groupId, fromIndex, toIndex)
        },
        topHeader = connectionHeader,
        isTesting = isTesting,
        updatedBannerText = updatedBannerText,
        footer = if (otherGroups.isEmpty()) null else {
            { OtherSubscriptions(otherGroups, onSelectGroup) }
        },
        header = if (usageInfo != null) {
            {
                SubscriptionInfoCard(
                    info = usageInfo,
                    fallbackTitle = groupTitle,
                    onOpenLink = onOpenLink,
                    onRefresh = { mainViewModel.onAction(MainAction.UpdateGroupSubscription(groupId)) },
                    onPing = { mainViewModel.onAction(MainAction.TestRealAllServers) },
                    sortByPing = sortByPing,
                    onToggleSortByPing = onToggleSortByPing,
                    collapsed = collapsed,
                    onToggleCollapse = { collapsed = !collapsed },
                )
            }
        } else null,
        contentPadding = contentPadding
    )
}

private class ServerRowActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem) -> Unit,
    val more: (String, ProfileItem) -> Unit,
    val remove: (String, String) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    autopilotGuid: String?,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    actions: ServerRowActions,
    onLocateHandled: () -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    topHeader: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    isTesting: Boolean,
    updatedBannerText: String?,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        LaunchedEffect(updatedBannerText) {
            if (updatedBannerText != null) gridState.animateScrollToItem(0)
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, gridState, onLocateHandled)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            if (updatedBannerText != null) {
                item(key = "updated-banner", span = { GridItemSpan(maxLineSpan) }) {
                    UpdatedBanner(updatedBannerText)
                }
            }
            topHeader?.let { headerContent ->
                item(key = "connection", span = { GridItemSpan(maxLineSpan) }) {
                    headerContent()
                }
            }
            header?.let { headerContent ->
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    headerContent()
                }
            }
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        isAutopilot = row.guid == autopilotGuid,
                        isTesting = isTesting,
                        doubleColumnDisplay = true,
                        actions = actions
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
            footer?.let { footerContent ->
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                    footerContent()
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        LaunchedEffect(updatedBannerText) {
            if (updatedBannerText != null) listState.animateScrollToItem(0)
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        var panelBottom by remember { mutableFloatStateOf(0f) }

        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            if (updatedBannerText != null) {
                item(key = "updated-banner") { UpdatedBanner(updatedBannerText) }
            }
            topHeader?.let { headerContent ->
                item(key = "connection") { headerContent() }
            }
            header?.let { headerContent ->
                stickyHeader(key = "header") {
                    Box(
                        Modifier.onGloballyPositioned { coords ->
                            panelBottom = coords.positionInWindow().y + coords.size.height
                        }
                    ) {
                        headerContent()
                    }
                }
            }
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            UnderSubscriptionCard(panelBottom) {
                                ServerItemRow(
                                    row = row,
                                    isSelected = row.guid == selectedGuid,
                                    isAutopilot = row.guid == autopilotGuid,
                                    isTesting = isTesting,
                                    actions = actions
                                )
                            }
                        }
                    }
                } else {
                    UnderSubscriptionCard(panelBottom) {
                        ServerItemRow(
                            row = row,
                            isSelected = row.guid == selectedGuid,
                            isAutopilot = row.guid == autopilotGuid,
                            isTesting = isTesting,
                            actions = actions
                        )
                    }
                }
            }
            footer?.let { footerContent ->
                item(key = "footer") { footerContent() }
            }
        }
    }
}

@Composable
private fun UpdatedBanner(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        )
    }
}

@Composable
private fun UnderSubscriptionCard(
    panelBottom: Float,
    content: @Composable () -> Unit,
) {
    var under by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val top = coords.positionInWindow().y
                val height = coords.size.height.toFloat().coerceAtLeast(1f)
                under = ((panelBottom - top) / height).coerceIn(0f, 1f)
            }
            .graphicsLayer {
                val p = under
                scaleX = 1f - 0.12f * p
                scaleY = 1f - 0.12f * p
                alpha = (1f - 0.9f * p).coerceAtLeast(0.05f)
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
    ) {
        content()
    }
}

@Composable
private fun OtherSubscriptions(
    groups: List<GroupMapItem>,
    onSelectGroup: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = stringResource(R.string.other_subscriptions_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        )
        groups.forEach { group ->
            val model = group.usageInfo?.let {
                buildSubscriptionInfoUiModel(it, System.currentTimeMillis())
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSelectGroup(group.id) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = group.remarks,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (model != null) {
                        val total = if (model.totalBytes > 0L) {
                            model.totalBytes.toTrafficString()
                        } else {
                            "∞"
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.sub_info_traffic,
                                model.usedBytes.toTrafficString(),
                                total
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        model.expireMillis?.let {
                            Text(
                                text = stringResource(
                                    R.string.sub_info_expiry,
                                    Utils.formatTimestamp(it, "yyyy-MM-dd")
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.other_subscription_switch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyListState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyGridState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun ServerItemRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    isAutopilot: Boolean,
    isTesting: Boolean,
    actions: ServerRowActions
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        isAutopilot = isAutopilot,
        isTesting = isTesting,
        doubleColumnDisplay = false,
        actions = actions
    )
}

@Composable
private fun ServerItemColumn(
    row: ServerRowUiModel,
    isSelected: Boolean,
    isAutopilot: Boolean,
    isTesting: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        isAutopilot = isAutopilot,
        isTesting = isTesting,
        doubleColumnDisplay = doubleColumnDisplay,
        actions = actions
    )
}

@Composable
private fun AutopilotCard(
    row: ServerRowUiModel,
    testResult: String,
    selectedStateDescription: String?,
    isSelected: Boolean,
    isTesting: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val accent = MaterialTheme.colorScheme.primary
    val cardColor = if (isSelected) {
        accent.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .background(SolidColor(cardColor))
            .then(
                if (isSelected) Modifier.border(1.5.dp, accent.copy(alpha = 0.7f), shape) else Modifier
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (selectedStateDescription != null) {
                    stateDescription = selectedStateDescription
                }
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_flash_on_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.remarks,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isTesting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = testResult.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    row.testDelayMillis < 0L -> colorPingRed
                    row.testDelayMillis > 0L -> pingGoodColor()
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    isAutopilot: Boolean,
    isTesting: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions
) {
    val testResult = when {
        row.testDelayMillis > 0L -> stringResource(R.string.server_test_delay_value, row.testDelayMillis)
        row.testDelayMillis < 0L -> stringResource(R.string.server_test_delay_na)
        else -> ""
    }
    val selectedStateDescription = if (isSelected) {
        stringResource(R.string.acc_selected_server)
    } else {
        null
    }
    if (isAutopilot) {
        AutopilotCard(
            row = row,
            testResult = testResult,
            selectedStateDescription = selectedStateDescription,
            isSelected = isSelected,
            isTesting = isTesting,
            onClick = { actions.select(row.guid) },
        )
        return
    }
    val flagEnd = row.remarks.indexOfFirst { it.isLetterOrDigit() }.let { if (it > 0) it else 0 }
    val head = row.remarks.substring(0, flagEnd).trim()
    val hasFlag = head.isNotEmpty()
    val name = if (hasFlag) row.remarks.substring(flagEnd).trim() else row.remarks
    val shape = RoundedCornerShape(18.dp)
    val accent = MaterialTheme.colorScheme.primary
    val cardColor = if (isSelected) {
        accent.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .shadow(
                elevation = 9.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(SolidColor(cardColor))
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, accent.copy(alpha = 0.7f), shape)
                } else {
                    Modifier
                }
            )
            .semantics {
                if (selectedStateDescription != null) {
                    stateDescription = selectedStateDescription
                }
            }
            .clickable { actions.select(row.guid) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                contentAlignment = Alignment.Center
            ) {
                if (flagRes != 0) {
                    Image(
                        painter = painterResource(flagRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = head,
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(lineBreak = LineBreak.Paragraph),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.description.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (isTesting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = testResult.ifEmpty { "—" },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    row.testDelayMillis < 0L -> colorPingRed
                    row.testDelayMillis > 0L -> pingGoodColor()
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}
