package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AlwaysOnVpn
import com.v2ray.ang.handler.AlwaysOnVpnState
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    private val _alwaysOnState = MutableStateFlow(AlwaysOnVpnState.UNAVAILABLE)
    val alwaysOnState = _alwaysOnState.asStateFlow()

    suspend fun refreshSystemVpnSettings() {
        val application = getApplication<Application>()
        _systemVpnSettingsAvailable.value = withContext(Dispatchers.IO) {
            // Android exposes the VPN page, not a direct link to the Always-on VPN switch.
            Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(application.packageManager) != null
        }
        _alwaysOnState.value = withContext(Dispatchers.IO) {
            AlwaysOnVpn.read(application)
        }
    }

    /** Persists the developer simulation switch and refreshes all subscriptions. */
    fun setSimulateBlock(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_SIMULATE_BLOCK, enabled)
        launchLoading {
            val result = withContext(Dispatchers.IO) {
                AngConfigManager.updateConfigViaSubAll()
            }
            if (result.configCount > 0) {
                SettingsChangeManager.makeRestartService()
                SettingsChangeManager.makeSetupGroupTab()
                toastSuccess(R.string.toast_simulation_updated)
            } else {
                toastError(R.string.toast_simulation_failed)
            }
        }
    }

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }
}
