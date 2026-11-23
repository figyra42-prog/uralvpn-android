package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.v2ray.ang.handler.AlwaysOnVpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SettingsViewModelTest {
    @Test
    fun vpnSettingsVisibilityFollowsActivityAvailability() = runBlocking(Dispatchers.IO) {
        val packageManager = mock<PackageManager>()
        val application = mock<Application>()
        whenever(application.packageManager).thenReturn(packageManager)
        var activity: ComponentName? = null

        mockConstruction(Intent::class.java) { intent, context ->
            assertEquals(listOf(Settings.ACTION_VPN_SETTINGS), context.arguments())
            whenever(intent.resolveActivity(packageManager)).thenAnswer { activity }
        }.use {
            val viewModel = SettingsViewModel(application)
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
            assertEquals(AlwaysOnVpnState.UNAVAILABLE, viewModel.alwaysOnState.value)
            viewModel.refreshSystemVpnSettings()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)

            activity = mock<ComponentName>()
            viewModel.refreshSystemVpnSettings()
            assertTrue(viewModel.systemVpnSettingsAvailable.value)

            activity = null
            viewModel.refreshSystemVpnSettings()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
        }
    }
}
