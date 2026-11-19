package io.nekohasekai.sfa.compose.screen.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.libbox.Status
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import androidx.compose.material3.TopAppBar
import io.nekohasekai.sfa.compose.model.Connection
import java.util.Locale

// UralVPN: трафик по приложениям за текущую сессию (данные соединений ядра).
private data class AppTraffic(val name: String, val upload: Long, val download: Long) {
    val total: Long get() = upload + download
}

private fun aggregate(connections: List<Connection>): List<AppTraffic> {
    val map = HashMap<String, LongArray>()
    for (connection in connections) {
        val info = connection.processInfo
        val name = info?.packageNames?.firstOrNull()
            ?: info?.processPath?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
            ?: "система"
        val item = map.getOrPut(name) { LongArray(2) }
        item[0] += connection.uploadTotal.coerceAtLeast(connection.upload)
        item[1] += connection.downloadTotal.coerceAtLeast(connection.download)
    }
    return map.map { (name, value) -> AppTraffic(name, value[0], value[1]) }
        .sortedByDescending { it.total }
}

private fun formatBytes(value: Long): String {
    if (value <= 0) return "0"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var size = value.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit += 1
    }
    return String.format(Locale.US, if (size >= 100) "%.0f %s" else "%.1f %s", size, units[unit])
}

@Composable
fun UralStatsScreen(serviceStatus: Status) {
    OverrideTopBar {
        TopAppBar(title = { Text("Трафик по приложениям") })
    }
    val viewModel: ConnectionsViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(serviceStatus) { viewModel.updateServiceStatus(serviceStatus) }
    val rows = remember(state.allConnections, state.connections) {
        aggregate(state.allConnections.ifEmpty { state.connections })
    }
    val padding = LocalScaffoldPadding.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
    ) {
        if (rows.isEmpty()) {
            Text(
                "Данных пока нет — включите VPN и попользуйтесь интернетом",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.name }) { row ->
                ListItem(
                    headlineContent = { Text(row.name, style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text("↓ ${formatBytes(row.download)} · ↑ ${formatBytes(row.upload)}")
                    },
                    trailingContent = {
                        Text(formatBytes(row.total), color = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
        }
    }
}
