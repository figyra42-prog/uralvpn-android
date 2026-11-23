package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UralWatchdogTest {

    @Test
    fun `first failure does not request reload`() {
        val counter = UralWatchdog.FailureCounter(2)

        assertFalse(counter.onFailure())
        assertEquals(1, counter.failures)
    }

    @Test
    fun `second consecutive failure requests reload and resets`() {
        val counter = UralWatchdog.FailureCounter(2)

        assertFalse(counter.onFailure())
        assertTrue(counter.onFailure())
        assertEquals(0, counter.failures)
    }

    @Test
    fun `success resets accumulated failures`() {
        val counter = UralWatchdog.FailureCounter(2)

        counter.onFailure()
        counter.onAlive()
        assertFalse(counter.onFailure())
        assertEquals(1, counter.failures)
    }

    @Test
    fun `threshold of one reloads immediately`() {
        val counter = UralWatchdog.FailureCounter(1)

        assertTrue(counter.onFailure())
        assertEquals(0, counter.failures)
    }
}
