package com.v2ray.ang.handler

/**
 * UralVPN: presets for the remote DNS server used for foreign sites.
 *
 * The value is written to [com.v2ray.ang.AppConfig.PREF_REMOTE_DNS] and must satisfy the same
 * formats as the core DNS setting: a plain IP, https://, tcp:// or quic:// address. Cloudflare,
 * Google and Quad9 use DoH; AdGuard uses its ad-blocking resolver IP so no bootstrap host is
 * required.
 */
object DnsPreset {
    const val CLOUDFLARE = "https://cloudflare-dns.com/dns-query"
    const val GOOGLE = "https://dns.google/dns-query"
    const val QUAD9 = "https://dns.quad9.net/dns-query"
    const val ADGUARD = "94.140.14.14"
    const val CUSTOM = "custom"

    private val cloudflareAliases = setOf(
        CLOUDFLARE,
        "1.1.1.1",
        "1.0.0.1",
        "https://1.1.1.1/dns-query",
        "tcp://1.1.1.1"
    )
    private val googleAliases = setOf(
        GOOGLE,
        "8.8.8.8",
        "8.8.4.4",
        "tcp://8.8.8.8"
    )
    private val quad9Aliases = setOf(
        QUAD9,
        "9.9.9.9",
        "149.112.112.112",
        "tcp://9.9.9.9"
    )
    private val adguardAliases = setOf(
        ADGUARD,
        "94.140.15.15",
        "https://dns.adguard-dns.com/dns-query",
        "tcp://94.140.14.14"
    )

    /** Maps a stored DNS address to the matching preset key, or [CUSTOM]. */
    fun keyFor(address: String): String {
        val value = address.trim().lowercase()
        return when (value) {
            in cloudflareAliases -> CLOUDFLARE
            in googleAliases -> GOOGLE
            in quad9Aliases -> QUAD9
            in adguardAliases -> ADGUARD
            else -> CUSTOM
        }
    }

    /** Returns the canonical address for a preset key, or null for [CUSTOM]. */
    fun addressFor(key: String): String? = when (key) {
        CLOUDFLARE -> CLOUDFLARE
        GOOGLE -> GOOGLE
        QUAD9 -> QUAD9
        ADGUARD -> ADGUARD
        else -> null
    }
}
