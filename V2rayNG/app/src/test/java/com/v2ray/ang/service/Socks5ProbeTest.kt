package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class Socks5ProbeTest {

    @Test
    fun `no-auth proxy accepts connect`() {
        val result = runProxyCheck { input, output ->
            val greeting = readExact(input, 3)
            assertEquals(5, greeting[0].toInt())
            assertEquals(1, greeting[1].toInt())
            assertEquals(0, greeting[2].toInt())
            output.write(byteArrayOf(5, 0))
            output.flush()

            val connect = readExact(input, 10)
            assertEquals(5, connect[0].toInt())
            assertEquals(1, connect[1].toInt())
            assertEquals(1, connect[3].toInt())
            assertEquals(1, connect[4].toInt())
            assertEquals(1, connect[5].toInt())
            assertEquals(1, connect[6].toInt())
            assertEquals(1, connect[7].toInt())
            assertEquals(1, connect[8].toInt())
            assertEquals(0xBB, connect[9].toInt() and 0xFF)
            output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            output.flush()
        }

        assertTrue(result)
    }

    @Test
    fun `username-password proxy accepts connect`() {
        val result = runProxyCheck(username = "user", password = "pass") { input, output ->
            val greeting = readExact(input, 4)
            assertEquals(5, greeting[0].toInt())
            assertEquals(2, greeting[1].toInt())
            assertEquals(0, greeting[2].toInt())
            assertEquals(2, greeting[3].toInt())
            output.write(byteArrayOf(5, 2))
            output.flush()

            val head = readExact(input, 2)
            assertEquals(1, head[0].toInt())
            val user = readExact(input, head[1].toInt())
            val passwordLength = readExact(input, 1)[0].toInt()
            val pass = readExact(input, passwordLength)
            assertEquals("user", String(user, Charsets.UTF_8))
            assertEquals("pass", String(pass, Charsets.UTF_8))
            output.write(byteArrayOf(1, 0))
            output.flush()

            readExact(input, 10)
            output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            output.flush()
        }

        assertTrue(result)
    }

    @Test
    fun `refused connect is reported as failure`() {
        val result = runProxyCheck { input, output ->
            readExact(input, 3)
            output.write(byteArrayOf(5, 0))
            output.flush()
            readExact(input, 10)
            output.write(byteArrayOf(5, 5, 0, 1))
            output.flush()
        }

        assertFalse(result)
    }

    @Test
    fun `silent proxy times out as failure`() {
        val result = runProxyCheck(timeoutMs = 200) { input, _ ->
            readExact(input, 3)
            Thread.sleep(500)
        }

        assertFalse(result)
    }

    private fun runProxyCheck(
        username: String? = null,
        password: String? = null,
        timeoutMs: Int = 2000,
        handler: (InputStream, OutputStream) -> Unit,
    ): Boolean {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val error = AtomicReference<Throwable?>()
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { client ->
                    handler(client.getInputStream(), client.getOutputStream())
                }
            } catch (throwable: Throwable) {
                error.set(throwable)
            }
        }

        val result = try {
            Socks5Probe.check(server.localPort, username, password, timeoutMs)
        } finally {
            runCatching { server.close() }
            worker.join(2000)
        }

        error.get()?.let { throw it }
        return result
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read < 0) throw IllegalStateException("Unexpected end of stream")
            offset += read
        }
        return buffer
    }
}
