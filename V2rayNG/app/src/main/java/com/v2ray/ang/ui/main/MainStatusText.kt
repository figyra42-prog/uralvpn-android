package com.v2ray.ang.ui.main

/** Which status line the main screen shows under the connect button. */
internal enum class MainStatusText {
    NoSubscription,
    Disconnected,
    Connected,
    ConnectedWith,
    Testing,
}

internal fun mainStatusTextKind(
    isTesting: Boolean,
    isRunning: Boolean,
    hasGroups: Boolean,
    selectedServerName: String?,
): MainStatusText = when {
    isTesting -> MainStatusText.Testing
    isRunning && !selectedServerName.isNullOrBlank() -> MainStatusText.ConnectedWith
    isRunning -> MainStatusText.Connected
    !hasGroups -> MainStatusText.NoSubscription
    else -> MainStatusText.Disconnected
}
