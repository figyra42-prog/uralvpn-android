package com.v2ray.ang.handler

import java.util.Locale

/** Formats the cold-open timing report written after an explicit server test. */
object UralColdStart {
    data class Row(
        val remark: String,
        val type: String,
        val connectMillis: Long,
        val totalMillis: Long,
    )

    fun report(rows: List<Row>): String {
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine("ural-timing, cold open of the servers:")
            rows.sortedBy { it.totalMillis }.forEach { row ->
                val connect = row.connectMillis
                val first = if (connect >= 0L) (row.totalMillis - connect).coerceAtLeast(0L) else row.totalMillis
                val connectText = if (connect >= 0L) connect.toString() else "n/a"
                appendLine(
                    String.format(
                        Locale.US,
                        "%4d ms cold (connect %s + first request %d), ping shown %d ms: %s [%s]",
                        row.totalMillis,
                        connectText,
                        first,
                        row.totalMillis,
                        row.remark,
                        row.type,
                    )
                )
            }
        }.trimEnd()
    }
}
