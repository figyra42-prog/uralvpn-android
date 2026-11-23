package com.v2ray.ang.handler

import android.content.Context
import android.provider.Settings

/**
 * UralVPN: reports whether Android runs this app as the always-on VPN and whether the
 * system-wide "Block connections without VPN" lockdown is enabled.
 *
 * Android has no public API for the current always-on package. These are the Settings.Secure
 * keys written by the system VPN page. Replace this helper with a public VpnService accessor
 * when one is available on the app's minSdk.
 */
enum class AlwaysOnVpnState {
    OFF,
    ON,
    LOCKDOWN,
    OTHER_APP,
    UNAVAILABLE,
}

object AlwaysOnVpn {
    private const val KEY_ALWAYS_ON_APP = "always_on_vpn_app"
    private const val KEY_LOCKDOWN = "always_on_vpn_lockdown"

    fun read(context: Context): AlwaysOnVpnState = runCatching {
        val resolver = context.contentResolver
        resolve(
            appPackage = context.packageName,
            alwaysOnPackage = Settings.Secure.getString(resolver, KEY_ALWAYS_ON_APP),
            lockdown = Settings.Secure.getString(resolver, KEY_LOCKDOWN),
        )
    }.getOrDefault(AlwaysOnVpnState.UNAVAILABLE)

    internal fun resolve(
        appPackage: String,
        alwaysOnPackage: String?,
        lockdown: String?,
    ): AlwaysOnVpnState = when {
        alwaysOnPackage.isNullOrEmpty() -> AlwaysOnVpnState.OFF
        alwaysOnPackage != appPackage -> AlwaysOnVpnState.OTHER_APP
        lockdown == "1" -> AlwaysOnVpnState.LOCKDOWN
        else -> AlwaysOnVpnState.ON
    }
}
