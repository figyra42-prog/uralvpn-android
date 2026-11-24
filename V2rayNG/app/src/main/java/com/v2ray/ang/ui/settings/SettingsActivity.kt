package com.v2ray.ang.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AlwaysOnVpnState
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.DnsPreset
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

class SettingsActivity : BaseComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSystemVpnSettings()
            }
        }
    }

    private fun openSystemVpnSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (error: ActivityNotFoundException) {
            reportSystemVpnSettingsFailure(error)
        } catch (error: SecurityException) {
            reportSystemVpnSettingsFailure(error)
        }
    }

    private fun reportSystemVpnSettingsFailure(error: RuntimeException) {
        LogUtil.e(AppConfig.TAG, "Cannot open system VPN settings", error)
        toastError(R.string.toast_system_vpn_settings_unavailable)
    }

    @Composable
    override fun ScreenContent() {
        val alwaysOnState by viewModel.alwaysOnState.collectAsStateWithLifecycle()
        SettingsScreen(
            viewModel = viewModel,
            alwaysOnState = alwaysOnState,
            onBackClick = { finish() },
            onModeHelpClicked = { Utils.openUri(this, AppConfig.APP_WIKI_MODE) },
            onSystemVpnSettingsClicked = ::openSystemVpnSettings
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    alwaysOnState: AlwaysOnVpnState,
    onBackClick: () -> Unit,
    onModeHelpClicked: () -> Unit,
    onSystemVpnSettingsClicked: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val systemVpnSettingsAvailable by viewModel.systemVpnSettingsAvailable.collectAsStateWithLifecycle()
    var uiSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var vpnSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var coreSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var muxSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var fragmentSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var observatorySettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var modeSettingsExpanded by rememberSaveable { mutableStateOf(true) }

    var killSwitch by rememberMmkvBool(AppConfig.PREF_KILL_SWITCH, false)
    var localDns by rememberMmkvBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false)
    var fakeDns by rememberMmkvBool(AppConfig.PREF_FAKE_DNS_ENABLED, false)
    var appendHttpProxy by rememberMmkvBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)
    var vpnDns by rememberMmkvString(AppConfig.PREF_VPN_DNS, "")
    var vpnBypassLan by rememberMmkvString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN)
    var vpnInterfaceAddress by rememberMmkvString(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, "0")
    var vpnMtu by rememberMmkvString(AppConfig.PREF_VPN_MTU, "")

    var mux by rememberMmkvBool(AppConfig.PREF_MUX_ENABLED, false)
    var muxConcurrency by rememberMmkvString(AppConfig.PREF_MUX_CONCURRENCY, "8")
    var muxXudpConcurrency by rememberMmkvString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY)
    var muxXudpQuic by rememberMmkvString(AppConfig.PREF_MUX_XUDP_QUIC, "reject")

    var fragment by rememberMmkvBool(AppConfig.PREF_FRAGMENT_ENABLED, false)
    var fragmentPackets by rememberMmkvString(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
    var fragmentLength by rememberMmkvString(AppConfig.PREF_FRAGMENT_LENGTH, "50-100")
    var fragmentInterval by rememberMmkvString(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
    var fragmentMaxSplit by rememberMmkvString(AppConfig.PREF_FRAGMENT_MAXSPLIT, "10")
    var observatoryLeastPingInterval by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
    var observatoryLeastLoadInterval by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
    var observatoryLeastLoadMethod by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD, AppConfig.OBSERVATORY_LEAST_LOAD_METHOD)
    var observatoryLeastLoadSampling by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING, AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING)
    var observatoryLeastLoadTimeout by rememberMmkvString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)

    var mode by rememberMmkvString(AppConfig.PREF_MODE, VPN)
    var enableRootMode by rememberMmkvBool(AppConfig.PREF_ROOT_MODE_ENABLE, false)
    var lanSharing by rememberMmkvBool(AppConfig.PREF_ROOT_LAN_SHARING, false)

    var hevTunLogLevel by rememberMmkvString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, AppConfig.DEFAULT_HEV_TUNNEL_LOGLEVEL)
    var hevTunRwTimeout by rememberMmkvString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, "")
    var useHevTun by rememberMmkvBool(AppConfig.PREF_USE_HEV_TUNNEL, true)

    var enableLocalProxy by rememberMmkvBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
    var socksPort by rememberMmkvString(AppConfig.PREF_SOCKS_PORT, "")
    var dynamicSocksPort by rememberMmkvBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)
    var socksUsername by rememberMmkvString(AppConfig.PREF_SOCKS_USERNAME, "")
    var socksPassword by rememberMmkvString(AppConfig.PREF_SOCKS_PASSWORD, "")
    var socksEnableUdp by rememberMmkvBool(AppConfig.PREF_SOCKS_ENABLE_UDP, AppConfig.DEFAULT_SOCKS_ENABLE_UDP)
    var proxySharing by rememberMmkvBool(AppConfig.PREF_PROXY_SHARING, false)

    var speedEnabled by rememberMmkvBool(AppConfig.PREF_SPEED_ENABLED, false)
    var confirmRemove by rememberMmkvBool(AppConfig.PREF_CONFIRM_REMOVE, false)
    var doubleColumnDisplay by rememberMmkvBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    var language by remember {
        mutableStateOf(
            MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE, "auto") ?: "auto"
        )
    }
    var uiModeNight by rememberMmkvString(AppConfig.PREF_UI_MODE_NIGHT, "0")

    var ipv6Enabled by rememberMmkvBool(AppConfig.PREF_IPV6_ENABLED, false)
    var preferIpv6 by rememberMmkvBool(AppConfig.PREF_PREFER_IPV6, false)
    var sniffingEnabled by rememberMmkvBool(AppConfig.PREF_SNIFFING_ENABLED, true)
    var routeOnlyEnabled by rememberMmkvBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
    var remoteDns by rememberMmkvString(AppConfig.PREF_REMOTE_DNS, "")
    var dnsCustomSelected by rememberSaveable {
        mutableStateOf(DnsPreset.keyFor(remoteDns) == DnsPreset.CUSTOM)
    }
    var domesticDns by rememberMmkvString(AppConfig.PREF_DOMESTIC_DNS, "")
    var dnsHosts by rememberMmkvString(AppConfig.PREF_DNS_HOSTS, "")
    var coreLogLevel by rememberMmkvString(AppConfig.PREF_LOGLEVEL, "warning")
    var outboundResolveMethod by rememberMmkvString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, AppConfig.DEFAULT_OUTBOUND_DOMAIN_RESOLVE_METHOD)

    var isBooted by rememberMmkvBool(AppConfig.PREF_IS_BOOTED, false)
    var delayTestUrl by rememberMmkvString(AppConfig.PREF_DELAY_TEST_URL, "")
    var realPingConcurrency by rememberMmkvString(AppConfig.PREF_REAL_PING_CONCURRENCY, "16")
    var ipApiUrl by rememberMmkvString(AppConfig.PREF_IP_API_URL, "")

    val isVpn = mode == VPN
    val hevTunEnabled = isVpn && useHevTun
    val localProxyForced = hevTunEnabled
    val effectiveLocalProxy = enableLocalProxy || localProxyForced
    val muxXudpConcurrencyInt = muxXudpConcurrency.toIntOrNull() ?: AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY.toInt()

    val languageEntries = stringArrayResource(R.array.language_select).toList()
    val languageValues = stringArrayResource(R.array.language_select_value).toList()
    val uiModeNightEntries = stringArrayResource(R.array.ui_mode_night).toList()
    val uiModeNightValues = stringArrayResource(R.array.ui_mode_night_value).toList()
    val bypassLanEntries = stringArrayResource(R.array.vpn_bypass_lan).toList()
    val bypassLanValues = stringArrayResource(R.array.vpn_bypass_lan_value).toList()
    val interfaceAddrEntries = stringArrayResource(R.array.vpn_interface_address).toList()
    val interfaceAddrValues = stringArrayResource(R.array.vpn_interface_address_value).toList()
    val hevLogEntries = stringArrayResource(R.array.hev_tunnel_loglevel).toList()
    val hevLogValues = stringArrayResource(R.array.hev_tunnel_loglevel).toList()
    val coreLogLevelEntries = stringArrayResource(R.array.core_loglevel).toList()
    val coreLogLevelValues = stringArrayResource(R.array.core_loglevel).toList()
    val outboundResolveEntries = stringArrayResource(R.array.outbound_domain_resolve_method).toList()
    val outboundResolveValues = stringArrayResource(R.array.outbound_domain_resolve_method_value).toList()
    val xudpQuicEntries = stringArrayResource(R.array.mux_xudp_quic_entries).toList()
    val xudpQuicValues = stringArrayResource(R.array.mux_xudp_quic_value).toList()
    val fragmentPacketsEntries = stringArrayResource(R.array.fragment_packets).toList()
    val fragmentPacketsValues = stringArrayResource(R.array.fragment_packets).toList()
    val observatoryLeastLoadMethodEntries = stringArrayResource(R.array.observatory_least_load_method).toList()
    val observatoryLeastLoadMethodValues = stringArrayResource(R.array.observatory_least_load_method).toList()
    val modeEntries = stringArrayResource(R.array.mode_entries).toList()
    val modeValues = stringArrayResource(R.array.mode_value).toList()
    val dnsPresetEntries = listOf(
        stringResource(R.string.dns_preset_cloudflare),
        stringResource(R.string.dns_preset_google),
        stringResource(R.string.dns_preset_quad9),
        stringResource(R.string.dns_preset_adguard),
        stringResource(R.string.dns_preset_custom)
    )
    val dnsPresetValues = listOf(
        DnsPreset.CLOUDFLARE,
        DnsPreset.GOOGLE,
        DnsPreset.QUAD9,
        DnsPreset.ADGUARD,
        DnsPreset.CUSTOM
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_settings),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
        ) {
            CollapsiblePreferenceGroupHeader(
                title = stringResource(R.string.title_ui_settings),
                expanded = uiSettingsExpanded,
                onExpandedChange = { uiSettingsExpanded = it }
            )
            if (uiSettingsExpanded) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_speed_enabled),
                    summary = stringResource(R.string.summary_pref_speed_enabled),
                    checked = speedEnabled,
                    onCheckedChange = { speedEnabled = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_confirm_remove),
                    summary = stringResource(R.string.summary_pref_confirm_remove),
                    checked = confirmRemove,
                    onCheckedChange = { confirmRemove = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_double_column_display),
                    summary = stringResource(R.string.summary_pref_double_column_display),
                    checked = doubleColumnDisplay,
                    onCheckedChange = {
                        doubleColumnDisplay = it
                        SettingsChangeManager.makeSetupGroupTab()
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_language),
                    entries = languageEntries,
                    values = languageValues,
                    selectedValue = language,
                    onSelected = {
                        language = it
                        AppLocaleManager.setApplicationLanguage(it)
                    }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_pref_ui_mode_night),
                    entries = uiModeNightEntries,
                    values = uiModeNightValues,
                    selectedValue = uiModeNight,
                    onSelected = {
                        uiModeNight = it
                        ThemeManager.setThemeMode(it)
                    }
                )
            }

            CollapsiblePreferenceGroupHeader(
                title = stringResource(R.string.title_vpn_settings),
                expanded = vpnSettingsExpanded,
                onExpandedChange = { vpnSettingsExpanded = it }
            )
            if (vpnSettingsExpanded) {
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_kill_switch),
                    summary = stringResource(
                        if (alwaysOnState == AlwaysOnVpnState.LOCKDOWN) {
                            R.string.summary_pref_kill_switch_lockdown
                        } else {
                            R.string.summary_pref_kill_switch
                        }
                    ),
                    checked = killSwitch,
                    onCheckedChange = { killSwitch = it }
                )
                if (systemVpnSettingsAvailable) {
                    SettingsMenuItem(
                        title = stringResource(R.string.title_pref_always_on_vpn),
                        subtitle = stringResource(
                            when (alwaysOnState) {
                                AlwaysOnVpnState.LOCKDOWN -> R.string.summary_always_on_vpn_lockdown
                                AlwaysOnVpnState.ON -> R.string.summary_always_on_vpn_on
                                AlwaysOnVpnState.OTHER_APP -> R.string.summary_always_on_vpn_other
                                AlwaysOnVpnState.OFF,
                                AlwaysOnVpnState.UNAVAILABLE -> R.string.summary_always_on_vpn_off
                            }
                        ),
                        onClick = onSystemVpnSettingsClicked
                    )
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.title_pref_ipv6_enabled),
                    summary = stringResource(R.string.summary_pref_ipv6_enabled),
                    checked = ipv6Enabled,
                    onCheckedChange = { ipv6Enabled = it }
                )
                SettingsListItem(
                    title = stringResource(R.string.title_pref_dns_preset),
                    entries = dnsPresetEntries,
                    values = dnsPresetValues,
                    selectedValue = if (dnsCustomSelected) {
                        DnsPreset.CUSTOM
                    } else {
                        DnsPreset.keyFor(remoteDns)
                    },
                    onSelected = { key ->
                        if (key == DnsPreset.CUSTOM) {
                            dnsCustomSelected = true
                        } else {
                            DnsPreset.addressFor(key)?.let { remoteDns = it }
                            dnsCustomSelected = false
                        }
                        localDns = true
                    }
                )
                if (dnsCustomSelected) {
                    SettingsEditItem(
                        title = stringResource(R.string.title_pref_custom_dns),
                        value = remoteDns,
                        onValueChanged = { remoteDns = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}
