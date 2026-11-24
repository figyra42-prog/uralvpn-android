package com.v2ray.ang.handler

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType

/**
 * Developer-only simulation of a block: direct locations are given a closed port, while CDN
 * locations and the autopilot keep working. Applied when the core config is built, so it also
 * works for profiles saved before the switch was enabled and needs no subscription refresh.
 */
object UralSimulation {
    private const val CLOSED_PORT = "1"

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SIMULATE_BLOCK, false)

    fun applyToProfile(profile: ProfileItem): ProfileItem =
        applyToProfile(profile, isEnabled())

    /** True when the switch is on and this profile is a direct location, not CDN/autopilot. */
    fun isBlocked(profile: ProfileItem): Boolean =
        isEnabled() && !isProtected(profile.remarks) &&
            (profile.configType == EConfigType.CUSTOM || !profile.configType.isComplexType())

    internal fun applyToProfile(profile: ProfileItem, enabled: Boolean): ProfileItem =
        if (!enabled || isProtected(profile.remarks)) profile
        else profile.copy(serverPort = CLOSED_PORT)

    internal fun isProtected(remark: String): Boolean {
        val value = remark.lowercase()
        return "мобильн" in value || "моб" in value || "📱" in remark ||
            "cdn" in value || "автопилот" in value || "autopilot" in value ||
            "автовыбор" in value || "auto balancer" in value || "two tier" in value
    }

    /** Closes the proxy port inside a raw custom Xray config for a direct location. */
    fun applyToCustomConfig(content: String, remark: String): String =
        applyToCustomConfig(content, remark, isEnabled())

    internal fun applyToCustomConfig(content: String, remark: String, enabled: Boolean): String {
        if (!enabled || isProtected(remark)) return content
        return runCatching {
            val root = JsonParser.parseString(content).asJsonObject
            val outbounds = root.getAsJsonArray("outbounds") ?: return@runCatching content
            var changed = false
            for (element in outbounds) {
                val outbound = element.asJsonObject
                val protocol = outbound.get("protocol")?.takeIf { !it.isJsonNull }?.asString
                if (protocol == "freedom" || protocol == "blackhole") continue
                val vnext = outbound.getAsJsonObject("settings")
                    ?.getAsJsonArray("vnext")
                    ?.firstOrNull()?.asJsonObject ?: continue
                vnext.addProperty("port", CLOSED_PORT.toInt())
                changed = true
            }
            if (changed) root.toString() else content
        }.getOrDefault(content)
    }
}
