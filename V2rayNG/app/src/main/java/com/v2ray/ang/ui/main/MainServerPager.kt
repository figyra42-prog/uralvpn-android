package com.v2ray.ang.ui.main

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineBreak
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
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

private const val FLAG_SCROLL_SCALE = 0.08f

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
    onOpenLink: (String) -> Unit,
    sortByPing: Boolean,
    onToggleSortByPing: () -> Unit,
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
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
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
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        isAutopilot = row.guid == autopilotGuid,
                        doubleColumnDisplay = true,
                        actions = actions,
                        gridState = gridState
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
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            header?.let { h ->
                item(key = "header") { h() }
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
                            ServerItemRow(
                                row = row,
                                isSelected = row.guid == selectedGuid,
                                isAutopilot = row.guid == autopilotGuid,
                                actions = actions,
                                listState = listState
                            )
                        }
                    }
                } else {
                    ServerItemRow(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        isAutopilot = row.guid == autopilotGuid,
                        actions = actions,
                        listState = listState
                    )
                }
            }
            footer?.let { footerContent ->
                item(key = "footer") { footerContent() }
            }
        }
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
    actions: ServerRowActions,
    listState: LazyListState? = null
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        isAutopilot = isAutopilot,
        doubleColumnDisplay = false,
        actions = actions,
        listState = listState
    )
}

@Composable
private fun ServerItemColumn(
    row: ServerRowUiModel,
    isSelected: Boolean,
    isAutopilot: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions,
    gridState: LazyGridState? = null
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        isAutopilot = isAutopilot,
        doubleColumnDisplay = doubleColumnDisplay,
        actions = actions,
        gridState = gridState
    )
}

@Composable
private fun AutopilotCard(
    row: ServerRowUiModel,
    testResult: String,
    selectedStateDescription: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), shape)
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
        Text(
            text = testResult.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                row.testDelayMillis < 0L -> colorPingRed
                row.testDelayMillis > 0L -> colorPing
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    isAutopilot: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null
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
            onClick = { actions.select(row.guid) },
        )
        return
    }
    val flag = row.remarks.take(4).takeIf {
        it.length >= 4 &&
            it.codePointAt(0) in 0x1F1E6..0x1F1FF &&
            it.codePointAt(2) in 0x1F1E6..0x1F1FF
    }.orEmpty()
    val name = if (flag.isNotEmpty()) row.remarks.substring(4).trimStart() else row.remarks
    val flagScale = when {
        listState != null -> {
            val layout = listState.layoutInfo
            val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
            val info = layout.visibleItemsInfo.firstOrNull { it.key == row.guid }
            val center = info?.let { it.offset + it.size / 2f }
                ?: (layout.viewportStartOffset + viewport / 2f)
            val viewportCenter = layout.viewportStartOffset + viewport / 2f
            1f - FLAG_SCROLL_SCALE * (abs(center - viewportCenter) / (viewport / 2f)).coerceIn(0f, 1f)
        }
        gridState != null -> {
            val layout = gridState.layoutInfo
            val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
            val info = layout.visibleItemsInfo.firstOrNull { it.key == row.guid }
            val center = info?.let { it.offset.y + it.size.height / 2f }
                ?: (layout.viewportStartOffset + viewport / 2f)
            val viewportCenter = layout.viewportStartOffset + viewport / 2f
            1f - FLAG_SCROLL_SCALE * (abs(center - viewportCenter) / (viewport / 2f)).coerceIn(0f, 1f)
        }
        else -> 1f
    }
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
        if (flag.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(34.dp)
                    .graphicsLayer {
                        scaleX = flagScale
                        scaleY = flagScale
                        alpha = 0.8f + 0.2f * flagScale
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = flag,
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    maxLines = 1
                )
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
        Text(
            text = testResult.ifEmpty { "—" },
            style = MaterialTheme.typography.labelLarge,
            color = when {
                row.testDelayMillis < 0L -> colorPingRed
                row.testDelayMillis > 0L -> colorPing
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
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
