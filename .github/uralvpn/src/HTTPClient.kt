package io.nekohasekai.sfa.utils

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.ktx.unwrap
import org.json.JSONObject
import java.io.Closeable

// UralVPN:
// - the Remnawave panel answers a subscription request with a stub config ("App not supported",
//   server 0.0.0.0:1) unless the request carries a device id (x-hwid), so device headers are sent;
// - the panel's sing-box profile has the direct servers only, its CDN (XHTTP) servers come in the
//   Xray JSON subscription: the same link is fetched again as an Xray client and both are merged in
//   the core (direct servers -> CDN autopilot, like the panel's Xray "Two Tier" template);
// - the location descriptions the panel gives to the Happ client are saved next to the profile.
class HTTPClient : Closeable {
    companion object {
        private const val TAG = "HTTPClient"
        private const val XRAY_USER_AGENT = "Happ/3.5.0"

        val userAgent by lazy {
            "SFA/1.13.0 (Android ${Build.VERSION.RELEASE})"
        }

        val hwid: String by lazy {
            runCatching {
                @SuppressLint("HardwareIds")
                val id = Settings.Secure.getString(Application.application.contentResolver, Settings.Secure.ANDROID_ID)
                id
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "uralvpn-${Build.MODEL}"
        }

        fun descriptionsFile(url: String): java.io.File {
            val name = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16)
            return java.io.File(Application.application.filesDir, "ural-desc-$name.json")
        }

        fun statusFile(url: String): java.io.File {
            val name = descriptionsFile(url).name.removePrefix("ural-desc-").removeSuffix(".json")
            return java.io.File(Application.application.filesDir, "ural-status-$name.txt")
        }

        // Why the CDN servers are missing (empty when everything is fine); shown on the home screen.
        private fun saveStatus(url: String, text: String) {
            runCatching { statusFile(url).writeText(text) }
        }

        private fun saveDescriptions(url: String, json: String) {
            descriptionsFile(url).writeText(json)
        }

        private fun isSubscriptionHost(url: String): Boolean = !url.contains("github.com") && !url.contains("githubusercontent.com")

        private fun isSingBoxProfile(content: String): Boolean = runCatching {
            val json = JSONObject(content)
            json.has("outbounds") && json.has("inbounds")
        }.getOrDefault(false)
    }

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    private fun fetch(url: String, userAgent: String): String {
        val request = client.newRequest()
        request.setUserAgent(userAgent)
        if (isSubscriptionHost(url)) {
            request.setHeader("x-hwid", hwid)
            request.setHeader("x-device-os", "Android")
            request.setHeader("x-ver-os", Build.VERSION.RELEASE)
            request.setHeader("x-device-model", Build.MODEL)
        }
        request.setURL(url)
        val response = request.execute()
        return response.content.unwrap
    }

    // The plain connection through the phone's own network, not through the VPN: it reaches the
    // panel even when the selected location is dead and the tunnel carries nothing.
    private fun fetchOutside(url: String, userAgent: String): String {
        val manager = Application.application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager
        var network: android.net.Network? = null
        if (manager != null) {
            for (candidate in manager.allNetworks) {
                val capabilities = manager.getNetworkCapabilities(candidate) ?: continue
                if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue
                if (!capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                if (!capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
                network = candidate
                break
            }
        }
        val underlying = network
        val connection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && underlying != null) {
            underlying.openConnection(java.net.URL(url))
        } else {
            java.net.URL(url).openConnection()
        } as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            if (isSubscriptionHost(url)) {
                connection.setRequestProperty("x-hwid", hwid)
                connection.setRequestProperty("x-device-os", "Android")
                connection.setRequestProperty("x-ver-os", Build.VERSION.RELEASE)
                connection.setRequestProperty("x-device-model", Build.MODEL)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return text
        } finally {
            connection.disconnect()
        }
    }

    // The subscription is fetched outside the tunnel first: a dead location then cannot lock the
    // profile, and an update does not depend on the tunnel at all. The tunnel is the fallback, for
    // networks where the panel's domain is blocked on the way out; a third try covers a lost packet.
    private fun fetchSubscription(url: String, userAgent: String): String {
        var lastError: Throwable? = null
        for (attempt in 0 until 3) {
            try {
                return if (attempt == 1) fetch(url, userAgent) else fetchOutside(url, userAgent)
            } catch (error: Throwable) {
                lastError = error
                Log.w(TAG, "subscription attempt ${attempt + 1} (${if (attempt == 1) "tunnel" else "outside"})", error)
                if (attempt < 2 && android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                    Thread.sleep(400)
                }
            }
        }
        throw lastError ?: java.io.IOException("subscription is not reachable")
    }

    fun getString(url: String): String {
        val content = if (isSubscriptionHost(url)) fetchSubscription(url, userAgent) else fetch(url, userAgent)
        if (!isSubscriptionHost(url) || !isSingBoxProfile(content)) {
            return content
        }
        val merged = runCatching {
            val xray = fetchSubscription(url, XRAY_USER_AGENT)
            runCatching { saveDescriptions(url, Libbox.uralvpnDescriptions(xray)) }
            Libbox.uralvpnMergeSubscription(content, xray)
        }
        merged.onFailure {
            Log.w(TAG, "merge CDN servers", it)
            saveStatus(url, "CDN не добавлены: ${it.message ?: it.javaClass.simpleName}".take(300))
        }
        merged.onSuccess {
            saveStatus(url, if (it.contains("CDN")) "" else "В подписке нет CDN-серверов (панель их не отдала)")
        }
        return io.nekohasekai.sfa.bg.UralTools.applyRouting(io.nekohasekai.sfa.bg.UralTools.applyDns(io.nekohasekai.sfa.bg.UralTools.applySimulation(merged.getOrDefault(content))))
    }

    override fun close() {
        client.close()
    }
}
