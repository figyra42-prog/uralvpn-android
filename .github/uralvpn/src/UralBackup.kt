package io.nekohasekai.sfa.bg

import android.content.Context
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// UralVPN: резервная копия профилей и настроек — JSON-файл, который можно сохранить и восстановить
// после переустановки приложения.
object UralBackup {
    private const val VERSION = 1

    suspend fun build(): String {
        val profiles = ProfileManager.list()
        val array = JSONArray()
        for (profile in profiles) {
            val item = JSONObject()
            item.put("name", profile.name)
            profile.icon?.let { item.put("icon", it) }
            val typed = profile.typed
            when (typed.type) {
                TypedProfile.Type.Remote -> {
                    item.put("type", "remote")
                    item.put("url", typed.remoteURL)
                    item.put("auto_update", typed.autoUpdate)
                    item.put("auto_update_interval", typed.autoUpdateInterval)
                }
                else -> {
                    item.put("type", "local")
                    item.put("content", runCatching { File(typed.path).readText() }.getOrDefault(""))
                }
            }
            array.put(item)
        }
        val settings = JSONObject()
        settings.put("theme_mode", prefs().getString("theme_mode", "auto"))
        settings.put("autostart", prefs().getBoolean("autostart", false))
        settings.put("kill_switch", prefs().getBoolean("kill_switch", false))
        settings.put("sort_by_ping", prefs().getBoolean("sort_by_ping", false))
        val root = JSONObject()
        root.put("app", "UralVPN")
        root.put("version", VERSION)
        root.put("profiles", array)
        root.put("settings", settings)
        return root.toString(2)
    }

    /** Возвращает число добавленных профилей; профили с тем же адресом не дублируются. */
    suspend fun restore(text: String): Int {
        val root = JSONObject(text)
        if (root.optString("app") != "UralVPN") throw IllegalArgumentException("это не файл резервной копии UralVPN")
        val existing = ProfileManager.list()
        val existingUrls = existing.mapNotNull { it.typed.remoteURL.takeIf { url -> url.isNotEmpty() } }.toSet()
        val existingNames = existing.map { it.name }.toSet()
        val settings = root.optJSONObject("settings")
        if (settings != null) {
            val editor = prefs().edit()
            settings.optString("theme_mode", "").takeIf { it.isNotEmpty() }?.let { editor.putString("theme_mode", it) }
            if (settings.has("autostart")) editor.putBoolean("autostart", settings.getBoolean("autostart"))
            if (settings.has("kill_switch")) editor.putBoolean("kill_switch", settings.getBoolean("kill_switch"))
            if (settings.has("sort_by_ping")) editor.putBoolean("sort_by_ping", settings.getBoolean("sort_by_ping"))
            editor.apply()
        }
        val array = root.optJSONArray("profiles") ?: return 0
        var added = 0
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val name = item.optString("name", "UralVPN")
            val typed = TypedProfile()
            when (item.optString("type")) {
                "remote" -> {
                    val url = item.optString("url", "")
                    if (url.isEmpty() || url in existingUrls) continue
                    typed.type = TypedProfile.Type.Remote
                    typed.remoteURL = url
                    typed.autoUpdate = item.optBoolean("auto_update", false)
                    typed.autoUpdateInterval = item.optInt("auto_update_interval", 60)
                }
                else -> {
                    val content = item.optString("content", "")
                    if (content.isEmpty()) continue
                    typed.type = TypedProfile.Type.Local
                    typed.path = newLocalPath()
                    File(typed.path).parentFile?.mkdirs()
                    File(typed.path).writeText(content)
                }
            }
            if (name in existingNames) continue
            val profile = Profile(name = name, icon = if (item.has("icon")) item.getString("icon") else null, typed = typed)
            ProfileManager.create(profile)
            added += 1
        }
        return added
    }

    private fun prefs() = Application.application.getSharedPreferences("ural_ui", Context.MODE_PRIVATE)

    private fun newLocalPath(): String {
        val dir = File(Application.application.filesDir, "profiles")
        dir.mkdirs()
        return File(dir, "backup-" + System.currentTimeMillis() + ".json").absolutePath
    }
}
