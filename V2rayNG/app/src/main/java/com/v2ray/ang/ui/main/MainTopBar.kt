package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.verticalScrollbar

@Composable
fun MainTopBar(
    isLoading: Boolean,
    showImportMenu: Boolean,
    onImportMenuChange: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onAction: (MainAction) -> Unit
) {
    val importMenuScrollState = rememberScrollState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp

    AppTopBar(
        title = "🏔UralVPN",
        centerTitle = true,
        titleFontWeight = FontWeight.Bold,
        onBackClick = {},
        isLoading = isLoading,
        navigationIcon = {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_menu_24dp), contentDescription = stringResource(R.string.main_menu))
            }
        },
        actions = {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(
                    onClick = { onImportMenuChange(true) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add_24dp),
                        contentDescription = stringResource(R.string.acc_add),
                        modifier = Modifier.size(30.dp),
                    )
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { onImportMenuChange(false) },
                    scrollState = importMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(importMenuScrollState)
                ) {
                    ImportMenuContent(
                        onAction = { action ->
                            onImportMenuChange(false)
                            onAction(action)
                        }
                    )
                }
            }
        }
    )
}
