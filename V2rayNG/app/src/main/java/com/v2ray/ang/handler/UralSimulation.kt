package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
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

    /** True when the switch is on and this simple profile is a direct location, not CDN/autopilot. */
    fun isBlocked(profile: ProfileItem): Boolean =
        isEnabled() && !profile.configType.isComplexType() && !isProtected(profile.remarks)

    internal fun applyToProfile(profile: ProfileItem, enabled: Boolean): ProfileItem =
        if (!enabled || isProtected(profile.remarks)) profile
        else profile.copy(serverPort = CLOSED_PORT)

    internal fun isProtected(remark: String): Boolean {
        val value = remark.lowercase()
        return "мобильн" in value || "моб" in value || "📱" in remark ||
            "cdn" in value || "автопилот" in value || "autopilot" in value ||
            "автовыбор" in value || "auto balancer" in value || "two tier" in value
    }
}
