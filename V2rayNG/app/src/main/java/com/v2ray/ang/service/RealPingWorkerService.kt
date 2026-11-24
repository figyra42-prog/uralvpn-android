package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.UralSimulation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(if (onlyTcp) concurrency * 2 else concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    private data class ProbeResult(
        val delayMillis: Long,
        val connectMillis: Long = -1L,
    )

    fun start() {
        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val result = if (onlyTcp) ProbeResult(startTcping(guid)) else startRealPing(guid)
                    if (scope.isActive) {
                        onEvent(RealPingEvent.Result(guid, result.delayMillis))
                        if (!onlyTcp && result.delayMillis > 0L) {
                            val config = MmkvManager.decodeServerConfig(guid)
                            onEvent(
                                RealPingEvent.Timing(
                                    guid = guid,
                                    remark = config?.remarks.orEmpty(),
                                    type = config?.configType?.name.orEmpty(),
                                    connectMillis = result.connectMillis,
                                    totalMillis = result.delayMillis,
                                )
                            )
                        }
                    }
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    if (scope.isActive) {
                        onEvent(RealPingEvent.Progress("$left / $count"))
                    }
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                if (isActive) {
                    onEvent(RealPingEvent.Finish("0"))
                }
            } catch (_: CancellationException) {
                // If cancelled, don't send finish event to avoid confusion
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun startRealPing(guid: String): ProbeResult {
        val retFailure = ProbeResult(-1L)

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        val testConfig = UralSimulation.applyToProfile(config)
        var connectMillis = -1L
        val canTcpProbe = testConfig.server.isNotNullEmpty()
            && testConfig.serverPort?.toIntOrNull() != null
            && testConfig.configType != EConfigType.HYSTERIA2
            && testConfig.configType != EConfigType.WIREGUARD
            && testConfig.alpn?.startsWith("h3") != true
            && (testConfig.configType == EConfigType.CUSTOM || !testConfig.configType.isComplexType())
        if (canTcpProbe) {
            val url = testConfig.server.orEmpty()
            val port = testConfig.serverPort.orEmpty().toInt()
            connectMillis = SpeedtestManager.socketConnectTime(url, port, 1000)
            if (connectMillis <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        val totalMillis = CoreNativeManager.measureOutboundDelay(
            configResult.content,
            SettingsManager.getDelayTestUrl(),
        )
        if (totalMillis < 0L) return retFailure
        return ProbeResult(totalMillis, connectMillis)
    }

    private fun startTcping(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        val testConfig = UralSimulation.applyToProfile(config)
        val canTcpProbe = testConfig.server.isNotNullEmpty()
            && testConfig.serverPort?.toIntOrNull() != null
            && testConfig.configType != EConfigType.HYSTERIA2
            && testConfig.configType != EConfigType.WIREGUARD
            && testConfig.alpn?.split(',')?.all { it.trim().startsWith("h3") } != true
            && (testConfig.configType == EConfigType.CUSTOM || !testConfig.configType.isComplexType())
        if (canTcpProbe) {
            val url = testConfig.server.orEmpty()
            val port = testConfig.serverPort.orEmpty().toInt()
            return SpeedtestManager.socketConnectTime(url, port, 1000)
        }

        return retFailure
    }
}
