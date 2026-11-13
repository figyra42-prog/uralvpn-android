package io.nekohasekai.sfa.bg

import android.content.Context
import android.net.Uri
import android.widget.Toast
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

// UralVPN: background subscription refresh and the "simulate blocking of direct servers" switch.
object UralTools {
    private const val DIRECT_TAG = "🚀 Прямые"
    private const val REFRESH_MINUTES = 360

    private val prefs get() = Application.application.getSharedPreferences("ural_ui", Context.MODE_PRIVATE)

    var simulateBlock: Boolean
        get() = prefs.getBoolean("simulate_block", false)
        set(value) {
            prefs.edit().putBoolean("simulate_block", value).apply()
        }

    // The link of the bot button "Add to UralVPN" (uralvpn://install-sub?url=<subscription link>) is
    // turned into the link of the import dialog that the app already understands.
    fun normalizeLink(uri: Uri): Uri {
        if (uri.scheme != "uralvpn") return uri
        val raw = uri.getQueryParameter("url")
            ?: uri.toString().substringAfter("url=", "")
        val link = Uri.decode(raw).trim()
        if (!link.startsWith("https://") && !link.startsWith("http://")) return uri
        return Uri.parse("sing-box://import-remote-profile?url=" + Uri.encode(link) + "#" + Uri.encode("UralVPN"))
    }

    // The system scheduler (WorkManager, already in the app) refreshes the subscription every
    // REFRESH_MINUTES minutes in the background, the refresh goes through HTTPClient (device headers,
    // CDN merge). It is switched on once for every remote profile, a later manual change is kept.
    suspend fun ensureAutoUpdate() {
        UralExpiryWork.schedule()
        runCatching {
            var changed = false
            for (profile in ProfileManager.list()) {
                if (profile.typed.type != TypedProfile.Type.Remote) continue
                val key = "auto_update_set_${profile.id}"
                if (prefs.getBoolean(key, false)) continue
                prefs.edit().putBoolean(key, true).apply()
                if (!profile.typed.autoUpdate) {
                    profile.typed.autoUpdate = true
                    profile.typed.autoUpdateInterval = REFRESH_MINUTES
                    ProfileManager.update(profile)
                    changed = true
                }
            }
            if (changed) UpdateProfileWork.reconfigureUpdater()
        }
    }

    // Simulation of a block: the direct servers get a closed port, so the autopilot has to fall
    // back to the CDN servers (and come back when the switch is off).
    fun applySimulation(content: String): String {
        if (!simulateBlock) return content
        return runCatching {
            val json = JSONObject(content)
            val outbounds = json.getJSONArray("outbounds")
            val direct = HashSet<String>()
            for (i in 0 until outbounds.length()) {
                val o = outbounds.getJSONObject(i)
                if (o.optString("tag") == DIRECT_TAG) {
                    val members: JSONArray = o.optJSONArray("outbounds") ?: JSONArray()
                    for (j in 0 until members.length()) direct.add(members.getString(j))
                }
            }
            for (i in 0 until outbounds.length()) {
                val o = outbounds.getJSONObject(i)
                if (o.optString("tag") in direct && o.has("server_port")) o.put("server_port", 1)
            }
            json.toString()
        }.getOrDefault(content)
    }

    // Re-download the subscriptions with the current switch state and reload the running service.
    suspend fun refreshNow(): Boolean = withContext(Dispatchers.IO) {
        var ok = true
        var selectedChanged = false
        val selected = io.nekohasekai.sfa.database.Settings.selectedProfile
        for (profile in ProfileManager.list()) {
            if (profile.typed.type != TypedProfile.Type.Remote) continue
            try {
                val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                Libbox.checkConfig(content)
                val file = File(profile.typed.path)
                if (file.readText() != content) {
                    file.writeText(content)
                    if (profile.id == selected) selectedChanged = true
                }
                profile.typed.lastUpdated = Date()
                ProfileManager.update(profile)
            } catch (e: Exception) {
                ok = false
            }
        }
        if (selectedChanged) runCatching { Libbox.newStandaloneCommandClient().serviceReload() }
        ok
    }

    fun toast(text: String) {
        Toast.makeText(Application.application, text, Toast.LENGTH_LONG).show()
    }
}
