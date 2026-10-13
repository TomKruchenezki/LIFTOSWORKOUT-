package app.liftos.domain.logic

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * All instants are stored as UTC epoch millis. Calendar dates ("which day was this workout?")
 * are derived with the zone that was active when the data was recorded and stored next to
 * the instant, so daylight-saving changes or travelling never move history to another day.
 */
object TimeUtils {
    val DEFAULT_ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

    fun zone(id: String?): ZoneId = try {
        if (id.isNullOrBlank()) DEFAULT_ZONE else ZoneId.of(id)
    } catch (_: Exception) {
        DEFAULT_ZONE
    }

    fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun localDateString(epochMs: Long, zone: ZoneId): String = localDate(epochMs, zone).toString()

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: Exception) {
        null
    }

    /** Epoch millis for a local date + time in [zone]; gaps (DST) are resolved forward by java.time. */
    fun toEpochMs(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()

    fun startOfDayMs(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Week containing [date]; Israel's week starts on Sunday. */
    fun weekStart(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.SUNDAY): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    fun weekRange(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.SUNDAY): ClosedRange<LocalDate> {
        val start = weekStart(date, firstDay)
        return start..start.plusDays(6)
    }

    fun monthRange(date: LocalDate): ClosedRange<LocalDate> =
        date.withDayOfMonth(1)..date.with(TemporalAdjusters.lastDayOfMonth())

    /** ISO day numbers (1 = Monday ... 7 = Sunday) in Israeli display order Sunday-first. */
    val ISRAELI_WEEK_ORDER: List<Int> = listOf(7, 1, 2, 3, 4, 5, 6)

    fun weekdayShortHe(isoDay: Int): String = when (isoDay) {
        7 -> "א׳"
        1 -> "ב׳"
        2 -> "ג׳"
        3 -> "ד׳"
        4 -> "ה׳"
        5 -> "ו׳"
        6 -> "ש׳"
        else -> ""
    }

    fun weekdayHe(isoDay: Int): String = when (isoDay) {
        7 -> "ראשון"
        1 -> "שני"
        2 -> "שלישי"
        3 -> "רביעי"
        4 -> "חמישי"
        5 -> "שישי"
        6 -> "שבת"
        else -> ""
    }

    fun weekdayHe(date: LocalDate): String = weekdayHe(date.dayOfWeek.value)
}
