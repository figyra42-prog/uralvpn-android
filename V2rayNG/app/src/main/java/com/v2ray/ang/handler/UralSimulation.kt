package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils

/**
 * Developer-only simulation of a block: direct locations are given a closed port, while CDN
 * locations keep working. Handles both Xray JSON subscriptions and base64 VLESS link lists.
 */
object UralSimulation {
    private const val DIRECT_TAG_PREFIX = "auto"
    private const val CLOSED_PORT = 1

    fun apply(content: String): String =
        apply(content, MmkvManager.decodeSettingsBool(AppConfig.PREF_SIMULATE_BLOCK, false))

    internal fun apply(content: String, enabled: Boolean): String {
        if (!enabled) return content
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) return applyToJson(content)
        if (trimmed.contains("vless://", ignoreCase = true)) return applyToLinks(trimmed)
        val decoded = Utils.decode(content)
        return if (decoded.contains("vless://", ignoreCase = true)) {
            Utils.encode(applyToLinks(decoded))
        } else {
            content
        }
    }

    internal fun applyToJson(content: String): String = runCatching {
        val root = JsonParser.parseString(content).asJsonObject
        val outbounds = root.getAsJsonArray("outbounds") ?: return@runCatching content
        var changed = false
        for (element in outbounds) {
            val outbound = element.asJsonObject
            if (outbound.get("protocol")?.takeIf { !it.isJsonNull }?.asString != "vless") continue
            val tag = outbound.get("tag")?.takeIf { !it.isJsonNull }?.asString ?: continue
            if (!tag.startsWith(DIRECT_TAG_PREFIX)) continue
            val vnext = outbound.getAsJsonObject("settings")
                ?.getAsJsonArray("vnext")?.firstOrNull()?.asJsonObject ?: continue
            vnext.addProperty("port", CLOSED_PORT)
            changed = true
        }
        if (changed) root.toString() else content
    }.getOrDefault(content)

    internal fun applyToLinks(text: String): String =
        text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("vless://", ignoreCase = true)) {
                line
            } else {
                val remark = Utils.decodeURIComponent(trimmed.substringAfter('#', ""))
                if (isCdnOrAutopilot(remark)) line else setClosedPort(line)
            }
        }

    private fun isCdnOrAutopilot(remark: String): Boolean {
        val value = remark.lowercase()
        return "мобильн" in value || "моб" in value || "📱" in remark ||
            "cdn" in value || "автопилот" in value || "autopilot" in value ||
            "автовыбор" in value
    }

    private fun setClosedPort(line: String): String {
        val at = line.indexOf('@')
        if (at < 0) return line
        val hostStart = at + 1
        if (hostStart >= line.length) return line
        val hostEnd: Int
        val portStart: Int
        if (line[hostStart] == '[') {
            val close = line.indexOf(']', hostStart)
            if (close < 0 || close + 1 >= line.length || line[close + 1] != ':') return line
            hostEnd = close
            portStart = close + 2
        } else {
            hostEnd = line.indexOf(':', hostStart)
            if (hostEnd < 0) return line
            portStart = hostEnd + 1
        }
        var portEnd = portStart
        while (portEnd < line.length && line[portEnd].isDigit()) portEnd++
        if (portEnd == portStart) return line
        return line.replaceRange(portStart, portEnd, CLOSED_PORT.toString())
    }
}
