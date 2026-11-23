package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlwaysOnVpnTest {

    private val app = "app.uralvpn.xray"

    @Test
    fun `no always-on package is off`() {
        assertEquals(AlwaysOnVpnState.OFF, AlwaysOnVpn.resolve(app, null, null))
        assertEquals(AlwaysOnVpnState.OFF, AlwaysOnVpn.resolve(app, "", "1"))
    }

    @Test
    fun `this app without lockdown is on`() {
        assertEquals(AlwaysOnVpnState.ON, AlwaysOnVpn.resolve(app, app, null))
        assertEquals(AlwaysOnVpnState.ON, AlwaysOnVpn.resolve(app, app, "0"))
    }

    @Test
    fun `this app with lockdown is lockdown`() {
        assertEquals(AlwaysOnVpnState.LOCKDOWN, AlwaysOnVpn.resolve(app, app, "1"))
    }

    @Test
    fun `another app is reported separately`() {
        assertEquals(AlwaysOnVpnState.OTHER_APP, AlwaysOnVpn.resolve(app, "com.example.other", "1"))
    }
}
