package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.util.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UralSimulationTest {
    private val json = """{
        "outbounds": [
            {"tag":"auto-latvia","protocol":"vless","settings":{"vnext":[{"port":443}]}},
            {"tag":"cdn-germany","protocol":"vless","settings":{"vnext":[{"port":443}]}},
            {"tag":"direct","protocol":"freedom","settings":{}}
        ]
    }"""

    private val links = """
        vless://uuid@direct.example:443?encryption=none#Латвия
        vless://uuid@cdn.example:443?encryption=none#Германия мобильный 📱
        vless://uuid@auto.example:443?encryption=none#АВТОПИЛОТ
    """.trimIndent()

    @Test
    fun disabledSimulationKeepsContent() {
        assertEquals(json, UralSimulation.apply(json, false))
        assertEquals(links, UralSimulation.apply(links, false))
    }

    @Test
    fun directJsonOutboundsGetClosedPort() {
        val root = JsonParser.parseString(UralSimulation.apply(json, true)).asJsonObject
        val outbounds = root.getAsJsonArray("outbounds")
        val ports = outbounds.mapNotNull { element ->
            val outbound = element.asJsonObject
            val vnext = outbound.getAsJsonObject("settings")
                ?.getAsJsonArray("vnext")?.firstOrNull()?.asJsonObject ?: return@mapNotNull null
            outbound.get("tag").asString to vnext.get("port").asInt
        }.toMap()
        assertEquals(1, ports["auto-latvia"] ?: -1)
        assertEquals(443, ports["cdn-germany"] ?: -1)
    }

    @Test
    fun directLinksGetClosedPortAndCdnAndAutopilotKeepWorking() {
        val lines = UralSimulation.apply(links, true).lines()
        assertEquals("vless://uuid@direct.example:1?encryption=none#Латвия", lines[0])
        assertEquals("vless://uuid@cdn.example:443?encryption=none#Германия мобильный 📱", lines[1])
        assertEquals("vless://uuid@auto.example:443?encryption=none#АВТОПИЛОТ", lines[2])
    }

    @Test
    fun base64LinksAreDecodedAndReencoded() {
        val encoded = Utils.encode(links)
        val decoded = Utils.decode(UralSimulation.apply(encoded, true))
        assertEquals("vless://uuid@direct.example:1?encryption=none#Латвия", decoded.lines().first())
    }
}
