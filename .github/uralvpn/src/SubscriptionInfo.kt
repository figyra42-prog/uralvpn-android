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

object SubInfoCache {
    val map = ConcurrentHashMap<Long, SubInfo>()
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
