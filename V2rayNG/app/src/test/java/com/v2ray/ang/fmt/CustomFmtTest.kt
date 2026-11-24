package com.v2ray.ang.fmt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomFmtTest {
    @Test
    fun vlessVnextProvidesServerAddressAndPort() {
        val json = """{
            "remarks":"Latvia",
            "log":{},
            "inbounds":[],
            "outbounds":[{
                "protocol":"vless",
                "settings":{"vnext":[{"address":"example.com","port":443,"users":[{"id":"x"}]}]}
            }],
            "routing":{}
        }"""
        val profile = CustomFmt.parse(json)
        assertEquals("example.com", profile.server)
        assertEquals("443", profile.serverPort)
    }
}
