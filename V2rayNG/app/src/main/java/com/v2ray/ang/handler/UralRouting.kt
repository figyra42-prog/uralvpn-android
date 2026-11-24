package com.v2ray.ang.handler

import com.google.gson.JsonObject

/**
 * UralVPN: keep custom Xray routing on the sniffed domain.
 *
 * The Remnawave templates use IPIfNonMatch, which makes Xray resolve every
 * destination before applying a rule. On a phone that adds a DNS round trip
 * to the first page load. AsIs routes the sniffed domain directly and does
 * not resolve it first.
 */
object UralRouting {
    const val FAST_DOMAIN_STRATEGY = "AsIs"

    fun applyFastRouting(root: JsonObject) {
        val routing = root.get("routing") as? JsonObject
            ?: JsonObject().also { root.add("routing", it) }
        routing.addProperty("domainStrategy", FAST_DOMAIN_STRATEGY)
    }
}
