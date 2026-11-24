package com.v2ray.ang.dto

sealed class RealPingEvent {

    /** Periodic progress update while the batch is still running. */
    data class Progress(val text: String) : RealPingEvent()

    /** A single server result is available. */
    data class Result(val guid: String, val delayMillis: Long) : RealPingEvent()

    /** Cold-open timing for one server, used by the batch report. */
    data class Timing(
        val guid: String,
        val remark: String,
        val type: String,
        val connectMillis: Long,
        val totalMillis: Long,
    ) : RealPingEvent()

    /** The entire batch has finished or been cancelled. */
    data class Finish(val status: String) : RealPingEvent()
}

