package io.nekohasekai.sfa.compose.screen.dashboard

import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import io.nekohasekai.sfa.utils.HTTPClient
import org.json.JSONObject

// UralVPN: account information the panel publishes in the headers of the subscription URL
// (Subscription-Userinfo, Profile-Title, announce), shown on the home screen like in the old app.
data class SubInfo(
    val title: String?,
    val used: Long,
    val total: Long,
    val expireSeconds: Long,
    val announce: String?,
    val supportUrl: String? = null,
    val webUrl: String? = null,
) {
    val fraction: Float? get() = if (total > 0) (used.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
}

private fun safeLink(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value.length > 2048) return null
    val scheme = runCatching { java.net.URI(value).scheme?.lowercase(Locale.ROOT) }.getOrNull() ?: return null
    return value.takeIf { scheme == "http" || scheme == "https" || scheme == "tg" }
}

// The last known account info is kept in memory and on disk, so the card is shown at once after the
// app starts, even before (or without) a successful network request.
object SubInfoCache {
    val map = ConcurrentHashMap<Long, SubInfo>()

    private val prefs get() = io.nekohasekai.sfa.Application.application.getSharedPreferences("ural_subinfo", android.content.Context.MODE_PRIVATE)

    fun get(id: Long): SubInfo? = map[id] ?: load(id)?.also { map[id] = it }

    fun put(id: Long, info: SubInfo) {
        map[id] = info
        runCatching {
            val json = JSONObject()
                .put("title", info.title ?: JSONObject.NULL)
                .put("used", info.used)
                .put("total", info.total)
                .put("expire", info.expireSeconds)
                .put("announce", info.announce ?: JSONObject.NULL)
                .put("support", info.supportUrl ?: JSONObject.NULL)
                .put("web", info.webUrl ?: JSONObject.NULL)
            prefs.edit().putString("info_$id", json.toString()).apply()
        }
    }

    private fun load(id: Long): SubInfo? = runCatching {
        val json = JSONObject(prefs.getString("info_$id", null) ?: return null)
        fun text(key: String) = if (json.isNull(key)) null else json.getString(key)
        SubInfo(
            title = text("title"),
            used = json.getLong("used"),
            total = json.getLong("total"),
            expireSeconds = json.getLong("expire"),
            announce = text("announce"),
            supportUrl = text("support"),
            webUrl = text("web"),
        )
    }.getOrNull()
}

private fun decodeText(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val decoded = when {
        value.startsWith("base64:", ignoreCase = true) ->
            runCatching { String(Base64.decode(value.substring(7).trim(), Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(value)

        value.contains('%') -> runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        else -> value
    }
    return decoded.trim().take(1000).ifEmpty { null }
}

fun fetchSubInfo(url: String): SubInfo? = runCatching {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", HTTPClient.userAgent)
        connection.setRequestProperty("x-hwid", HTTPClient.hwid)
        connection.setRequestProperty("x-device-os", "Android")
        connection.setRequestProperty("x-ver-os", android.os.Build.VERSION.RELEASE)
        connection.setRequestProperty("x-device-model", android.os.Build.MODEL)
        connection.responseCode
        val fields = HashMap<String, Long>()
        connection.getHeaderField("subscription-userinfo")?.split(';')?.forEach { part ->
            val separator = part.indexOf('=')
            if (separator > 0) {
                val number = part.substring(separator + 1).trim().toDoubleOrNull()
                if (number != null) fields[part.substring(0, separator).trim().lowercase(Locale.ROOT)] = number.toLong()
            }
        }
        val title = decodeText(connection.getHeaderField("profile-title"))
        val announce = decodeText(connection.getHeaderField("announce"))
        if (fields.isEmpty() && title == null && announce == null) {
            null
        } else {
            val expire = fields["expire"] ?: 0L
            SubInfo(
                title = title,
                used = (fields["upload"] ?: 0L) + (fields["download"] ?: 0L),
                total = fields["total"] ?: 0L,
                expireSeconds = if (expire >= 100_000_000_000L) expire / 1000 else expire,
                announce = announce,
                supportUrl = safeLink(connection.getHeaderField("support-url")),
                webUrl = safeLink(connection.getHeaderField("profile-web-page-url")),
            )
        }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

// Tags of the servers in the profile's selector, so the list is shown also while the VPN is off.
fun readSelectorNames(path: String): List<String> = runCatching {
    val outbounds = JSONObject(File(path).readText()).getJSONArray("outbounds")
    var names = emptyList<String>()
    for (i in 0 until outbounds.length()) {
        val outbound = outbounds.getJSONObject(i)
        if (outbound.optString("type") == "selector") {
            val members = outbound.getJSONArray("outbounds")
            names = (0 until members.length()).map { members.getString(it) }
            break
        }
    }
    names
}.getOrDefault(emptyList())

// Location descriptions the panel gives to the Happ client (saved by HTTPClient at each update).
fun readDescriptions(url: String): Map<String, String> = runCatching {
    val json = JSONObject(HTTPClient.descriptionsFile(url).readText())
    json.keys().asSequence().associateWith { json.getString(it) }
}.getOrDefault(emptyMap())

fun readStatus(url: String): String = runCatching { HTTPClient.statusFile(url).readText().trim() }.getOrDefault("")

fun formatTraffic(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

fun formatExpire(seconds: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(seconds * 1000))
