package com.v2ray.ang.ui.main

/** Decides whether a server row still waits for its own ping result. */
internal object ServerPingDisplay {
    fun showSpinner(isTesting: Boolean, delayMillis: Long): Boolean =
        isTesting && delayMillis == 0L
}
