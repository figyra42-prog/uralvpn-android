package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UralColdStartTest {
    @Test
    fun reportIsSortedByColdTotal() {
        val report = UralColdStart.report(
            listOf(
                UralColdStart.Row("Slow", "vless", 200, 900),
                UralColdStart.Row("Fast", "vless", 20, 100),
            )
        )
        val lines = report.lines()
        assertTrue(lines[0].contains("cold open"))
        assertTrue(lines[1].contains("100 ms cold"))
        assertTrue(lines[1].contains("Fast"))
        assertTrue(lines[2].contains("900 ms cold"))
        assertTrue(lines[2].contains("Slow"))
    }

    @Test
    fun reportExplainsConnectAndFirstRequest() {
        val report = UralColdStart.report(
            listOf(UralColdStart.Row("Latvia", "vless", 40, 140))
        )
        assertTrue(report.contains("connect 40 + first request 100"))
    }

    @Test
    fun unknownConnectIsShownAsNa() {
        val report = UralColdStart.report(
            listOf(UralColdStart.Row("Unknown", "vless", -1, 100))
        )
        assertTrue(report.contains("connect n/a + first request 100"))
    }

    @Test
    fun emptyReportIsEmpty() {
        assertEquals("", UralColdStart.report(emptyList()))
    }
}
