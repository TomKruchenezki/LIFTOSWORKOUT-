package app.liftos.domain.calendar

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.model.ActivityEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Frequency(val labelHe: String) {
    DAILY("כל יום"),
    WEEKLY("כל שבוע"),
    MONTHLY("כל חודש"),
}

/**
 * A small, explicit repeat rule stored as text, e.g. `FREQ=WEEKLY;INTERVAL=1;BYDAY=1,3;UNTIL=2026-12-31`.
 * (BYDAY uses ISO weekdays 1 = Monday ... 7 = Sunday.) Only what the app needs is supported, so the
 * behaviour is easy to reason about and test.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val until: LocalDate? = null,
    val count: Int? = null,
) {
    init {
        require(interval >= 1) { "interval must be positive" }
    }

    fun toText(): String = buildList {
        add("FREQ=${frequency.name}")
        add("INTERVAL=$interval")
        if (weekdays.isNotEmpty()) add("BYDAY=${weekdays.sorted().joinToString(",")}")
        until?.let { add("UNTIL=$it") }
        count?.let { add("COUNT=$it") }
    }.joinToString(";")

    /** Hebrew sentence for the UI, e.g. "כל שבוע בימים ב׳, ד׳ עד 31/12/2026". */
    fun describe(): String {
        val base = when (frequency) {
            Frequency.DAILY -> if (interval == 1) "כל יום" else "כל $interval ימים"
            Frequency.WEEKLY -> if (interval == 1) "כל שבוע" else "כל $interval שבועות"
            Frequency.MONTHLY -> if (interval == 1) "כל חודש" else "כל $interval חודשים"
        }
        val days = if (frequency == Frequency.WEEKLY && weekdays.isNotEmpty()) {
            " בימים " + weekdays.sortedBy { (it % 7) }.joinToString(", ") { app.liftos.domain.logic.TimeUtils.weekdayShortHe(it) }
        } else ""
        val end = when {
            until != null -> " עד ${app.liftos.domain.logic.Formatters.date(until)}"
            count != null -> " ($count פעמים)"
            else -> ""
        }
        return base + days + end
    }

    /**
     * Dates inside [range] on which the event that starts on [start] occurs. [exceptions] are skipped
     * (they were moved or changed individually). The result is capped to protect against runaway rules.
     */
    fun occurrences(start: LocalDate, range: DateRange, exceptions: Set<LocalDate> = emptySet()): List<LocalDate> {
        val all = mutableListOf<LocalDate>()
        var produced = 0
        var cursor = start
        val hardEnd = minOf(range.end, until ?: range.end)
        val maxDays = 366 * 10
        var guard = 0
        while (!cursor.isAfter(hardEnd) && guard < maxDays) {
            guard++
            val matches = matches(start, cursor)
            if (matches) {
                produced++
                if (count != null && produced > count) break
                if (cursor in range && cursor !in exceptions) all += cursor
            }
            cursor = cursor.plusDays(1)
        }
        return all
    }

    private fun matches(start: LocalDate, date: LocalDate): Boolean {
        if (date.isBefore(start)) return false
        return when (frequency) {
            Frequency.DAILY -> ChronoUnit.DAYS.between(start, date) % interval == 0L
            Frequency.WEEKLY -> {
                val days = weekdays.ifEmpty { listOf(start.dayOfWeek.value) }
                val weekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val dateWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val weeks = ChronoUnit.WEEKS.between(weekStart, dateWeekStart)
                weeks % interval == 0L && date.dayOfWeek.value in days
            }
            Frequency.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), date.withDayOfMonth(1))
                val target = minOf(start.dayOfMonth, date.lengthOfMonth())
                months % interval == 0L && date.dayOfMonth == target
            }
        }
    }

    companion object {
        fun parse(text: String): RecurrenceRule? {
            if (text.isBlank()) return null
            val parts = text.split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).trim().uppercase() to it.substring(i + 1).trim()
            }.toMap()
            val freq = parts["FREQ"]?.let { f -> Frequency.entries.firstOrNull { it.name == f.uppercase() } } ?: return null
            val interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it >= 1 } ?: 1
            val days = parts["BYDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..7 }.orEmpty()
            val until = parts["UNTIL"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it >= 1 }
            return RecurrenceRule(freq, interval, days, until, count)
        }
    }
}

object Exceptions {
    fun parse(text: String): Set<LocalDate> =
        text.split(',').mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.toSet()

    fun format(dates: Set<LocalDate>): String = dates.sorted().joinToString(",")

    /** The series without one occurrence (it was moved, deleted or changed on its own). */
    fun withException(event: ActivityEvent, date: LocalDate): ActivityEvent =
        event.copy(recurrenceExceptions = format(parse(event.recurrenceExceptions) + date))
}
