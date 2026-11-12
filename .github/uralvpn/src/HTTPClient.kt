package io.nekohasekai.sfa.utils

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.ktx.unwrap
import java.io.Closeable

// UralVPN: the Remnawave panel answers a subscription request with a stub config
// ("App not supported", server 0.0.0.0:1) unless the request carries a device id (x-hwid).
// Send the same headers as other supported clients and a User-Agent the panel maps to sing-box JSON.
class HTTPClient : Closeable {
    companion object {
        val userAgent by lazy {
            "SFA/1.13.0 (Android ${Build.VERSION.RELEASE})"
        }

        @SuppressLint("HardwareIds")
        val hwid: String by lazy {
            runCatching {
                Settings.Secure.getString(Application.application.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "uralvpn-${Build.MODEL}"
        }
    }

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        val request = client.newRequest()
        request.setUserAgent(userAgent)
        request.setHeader("x-hwid", hwid)
        request.setHeader("x-device-os", "Android")
        request.setHeader("x-ver-os", Build.VERSION.RELEASE)
        request.setHeader("x-device-model", Build.MODEL)
        request.setURL(url)
        val response = request.execute()
        return response.content.unwrap
    }

    override fun close() {
        client.close()
    }
}
