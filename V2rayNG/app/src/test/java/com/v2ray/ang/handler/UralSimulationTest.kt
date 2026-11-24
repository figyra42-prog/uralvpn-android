package com.v2ray.ang.handler

import com.google.gson.JsonParser
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

    @Test
    fun englishAutopilotKeepsWorking() {
        val profile = profile("Auto Balancer Two Tier")
        assertEquals("443", UralSimulation.applyToProfile(profile, true).serverPort)
    }

    @Test
    fun directCustomConfigGetsClosedProxyPort() {
        val content = """{"outbounds":[{"protocol":"vless","tag":"proxy","settings":{"vnext":[{"port":443}]}},{"protocol":"freedom","tag":"direct"},{"protocol":"blackhole","tag":"block"}]}"""
        val root = JsonParser.parseString(
            UralSimulation.applyToCustomConfig(content, "🇱🇻Латвия", true)
        ).asJsonObject
        val proxy = root.getAsJsonArray("outbounds")
            .first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
        val port = proxy.getAsJsonObject("settings")
            .getAsJsonArray("vnext").first().asJsonObject.get("port").asInt
        assertEquals(1, port)
    }

    @Test
    fun cdnCustomConfigKeepsProxyPort() {
        val content = """{"outbounds":[{"protocol":"vless","tag":"proxy","settings":{"vnext":[{"port":443}]}},{"protocol":"freedom","tag":"direct"}]}"""
        val root = JsonParser.parseString(
            UralSimulation.applyToCustomConfig(content, "Германия мобильный 📱", true)
        ).asJsonObject
        val proxy = root.getAsJsonArray("outbounds")
            .first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
        val port = proxy.getAsJsonObject("settings")
            .getAsJsonArray("vnext").first().asJsonObject.get("port").asInt
        assertEquals(443, port)
    }
}
