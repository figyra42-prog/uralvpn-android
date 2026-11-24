package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomEndpointTest {
    @Test
    fun readsVlessVnextEndpoint() {
        val raw = """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443}]}},{"protocol":"freedom"}]}"""
        assertEquals(CustomEndpoint.Endpoint("example.com", 443), CustomEndpoint.fromRaw(raw))
    }

    @Test
    fun readsHysteriaEndpoint() {
        val raw = """{"outbounds":[{"protocol":"hysteria","settings":{"address":"hy.example","port":8443,"version":2}}]}"""
        assertEquals(CustomEndpoint.Endpoint("hy.example", 8443), CustomEndpoint.fromRaw(raw))
    }

    @Test
    fun ignoresNonProxyOutbounds() {
        val raw = """{"outbounds":[{"protocol":"freedom","settings":{}},{"protocol":"blackhole","settings":{}}]}"""
        assertNull(CustomEndpoint.fromRaw(raw))
    }
}
