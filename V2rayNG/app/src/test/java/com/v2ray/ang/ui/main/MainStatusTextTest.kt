package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainStatusTextTest {

    @Test
    fun testingTakesPrecedence() {
        assertEquals(
            MainStatusText.Testing,
            mainStatusTextKind(isTesting = true, isRunning = true, hasGroups = true, selectedServerName = "Latvia"),
        )
    }

    @Test
    fun connectedNamesTheSelectedLocation() {
        assertEquals(
            MainStatusText.ConnectedWith,
            mainStatusTextKind(isTesting = false, isRunning = true, hasGroups = true, selectedServerName = "Latvia"),
        )
    }

    @Test
    fun connectedFallsBackWhenLocationIsBlank() {
        assertEquals(
            MainStatusText.Connected,
            mainStatusTextKind(isTesting = false, isRunning = true, hasGroups = true, selectedServerName = "  "),
        )
    }

    @Test
    fun noSubscriptionWhenThereAreNoGroups() {
        assertEquals(
            MainStatusText.NoSubscription,
            mainStatusTextKind(isTesting = false, isRunning = false, hasGroups = false, selectedServerName = null),
        )
    }

    @Test
    fun disconnectedWithSubscription() {
        assertEquals(
            MainStatusText.Disconnected,
            mainStatusTextKind(isTesting = false, isRunning = false, hasGroups = true, selectedServerName = null),
        )
    }
}
