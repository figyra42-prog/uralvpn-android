package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem

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

    internal fun applyToProfile(profile: ProfileItem, enabled: Boolean): ProfileItem =
        if (!enabled || isProtected(profile.remarks)) profile
        else profile.copy(serverPort = CLOSED_PORT)

    internal fun isProtected(remark: String): Boolean {
        val value = remark.lowercase()
        return "мобильн" in value || "моб" in value || "📱" in remark ||
            "cdn" in value || "автопилот" in value || "autopilot" in value ||
            "автовыбор" in value
    }
}
