package com.v2ray.ang.ui.main

/** Converts two cumulative counter samples into bytes per second. */
internal object TrafficSpeed {
    fun calculate(
        previousUp: Long,
        previousDown: Long,
        previousAt: Long,
        up: Long,
        down: Long,
        now: Long,
    ): Pair<Long, Long>? {
        if (previousAt <= 0L || now <= previousAt) return null
        val seconds = (now - previousAt) / 1000.0
        val upSpeed = ((up - previousUp).coerceAtLeast(0L) / seconds).toLong()
        val downSpeed = ((down - previousDown).coerceAtLeast(0L) / seconds).toLong()
        return upSpeed to downSpeed
    }
}
