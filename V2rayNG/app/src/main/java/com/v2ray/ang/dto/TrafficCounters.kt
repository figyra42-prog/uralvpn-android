package com.v2ray.ang.dto

import java.io.Serializable

/** Cumulative outbound counters returned by the daemon for the live speed display. */
data class TrafficCounters(
    val uplink: Long,
    val downlink: Long,
) : Serializable
