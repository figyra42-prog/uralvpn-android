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

    // UralVPN: просим подтверждение перед отключением, чтобы трафик не ушёл напрямую.
    var killSwitch: Boolean
        get() = prefs.getBoolean("kill_switch", false)
        set(value) {
            prefs.edit().putBoolean("kill_switch", value).apply()
        }

    // Системная защита Android: «постоянная VPN» и «блокировать соединения без VPN».
    fun alwaysOnState(context: Context): Pair<Boolean, Boolean> = runCatching {
        val resolver = context.contentResolver
        val app = android.provider.Settings.Secure.getString(resolver, "always_on_vpn_app")
        val lockdown = android.provider.Settings.Secure.getString(resolver, "always_on_vpn_lockdown")
        (app == context.packageName) to (lockdown == "1")
    }.getOrDefault(false to false)

    // UralVPN: DNS для иностранных сайтов (тег cf-dns в профиле подписки).
    var dnsMode: String
        get() = prefs.getString("dns_mode", "cloudflare") ?: "cloudflare"
        set(value) {
            prefs.edit().putString("dns_mode", value).apply()
        }

    var dnsCustom: String
        get() = prefs.getString("dns_custom", "") ?: ""
        set(value) {
            prefs.edit().putString("dns_custom", value).apply()
        }

    fun dnsTarget(): Pair<String, String>? = when (dnsMode) {
        "google" -> "tls" to "8.8.8.8"
        "quad9" -> "tls" to "9.9.9.9"
        "adguard" -> "https" to "https://dns.adguard-dns.com/dns-query"
        "custom" -> {
            val value = dnsCustom.trim()
            when {
                value.startsWith("https://") -> "https" to value
                value.startsWith("tls://") -> "tls" to value.removePrefix("tls://")
                value.startsWith("udp://") -> "udp" to value.removePrefix("udp://")
                value.isNotEmpty() -> "udp" to value
                else -> null
            }
        }
        else -> "tls" to "1.1.1.1"
    }

    // UralVPN: свои правила маршрутизации — домены, которые всегда напрямую или всегда через VPN.
    var directDomains: String
        get() = prefs.getString("direct_domains", "") ?: ""
        set(value) {
            prefs.edit().putString("direct_domains", value).apply()
        }

    var proxyDomains: String
        get() = prefs.getString("proxy_domains", "") ?: ""
        set(value) {
            prefs.edit().putString("proxy_domains", value).apply()
        }

    private fun splitDomains(value: String): List<String> =
        value.split('\n', ',', ' ')
            .map { it.trim().removePrefix("*.").removePrefix(".") }
            .filter { it.isNotEmpty() && !it.contains('/') }
            .distinct()

    // Вставляет правила в начало route.rules: принудительно через VPN — раньше всего, затем напрямую.
    fun applyRouting(content: String): String = runCatching {
        val proxy = splitDomains(proxyDomains)
        val direct = splitDomains(directDomains)
        if (proxy.isEmpty() && direct.isEmpty()) return content
        val root = JSONObject(content)
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val rules = route.optJSONArray("rules") ?: JSONArray().also { route.put("rules", it) }
        val outbounds = root.optJSONArray("outbounds") ?: return content
        var selector = ""
        for (i in 0 until outbounds.length()) {
            val item = outbounds.optJSONObject(i) ?: continue
            if (item.optString("type") == "selector") {
                selector = item.optString("tag")
                break
            }
        }
        if (selector.isEmpty()) return content
        val merged = JSONArray()
        if (proxy.isNotEmpty()) {
            merged.put(JSONObject().put("domain_suffix", JSONArray(proxy)).put("outbound", selector))
        }
        if (direct.isNotEmpty()) {
            merged.put(JSONObject().put("domain_suffix", JSONArray(direct)).put("outbound", "direct"))
        }
        for (i in 0 until rules.length()) merged.put(rules.get(i))
        route.put("rules", merged)
        root.toString()
    }.getOrDefault(content)

    // Заменяет только сервер тега cf-dns: локальный DNS и правила маршрутизации не трогаются.
    // Для DoH/DoT с доменным именем нужен domain_resolver — берём локальный DNS профиля.
    fun applyDns(content: String): String = runCatching {
        val target = dnsTarget() ?: return content
        var type = target.first
        var server = target.second
        var path: String? = null
        if (server.startsWith("https://")) {
            val uri = android.net.Uri.parse(server)
            server = uri.host ?: return content
            path = uri.path?.takeIf { it.isNotEmpty() } ?: "/dns-query"
            type = "https"
        } else if (server.startsWith("tls://")) {
            server = server.removePrefix("tls://")
            type = "tls"
        }
        val root = JSONObject(content)
        val dns = root.optJSONObject("dns") ?: return content
        val servers = dns.optJSONArray("servers") ?: return content
        val isIp = Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$").matches(server) || server.contains(':')
        var changed = false
        for (i in 0 until servers.length()) {
            val item = servers.optJSONObject(i) ?: continue
            if (item.optString("tag") != "cf-dns") continue
            item.put("type", type)
            item.put("server", server)
            if (path != null) item.put("path", path) else item.remove("path")
            if (!isIp) item.put("domain_resolver", "local") else item.remove("domain_resolver")
            changed = true
        }
        if (changed) root.toString() else content
    }.getOrDefault(content)

    // UralVPN: IPv6 в туннеле по умолчанию выключен. У большинства нод нет выхода в IPv6, и
    // приложения получают адреса, по которым соединение виснет до таймаута.
    var ipv6Enabled: Boolean
        get() = prefs.getBoolean("ipv6_enabled", false)
        set(value) {
            prefs.edit().putBoolean("ipv6_enabled", value).apply()
        }

    fun applyIpv6(content: String): String {
        if (ipv6Enabled) return content
        return runCatching {
            val root = JSONObject(content)
            var changed = false
            // The TUN keeps its IPv6 address on purpose: without it the apps' IPv6 traffic leaves
            // the VPN directly, which leaks the real address. The blocking happens inside the tunnel.
            root.optJSONObject("dns")?.optJSONArray("servers")?.let { servers ->
                for (i in 0 until servers.length()) {
                    val server = servers.optJSONObject(i) ?: continue
                    if (server.optString("type") != "fakeip") continue
                    if (server.has("inet6_range")) {
                        server.remove("inet6_range")
                        changed = true
                    }
                }
            }
            val route = root.optJSONObject("route")
            if (route != null) {
                val rules = route.optJSONArray("rules") ?: JSONArray().also { route.put("rules", it) }
                var blocked = false
                for (i in 0 until rules.length()) {
                    val rule = rules.optJSONObject(i) ?: continue
                    if (rule.optString("action") != "reject") continue
                    val cidr = rule.optJSONArray("ip_cidr") ?: continue
                    for (j in 0 until cidr.length()) if (cidr.optString(j) == "::/0") blocked = true
                }
                if (!blocked) {
                    rules.put(JSONObject().put("action", "reject").put("ip_cidr", JSONArray().put("::/0")))
                    changed = true
                }
            }
            if (changed) root.toString() else content
        }.getOrDefault(content)
    }

    fun openVpnSettings(context: Context) {
        runCatching {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
        }
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

    // Разрешение «не оптимизировать батарею»: без него Android может усыплять VPN.
    fun batteryOptimizationIgnored(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
}
