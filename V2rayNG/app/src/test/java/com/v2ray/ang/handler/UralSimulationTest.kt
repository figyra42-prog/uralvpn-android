package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UralSimulationTest {
    private fun profile(remark: String, port: String = "443") =
        ProfileItem.create(EConfigType.VLESS).apply {
            this.remarks = remark
            this.serverPort = port
        }

    @Test
    fun disabledSimulationKeepsThePort() {
        val profile = profile("Латвия")
        assertEquals("443", UralSimulation.applyToProfile(profile, false).serverPort)
    }

    @Test
    fun directProfileGetsClosedPort() {
        val profile = profile("🇱🇻 Латвия")
        assertEquals("1", UralSimulation.applyToProfile(profile, true).serverPort)
    }

    @Test
    fun cdnProfileKeepsWorking() {
        val profile = profile("🇩🇪 Германия мобильный 📱")
        assertEquals("443", UralSimulation.applyToProfile(profile, true).serverPort)
    }

    @Test
    fun autopilotKeepsWorking() {
        val profile = profile("🚀 АВТОПИЛОТ")
        assertEquals("443", UralSimulation.applyToProfile(profile, true).serverPort)
    }
}
