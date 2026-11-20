package io.nekohasekai.sfa.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.ContextCompat
import io.nekohasekai.sfa.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

// UralVPN: watchdog мёртвого туннеля. Проверяет, проходит ли трафик через VPN,
// и перезагружает сервис, если соединение зависло: при смене сети, при включении
// экрана и раз в 2 минуты в фоне.
object UralWatchdog {
    private const val CHECK_INTERVAL_MS = 300_000L
    private const val GRACE_MS = 20_000L
    private const val SCREEN_DELAY_MS = 1_500L
    private const val NETWORK_DELAY_MS = 6_000L
    private const val TIMEOUT_MS = 3_000
    private const val FAILS_BEFORE_RELOAD = 2

    private var scope: CoroutineScope? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var failures = 0
    private var checking = false
    private var running: () -> Boolean = { false }
    private var reload: suspend () -> Unit = {}

    fun start(isRunning: () -> Boolean, onReload: suspend () -> Unit) {
        if (scope != null) return
        running = isRunning
        reload = onReload
        failures = 0
        val ctx = Application.application
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s

        // 3. Фоновая проверка раз в 2 минуты (страховка, когда экран выключен).
        s.launch {
            delay(GRACE_MS)
            while (isActive) {
                check()
                delay(CHECK_INTERVAL_MS)
            }
        }

        // 1. Реакция на сеть: после потери/появления сети даём 6 секунд и проверяем.
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var seen = false
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (seen) s.launch { delay(NETWORK_DELAY_MS); check() }
                    seen = true
                }

                override fun onLost(network: Network) {
                    seen = false
                    s.launch { delay(NETWORK_DELAY_MS); check() }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        }

        // 2. Разблокировка/включение экрана — быстрая проверка.
        runCatching {
            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    s.launch { delay(SCREEN_DELAY_MS); check() }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(ctx, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiver = r
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        val ctx = Application.application
        runCatching {
            networkCallback?.let {
                (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        }
        networkCallback = null
        runCatching { screenReceiver?.let { ctx.unregisterReceiver(it) } }
        screenReceiver = null
        failures = 0
        checking = false
    }

    private suspend fun check() {
        if (!running()) return
        if (checking) return
        checking = true
        try {
            val alive = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("1.1.1.1", 443), TIMEOUT_MS)
                    }
                    true
                }.getOrDefault(false)
            }
            if (alive) {
                failures = 0
            } else {
                failures += 1
                if (failures >= FAILS_BEFORE_RELOAD) {
                    failures = 0
                    if (running()) runCatching { reload() }
                }
            }
        } finally {
            checking = false
        }
    }
}
