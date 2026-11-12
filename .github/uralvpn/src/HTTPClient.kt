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
//   the core (direct servers -> CDN autopilot, like the panel's Xray "Two Tier" template).
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

    fun getString(url: String): String {
        val content = fetch(url, userAgent)
        if (!isSubscriptionHost(url) || !isSingBoxProfile(content)) {
            return content
        }
        return runCatching {
            val xray = fetch(url, XRAY_USER_AGENT)
            Libbox.uralvpnMergeSubscription(content, xray)
        }.onFailure {
            Log.w(TAG, "merge CDN servers", it)
        }.getOrDefault(content)
    }

    override fun close() {
        client.close()
    }
}
