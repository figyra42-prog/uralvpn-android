package com.v2ray.ang.service

import com.v2ray.ang.AppConfig
import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * UralVPN: minimal SOCKS5 CONNECT probe used by [UralWatchdog].
 *
 * A successful CONNECT through the core's local SOCKS inbound proves that the core accepted a
 * request and established an outbound connection to the probe target. The probe target is a
 * public IP so it is routed through the selected server, not treated as a Russian destination.
 */
internal object Socks5Probe {
    private const val VERSION = 0x05
    private const val METHOD_NO_AUTH = 0x00
    private const val METHOD_USER_PASS = 0x02
    private const val CMD_CONNECT = 0x01
    private const val ATYP_IPV4 = 0x01
    private const val PROBE_PORT = 443
    private val PROBE_HOST = byteArrayOf(1, 1, 1, 1)

    /**
     * Opens a SOCKS5 CONNECT to 1.1.1.1:443 through [port] on the loopback interface.
     *
     * @return true when the proxy accepted the CONNECT request.
     */
    fun check(
        port: Int,
        username: String?,
        password: String?,
        timeoutMs: Int = 3000,
    ): Boolean {
        if (port !in 1..65535 || timeoutMs <= 0) return false
        return try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), timeoutMs)
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                val useAuth = !username.isNullOrEmpty() && !password.isNullOrEmpty()

                if (useAuth) {
                    output.write(
                        byteArrayOf(
                            VERSION.toByte(),
                            2,
                            METHOD_NO_AUTH.toByte(),
                            METHOD_USER_PASS.toByte()
                        )
                    )
                    output.flush()
                    val method = readExact(input, 2)
                    if (method[0].toInt() != VERSION ||
                        method[1].toInt() != METHOD_USER_PASS
                    ) {
                        return false
                    }

                    val user = username!!.toByteArray(Charsets.UTF_8)
                    val pass = password!!.toByteArray(Charsets.UTF_8)
                    if (user.size > 255 || pass.size > 255) return false
                    output.write(
                        byteArrayOf(1, user.size.toByte()) + user +
                                byteArrayOf(pass.size.toByte()) + pass
                    )
                    output.flush()
                    val auth = readExact(input, 2)
                    if (auth[0].toInt() != 1 || auth[1].toInt() != 0) return false
                } else {
                    output.write(
                        byteArrayOf(VERSION.toByte(), 1, METHOD_NO_AUTH.toByte())
                    )
                    output.flush()
                    val method = readExact(input, 2)
                    if (method[0].toInt() != VERSION ||
                        method[1].toInt() != METHOD_NO_AUTH
                    ) {
                        return false
                    }
                }

                output.write(
                    byteArrayOf(
                        VERSION.toByte(),
                        CMD_CONNECT.toByte(),
                        0,
                        ATYP_IPV4.toByte(),
                        PROBE_HOST[0],
                        PROBE_HOST[1],
                        PROBE_HOST[2],
                        PROBE_HOST[3],
                        (PROBE_PORT shr 8).toByte(),
                        PROBE_PORT.toByte()
                    )
                )
                output.flush()
                val reply = readExact(input, 4)
                reply[0].toInt() == VERSION && reply[1].toInt() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read < 0) throw EOFException("Unexpected end of SOCKS stream")
            offset += read
        }
        return buffer
    }
}
