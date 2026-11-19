#!/usr/bin/env python3
"""Подключает UralWatchdog к жизненному циклу сервиса (вызывается из brand.sh)."""
import sys

root = sys.argv[1]
path = root + "/app/src/main/java/io/nekohasekai/sfa/bg/BoxService.kt"
s = open(path, encoding="utf-8").read()

if "UralWatchdog.start(" in s:
    print("watchdog: already patched")
    raise SystemExit(0)

start_anchor = """            notification.start()
        } catch (e: Exception) {
            stopAndAlert(Alert.StartService, e.message)"""
start_repl = """            notification.start()
            UralWatchdog.start(
                isRunning = { status.value == Status.Started },
                onReload = { runCatching { serviceReload0() } },
            )
        } catch (e: Exception) {
            stopAndAlert(Alert.StartService, e.message)"""
assert s.count(start_anchor) == 1, "start anchor"
s = s.replace(start_anchor, start_repl, 1)

stop_anchor = """        if (status.value != Status.Started) return
        status.value = Status.Stopping"""
stop_repl = """        if (status.value != Status.Started) return
        status.value = Status.Stopping
        UralWatchdog.stop()"""
assert s.count(stop_anchor) == 1, "stop anchor"
s = s.replace(stop_anchor, stop_repl, 1)

alert_anchor = """    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        Settings.startedByUser = false"""
alert_repl = """    private suspend fun stopAndAlert(type: Alert, message: String? = null) {
        UralWatchdog.stop()
        Settings.startedByUser = false"""
assert s.count(alert_anchor) == 1, "alert anchor"
s = s.replace(alert_anchor, alert_repl, 1)

open(path, "w", encoding="utf-8").write(s)
print("watchdog: patched BoxService")
