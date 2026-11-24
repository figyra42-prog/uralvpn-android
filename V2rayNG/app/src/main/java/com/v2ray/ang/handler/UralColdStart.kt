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
            rows.sortedBy(::coldMillis).forEach { row ->
                val connect = row.connectMillis
                val cold = coldMillis(row)
                val first = if (connect >= 0L) (cold - connect).coerceAtLeast(0L) else row.totalMillis
                val connectText = if (connect >= 0L) connect.toString() else "n/a"
                appendLine(
                    String.format(
                        Locale.US,
                        "%4d ms cold (connect %s + first request %d), ping shown %d ms: %s [%s]",
                        cold,
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

    private fun coldMillis(row: Row): Long =
        if (row.connectMillis >= 0L) maxOf(row.totalMillis, row.connectMillis) else row.totalMillis
}
