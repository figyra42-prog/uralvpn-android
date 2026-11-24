package com.v2ray.ang.ui.main

/** Decides when the main screen may run an automatic latency test. */
internal object AutoPingPolicy {
    fun shouldPing(isRunning: Boolean, serverCount: Int): Boolean =
        isRunning && serverCount > 0
}
