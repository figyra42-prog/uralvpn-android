package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UralDeepLinkTest {

    @Test
    fun `install-sub accepts https`() {
        assertEquals(
            UralDeepLink.Target.Subscription("https://sub.example/api/sub/token"),
            UralDeepLink.fromScheme("install-sub", "https://sub.example/api/sub/token", null)
        )
    }

    @Test
    fun `install-sub rejects non-https links`() {
        assertNull(UralDeepLink.fromScheme("install-sub", "http://sub.example/token", null))
        assertNull(UralDeepLink.fromScheme("install-sub", "javascript:alert(1)", null))
        assertNull(UralDeepLink.fromScheme("install-sub", "", null))
    }

    @Test
    fun `install-config accepts config links and keeps the fragment`() {
        assertEquals(
            UralDeepLink.Target.Config("vless://uuid@example.com:443", "UralVPN"),
            UralDeepLink.fromScheme("install-config", "vless://uuid@example.com:443", "UralVPN")
        )
    }

    @Test
    fun `unknown host and scheme are rejected`() {
        assertNull(UralDeepLink.fromScheme("other", "https://example.com", null))
        assertNull(UralDeepLink.fromScheme("install-config", "ftp://example.com", null))
    }

    @Test
    fun `shared text accepts subscription and config links`() {
        assertEquals(
            UralDeepLink.Target.Config("https://sub.example/token", null),
            UralDeepLink.fromShared("https://sub.example/token")
        )
        assertEquals(
            UralDeepLink.Target.Config("vless://uuid@example.com:443", null),
            UralDeepLink.fromShared("vless://uuid@example.com:443")
        )
        assertNull(UralDeepLink.fromShared("hello"))
        assertNull(UralDeepLink.fromShared(""))
    }
}
