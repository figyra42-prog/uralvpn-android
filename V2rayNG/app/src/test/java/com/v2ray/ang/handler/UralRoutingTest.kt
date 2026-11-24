package com.v2ray.ang.handler

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UralRoutingTest {
    @Test
    fun replacesIpIfNonMatchWithAsIs() {
        val root = JsonParser.parseString(
            """{"routing":{"domainStrategy":"IPIfNonMatch","rules":[{"domain":["geosite:category-ads"]}]}}"""
        ).asJsonObject

        UralRouting.applyFastRouting(root)

        assertEquals("AsIs", root.getAsJsonObject("routing").get("domainStrategy").asString)
    }

    @Test
    fun createsRoutingWhenItIsMissing() {
        val root = JsonParser.parseString("""{"outbounds":[]}""").asJsonObject

        UralRouting.applyFastRouting(root)

        assertEquals("AsIs", root.getAsJsonObject("routing").get("domainStrategy").asString)
    }

    @Test
    fun keepsExistingRoutingRules() {
        val root = JsonParser.parseString(
            """{"routing":{"domainStrategy":"IPIfNonMatch","rules":[{"outboundTag":"direct"}]}}"""
        ).asJsonObject

        UralRouting.applyFastRouting(root)

        assertEquals(1, root.getAsJsonObject("routing").getAsJsonArray("rules").size())
    }
}
