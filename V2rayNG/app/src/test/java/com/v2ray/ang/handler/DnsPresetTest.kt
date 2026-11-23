package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DnsPresetTest {

    @Test
    fun `default cloudflare maps to preset`() {
        assertEquals(DnsPreset.CLOUDFLARE, DnsPreset.keyFor(AppConfig.DNS_PROXY))
        assertEquals(DnsPreset.CLOUDFLARE, DnsPreset.keyFor("1.1.1.1"))
        assertEquals(
            DnsPreset.CLOUDFLARE,
            DnsPreset.keyFor("  HTTPS://CLOUDFLARE-DNS.COM/DNS-QUERY  ")
        )
    }

    @Test
    fun `google and quad9 map to presets`() {
        assertEquals(DnsPreset.GOOGLE, DnsPreset.keyFor("8.8.8.8"))
        assertEquals(DnsPreset.GOOGLE, DnsPreset.keyFor("https://dns.google/dns-query"))
        assertEquals(DnsPreset.QUAD9, DnsPreset.keyFor("9.9.9.9"))
        assertEquals(DnsPreset.QUAD9, DnsPreset.keyFor("tcp://9.9.9.9"))
    }

    @Test
    fun `adguard maps to preset`() {
        assertEquals(DnsPreset.ADGUARD, DnsPreset.keyFor("94.140.14.14"))
        assertEquals(DnsPreset.ADGUARD, DnsPreset.keyFor("https://dns.adguard-dns.com/dns-query"))
    }

    @Test
    fun `unknown value is custom`() {
        assertEquals(DnsPreset.CUSTOM, DnsPreset.keyFor(""))
        assertEquals(DnsPreset.CUSTOM, DnsPreset.keyFor("tls://dns.example"))
        assertNull(DnsPreset.addressFor(DnsPreset.CUSTOM))
    }

    @Test
    fun `addressFor returns preset addresses`() {
        assertEquals(DnsPreset.CLOUDFLARE, DnsPreset.addressFor(DnsPreset.CLOUDFLARE))
        assertEquals(DnsPreset.GOOGLE, DnsPreset.addressFor(DnsPreset.GOOGLE))
        assertEquals(DnsPreset.QUAD9, DnsPreset.addressFor(DnsPreset.QUAD9))
        assertEquals(DnsPreset.ADGUARD, DnsPreset.addressFor(DnsPreset.ADGUARD))
    }
}
