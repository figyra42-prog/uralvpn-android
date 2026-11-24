package com.v2ray.ang.handler

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UralFastDnsTest {
    @Test
    fun addsFakeDnsAsDefaultAndSniffsIt() {
        val root = JsonParser.parseString(
            """{
              "dns":{"servers":[{"address":"1.1.1.1"}]},
              "inbounds":[{"protocol":"tun","sniffing":{"enabled":true,"destOverride":["http","tls"]}}]
            }"""
        ).asJsonObject

        UralFastDns.apply(root)

        assertEquals("198.18.0.0/15", root.getAsJsonArray("fakedns").first().asJsonObject.get("ipPool").asString)
        assertEquals("fakedns", root.getAsJsonObject("dns").getAsJsonArray("servers").first().asJsonObject.get("address").asString)
        assertTrue(
            root.getAsJsonArray("inbounds").first().asJsonObject
                .getAsJsonObject("sniffing").getAsJsonArray("destOverride")
                .any { it.asString == "fakedns" }
        )
        assertTrue(root.getAsJsonArray("outbounds").any { it.asJsonObject.get("tag").asString == "dns-out" })
        assertEquals("dns-out", root.getAsJsonObject("routing").getAsJsonArray("rules").first().asJsonObject.get("outboundTag").asString)
    }

    @Test
    fun isIdempotent() {
        val root = JsonParser.parseString(
            """{"dns":{"servers":[{"address":"1.1.1.1"}]},"inbounds":[{"sniffing":{"destOverride":[]}}]}"""
        ).asJsonObject

        UralFastDns.apply(root)
        UralFastDns.apply(root)

        val servers = root.getAsJsonObject("dns").getAsJsonArray("servers")
        assertEquals(2, servers.size())
        val overrides = root.getAsJsonArray("inbounds").first().asJsonObject
            .getAsJsonObject("sniffing").getAsJsonArray("destOverride")
        assertEquals(1, overrides.count { it.asString == "fakedns" })
        assertEquals(1, root.getAsJsonArray("outbounds").count { it.asJsonObject.get("tag")?.asString == "dns-out" })
        assertEquals(1, root.getAsJsonObject("routing").getAsJsonArray("rules").count { it.asJsonObject.get("outboundTag")?.asString == "dns-out" })
    }

    @Test
    fun createsDnsAndSniffingWhenMissing() {
        val root = JsonParser.parseString("""{"inbounds":[{}]}""").asJsonObject

        UralFastDns.apply(root)

        assertEquals("fakedns", root.getAsJsonObject("dns").getAsJsonArray("servers").first().asJsonObject.get("address").asString)
        assertTrue(root.getAsJsonArray("fakedns").first().asJsonObject.has("ipPool"))
        assertTrue(
            root.getAsJsonArray("inbounds").first().asJsonObject
                .getAsJsonObject("sniffing").getAsJsonArray("destOverride")
                .any { it.asString == "fakedns" }
        )
    }
}
