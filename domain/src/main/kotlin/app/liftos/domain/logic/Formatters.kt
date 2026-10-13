package app.liftos.domain.logic

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale-independent number/date formatting (Latin digits) so text exports are stable. */
object Formatters {
    private val symbols = DecimalFormatSymbols(Locale.ROOT)

    /** 62.5 -> "62.5", 60.0 -> "60", null -> "". */
    fun number(value: Double?, maxDecimals: Int = 2): String {
        if (value == null) return ""
        val pattern = if (maxDecimals <= 0) "0" else "0." + "#".repeat(maxDecimals)
        return DecimalFormat(pattern, symbols).format(value)
    }

    /** 12340.0 -> "12,340". */
    fun thousands(value: Double): String = DecimalFormat("#,##0", symbols).format(Math.round(value))

    /** Parses a user-typed decimal ("62,5" or "62.5"); returns null for blank/invalid input. */
    fun parseDecimal(text: String): Double? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty() || cleaned == ".") return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun parseInt(text: String): Int? = text.trim().toIntOrNull()

    /** 3_725_000 ms -> "1:02:05"; 125_000 -> "2:05". */
    fun duration(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0) / 1000)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    fun minutesHe(ms: Long): String = "${(ms.coerceAtLeast(0) / 60_000)} דק׳"

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun date(date: LocalDate): String = date.format(dateFormat)

    fun time(epochMs: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFormat)

    /** "יום ג׳, 13/10/2026" */
    fun dateWithWeekday(date: LocalDate): String =
        "יום ${TimeUtils.weekdayShortHe(date.dayOfWeek.value)}, ${date(date)}"
}
