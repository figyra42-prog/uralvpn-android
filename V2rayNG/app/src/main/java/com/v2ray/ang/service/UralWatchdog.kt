package com.v2ray.ang.service

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UralVPN: restarts a tunnel that silently stopped passing traffic.
 *
 * Some Android 17 builds put the VPN process to sleep and the core does not always reconnect.
 * The watchdog probes the running core through its local SOCKS inbound, retries on screen
 * unlock and upstream network changes, and reloads the core after [FAILS_BEFORE_RELOAD]
 * consecutive failures.
 *
 * The coroutine scope is owned by this object and is cancelled by [stop], which
 * [CoreServiceManager] calls on every teardown path.
 */
object UralWatchdog {
    internal const val GRACE_MS = 20_000L
    internal const val SCREEN_DELAY_MS = 1_500L
    internal const val NETWORK_DELAY_MS = 6_000L
    internal const val CHECK_INTERVAL_MS = 300_000L
    internal const val FAILS_BEFORE_RELOAD = 2

    @Volatile
    private var scope: CoroutineScope? = null
    private val pendingLock = Any()
    private var pendingCheck: Job? = null
    private val checking = AtomicBoolean(false)
    private var failures = FailureCounter(FAILS_BEFORE_RELOAD)

    @Volatile
    private var running: () -> Boolean = { false }

    @Volatile
    private var probe: suspend () -> Boolean = { false }

    @Volatile
    private var reload: suspend () -> Boolean = { false }

    @Synchronized
    fun start(
        isRunning: () -> Boolean,
        probe: suspend () -> Boolean,
        onReload: suspend () -> Boolean,
    ) {
        if (scope != null) return
        running = isRunning
        this.probe = probe
        reload = onReload
        failures = FailureCounter(FAILS_BEFORE_RELOAD)
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope
        LogUtil.i(AppConfig.TAG, "UralWatchdog: started, mode=${runModeName()}")
        newScope.launch {
            delay(GRACE_MS)
            while (isActive) {
                check(newScope)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        if (scope == null) return
        running = { false }
        scope?.cancel()
        scope = null
        synchronized(pendingLock) {
            pendingCheck?.cancel()
            pendingCheck = null
        }
        checking.set(false)
        failures.reset()
        probe = { false }
        reload = { false }
        LogUtil.i(AppConfig.TAG, "UralWatchdog: stopped")
    }

    /** Schedules a check after the screen turns on or is unlocked. */
    fun scheduleScreenCheck() = schedule(SCREEN_DELAY_MS)

    /** Schedules a check after the upstream network changed. */
    fun scheduleNetworkCheck() = schedule(NETWORK_DELAY_MS)

    private fun schedule(delayMs: Long) {
        val owner = scope ?: return
        synchronized(pendingLock) {
            if (pendingCheck?.isActive == true) return
            pendingCheck = owner.launch {
                delay(delayMs)
                check(owner)
            }
        }
    }

    private suspend fun check(owner: CoroutineScope) {
        if (scope !== owner || !running()) return
        if (!checking.compareAndSet(false, true)) return
        try {
            val alive = try {
                withContext(Dispatchers.IO) { probe() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (scope !== owner) return
            if (alive) {
                failures.onAlive()
                return
            }

            val mode = runModeName()
            val guid = MmkvManager.getSelectServer().orEmpty()
            val failed = failures.failures + 1
            if (!failures.onFailure()) {
                LogUtil.w(
                    AppConfig.TAG,
                    "UralWatchdog: probe failed, mode=$mode, guid=$guid, failures=$failed"
                )
                return
            }

            LogUtil.w(
                AppConfig.TAG,
                "UralWatchdog: tunnel is dead, reloading, mode=$mode, guid=$guid, failures=$failed"
            )
            if (scope !== owner || !running()) return
            try {
                val reloaded = withContext(Dispatchers.IO) { reload() }
                if (!reloaded && scope === owner) {
                    LogUtil.e(
                        AppConfig.TAG,
                        "UralWatchdog: reload did not start the core, mode=$mode, guid=$guid"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(
                    AppConfig.TAG,
                    "UralWatchdog: reload failed, mode=$mode, guid=$guid",
                    error
                )
            }
        } finally {
            checking.set(false)
        }
    }

    private fun runModeName(): String = when {
        SettingsManager.isRootMode() -> "root"
        SettingsManager.isVpnMode() -> "vpn"
        else -> "proxy"
    }

    /** Counts consecutive failures and reports when a reload is due. */
    internal class FailureCounter(private val threshold: Int) {
        var failures = 0
            private set

        fun onAlive() {
            failures = 0
        }

        fun onFailure(): Boolean {
            failures += 1
            if (failures >= threshold) {
                failures = 0
                return true
            }
            return false
        }

        fun reset() {
            failures = 0
        }
    }
}
