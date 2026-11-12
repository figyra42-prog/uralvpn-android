package io.nekohasekai.sfa.bg

import android.content.Context
import io.nekohasekai.sfa.Application

// UralVPN: "start with the device" switch from the settings screen; BootReceiver reads it.
object UralAutostart {
    private val prefs get() = Application.application.getSharedPreferences("ural_ui", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("autostart", false)
        set(value) {
            prefs.edit().putBoolean("autostart", value).apply()
        }
}
