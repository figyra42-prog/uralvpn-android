package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.verticalScrollbar

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Home(R.drawable.ic_home_24dp, R.string.menu_home),
    AddSubscription(R.drawable.ic_add_24dp, R.string.menu_add_subscription),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.menu_apps),
    Logcat(R.drawable.ic_logcat_24dp, R.string.menu_logs),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    Subscriptions(R.drawable.ic_link_24dp, R.string.menu_my_subscription),
    CheckUpdate(R.drawable.ic_check_update_24dp, R.string.menu_updates),
    Notifications(R.drawable.ic_notifications_24dp, R.string.menu_notifications),
    UralVpnSite(R.drawable.ic_translate_24dp, R.string.menu_uralvpn_site),
    About(R.drawable.ic_about_24dp, R.string.menu_about),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    Promotion(R.drawable.ic_promotion_24dp, R.string.title_pref_promotion)
}

private val topItems = listOf(
    MainDestination.Home,
    MainDestination.AddSubscription,
    MainDestination.PerAppProxy,
    MainDestination.Logcat,
    MainDestination.Settings,
    MainDestination.Subscriptions
)

private val bottomItems = listOf(
    MainDestination.CheckUpdate,
    MainDestination.Notifications,
    MainDestination.UralVpnSite,
    MainDestination.About
)

private val moreItems = listOf(
    MainDestination.Routing,
    MainDestination.UserAssets,
    MainDestination.BackupRestore,
    MainDestination.Promotion
)

@Composable
fun MainDrawerContent(
    drawerState: DrawerState,
    startOnBoot: Boolean,
    onStartOnBootChange: (Boolean) -> Unit,
    onAddSubscription: () -> Unit,
    onNavigate: (MainDestination) -> Unit
) {
    val drawerScrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.fillMaxWidth(0.75f),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                text = "🏔UralVPN",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(12.dp))

            topItems.forEach { item ->
                DrawerItem(item, onAddSubscription, onNavigate)
            }

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_autostart)) },
                selected = false,
                onClick = { onStartOnBootChange(!startOnBoot) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_power_24dp),
                        contentDescription = null
                    )
                },
                badge = {
                    Switch(
                        checked = startOnBoot,
                        onCheckedChange = onStartOnBootChange
                    )
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            bottomItems.forEach { item ->
                DrawerItem(item, onAddSubscription, onNavigate)
            }

            AppDivider()
            Text(
                text = stringResource(R.string.menu_more),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 4.dp)
            )
            moreItems.forEach { item ->
                DrawerItem(item, onAddSubscription, onNavigate)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerItem(
    item: MainDestination,
    onAddSubscription: () -> Unit,
    onNavigate: (MainDestination) -> Unit
) {
    NavigationDrawerItem(
        label = { Text(stringResource(item.labelRes)) },
        selected = false,
        onClick = {
            if (item == MainDestination.AddSubscription) onAddSubscription()
            else onNavigate(item)
        },
        icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
