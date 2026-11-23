package com.v2ray.ang.handler

import java.net.URI

/**
 * UralVPN: validates the subscription and configuration links received from the bot.
 *
 * The parser is independent of Android's Uri so the accepted formats can be unit-tested.
 */
object UralDeepLink {
    sealed interface Target {
        data class Subscription(val url: String) : Target
        data class Config(val url: String, val fragment: String?) : Target
    }

    private val configSchemes = setOf(
        "http", "https", "vless", "vmess", "trojan", "ss", "socks",
        "hysteria2", "hy2", "tuic", "wireguard", "wg"
    )

    /** Parses uralvpn://install-sub and uralvpn://install-config links. */
    fun fromScheme(host: String?, url: String?, fragment: String?): Target? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val scheme = schemeOf(raw) ?: return null
        return when (host?.lowercase()) {
            "install-sub" -> if (scheme == "https") Target.Subscription(raw) else null
            "install-config" -> if (scheme in configSchemes) {
                Target.Config(raw, fragment?.takeIf { it.isNotBlank() })
            } else {
                null
            }
            else -> null
        }
    }

    /** Parses text shared into the app through ACTION_SEND. */
    fun fromShared(text: String?): Target? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val scheme = schemeOf(raw) ?: return null
        return if (scheme in configSchemes) Target.Config(raw, null) else null
    }

    private fun schemeOf(value: String): String? =
        runCatching { URI(value).scheme?.lowercase() }.getOrNull()
}
