package com.v2ray.ang.handler

import com.google.gson.JsonParser

/** Reads the proxy endpoint of an already saved custom Xray config. */
object CustomEndpoint {
    data class Endpoint(val address: String, val port: Int)

    fun fromRaw(content: String): Endpoint? {
        if (content.isBlank()) return null
        val root = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull() ?: return null
        val outbounds = root.getAsJsonArray("outbounds") ?: return null
        for (element in outbounds) {
            val outbound = element.asJsonObject
            val protocol = outbound.get("protocol")?.takeIf { !it.isJsonNull }?.asString
            if (protocol == "freedom" || protocol == "blackhole") continue
            val settings = outbound.getAsJsonObject("settings") ?: continue
            var address = settings.get("address")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
            var port = settings.get("port")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asInt
            if (address.isNullOrBlank() || port == null) {
                val vnext = settings.getAsJsonArray("vnext")?.firstOrNull()?.asJsonObject
                address = vnext?.get("address")?.takeIf { !it.isJsonNull }?.asString
                port = vnext?.get("port")?.takeIf { !it.isJsonNull }?.asInt
            }
            if (!address.isNullOrBlank() && port != null && port > 0) {
                return Endpoint(address, port)
            }
        }
        return null
    }
}
