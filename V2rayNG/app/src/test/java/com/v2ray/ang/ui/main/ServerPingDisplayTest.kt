package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerPingDisplayTest {
    @Test
    fun spinnerIsShownOnlyForRowsWithoutResult() {
        assertTrue(ServerPingDisplay.showSpinner(isTesting = true, delayMillis = 0L))
        assertFalse(ServerPingDisplay.showSpinner(isTesting = true, delayMillis = 42L))
        assertFalse(ServerPingDisplay.showSpinner(isTesting = true, delayMillis = -1L))
        assertFalse(ServerPingDisplay.showSpinner(isTesting = false, delayMillis = 0L))
    }
}
