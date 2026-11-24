package com.v2ray.ang.handler

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * UralVPN: FakeDNS for custom Xray profiles.
 *
 * The old client used a fake-ip DNS server, so the browser got an answer
 * immediately and the real name travelled inside the tunnel. The Xray
 * subscription still resolves every name through DoH, which adds a round
 * trip to the first request. This keeps the subscription's real resolvers
 * for explicit domain rules, but makes FakeDNS the default resolver.
 */
object UralFastDns {
    private const val FAKE_DNS = "fakedns"
    private const val IP_POOL = "198.18.0.0/15"
    private const val POOL_SIZE = 10000

    fun apply(root: JsonObject) {
        ensurePool(root)
        ensureDnsServer(root)
        ensureSniffing(root)
    }

    private fun ensurePool(root: JsonObject) {
        if (root.has("fakedns")) return
        val pool = JsonObject().apply {
            addProperty("ipPool", IP_POOL)
            addProperty("poolSize", POOL_SIZE)
        }
        root.add("fakedns", JsonArray().apply { add(pool) })
    }

    private fun ensureDnsServer(root: JsonObject) {
        val dns = root.get("dns") as? JsonObject
            ?: JsonObject().also { root.add("dns", it) }
        val servers = dns.get("servers") as? JsonArray
            ?: JsonArray().also { dns.add("servers", it) }
        if (servers.any { server ->
                server.isJsonObject &&
                    server.asJsonObject.get("address")?.takeIf { it.isJsonPrimitive }?.asString == FAKE_DNS
            }
        ) {
            return
        }
        val first = JsonObject().apply { addProperty("address", FAKE_DNS) }
        val reordered = JsonArray().apply {
            add(first)
            for (server in servers) add(server)
        }
        dns.add("servers", reordered)
    }

    private fun ensureSniffing(root: JsonObject) {
        val inbounds = root.get("inbounds") as? JsonArray ?: return
        for (element in inbounds) {
            val inbound = element as? JsonObject ?: continue
            val sniffing = inbound.get("sniffing") as? JsonObject
                ?: JsonObject().also { inbound.add("sniffing", it) }
            sniffing.addProperty("enabled", true)
            val overrides = sniffing.get("destOverride") as? JsonArray
                ?: JsonArray().also { sniffing.add("destOverride", it) }
            if (overrides.none { item ->
                    item.isJsonPrimitive && item.asJsonPrimitive.isString && item.asString == FAKE_DNS
                }
            ) {
                overrides.add(FAKE_DNS)
            }
        }
    }
}
