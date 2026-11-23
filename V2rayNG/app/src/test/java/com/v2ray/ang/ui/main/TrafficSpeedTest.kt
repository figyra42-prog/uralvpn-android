package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrafficSpeedTest {

    @Test
    fun `first sample has no speed`() {
        assertNull(TrafficSpeed.calculate(0, 0, 0, 1000, 2000, 1000))
    }

    @Test
    fun `counters are converted to bytes per second`() {
        assertEquals(
            1000L to 2000L,
            TrafficSpeed.calculate(0, 0, 1000, 1000, 2000, 2000)
        )
    }

    @Test
    fun `counter reset is treated as zero speed`() {
        assertEquals(
            0L to 0L,
            TrafficSpeed.calculate(5000, 6000, 1000, 100, 200, 2000)
        )
    }

    @Test
    fun `same timestamp has no speed`() {
        assertNull(TrafficSpeed.calculate(0, 0, 1000, 1000, 2000, 1000))
    }
}
