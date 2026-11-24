package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoPingPolicyTest {
    @Test
    fun pingsOnlyWhenRunningWithServers() {
        assertTrue(AutoPingPolicy.shouldPing(isRunning = true, serverCount = 3))
    }

    @Test
    fun doesNotPingWhenStopped() {
        assertFalse(AutoPingPolicy.shouldPing(isRunning = false, serverCount = 3))
    }

    @Test
    fun doesNotPingWithoutServers() {
        assertFalse(AutoPingPolicy.shouldPing(isRunning = true, serverCount = 0))
    }
}
