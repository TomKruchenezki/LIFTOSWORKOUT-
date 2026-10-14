package app.liftos.domain.reminders

import app.liftos.domain.model.ReminderSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class ReminderType(val labelHe: String, val requestCode: Int) {
    WORKOUT("אימון מתוכנן", 8101),
    WEIGH_IN("שקילה", 8102),
    MEASURE("מדידת היקפים", 8103),
    WEEKLY_SUMMARY("סיכום שבועי", 8104),
    BACKUP("גיבוי נתונים", 8105),
}

/** What the planner needs to know about the user's data; all of it is read from the database by the caller. */
data class ReminderContext(
    /** ISO weekdays on which the active plan has a workout. */
    val plannedWeekdays: Set<Int> = emptySet(),
    val lastWeighInDate: LocalDate? = null,
    /** Last measurement that contains a circumference or body fat value. */
    val lastMeasurementDate: LocalDate? = null,
    val lastBackupDate: LocalDate? = null,
    /** Dates on which a workout was recorded. */
    val workoutDates: Set<LocalDate> = emptySet(),
)

/**
 * Decides WHEN a reminder should fire next and WHETHER it is still needed when the time comes (no "weigh
 * in" reminder after the user already weighed in). Pure functions on top of dates, so DST and the
 * Sunday-first week are unit-tested. The Android side only turns the answer into an alarm.
 */
object ReminderPlanner {
    /** The backup reminder has no time setting of its own: mid-morning, when people are awake. */
    val BACKUP_TIME: LocalTime = LocalTime.of(10, 0)

    fun parseTime(text: String, fallback: LocalTime): LocalTime = try {
        val (h, m) = text.trim().split(':')
        LocalTime.of(h.toInt(), m.toInt())
    } catch (_: Exception) {
        fallback
    }

    fun format(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun isEnabled(type: ReminderType, s: ReminderSettings): Boolean = when (type) {
        ReminderType.WORKOUT -> s.workoutEnabled
        ReminderType.WEIGH_IN -> s.weighInEnabled
        ReminderType.MEASURE -> s.measureEnabled
        ReminderType.WEEKLY_SUMMARY -> s.weeklySummaryEnabled
        ReminderType.BACKUP -> s.backupEnabled
    }

    /** The next moment (strictly after [now]) the reminder should fire, or null when it is off or cannot apply. */
    fun nextFire(type: ReminderType, s: ReminderSettings, now: ZonedDateTime, ctx: ReminderContext): ZonedDateTime? {
        if (!isEnabled(type, s)) return null
        return when (type) {
            ReminderType.WORKOUT ->
                onWeekdays(now, ctx.plannedWeekdays, parseTime(s.workoutTime, LocalTime.of(17, 30)))
            ReminderType.WEIGH_IN ->
                onWeekdays(now, s.weighInWeekdays.filter { it in 1..7 }.toSet(), parseTime(s.weighInTime, LocalTime.of(7, 30)))
            ReminderType.WEEKLY_SUMMARY ->
                onWeekdays(now, setOf(s.weeklySummaryWeekday).filter { it in 1..7 }.toSet(), parseTime(s.weeklySummaryTime, LocalTime.of(20, 0)))
            ReminderType.MEASURE ->
                everyNDays(now, ctx.lastMeasurementDate, s.measureEveryDays, parseTime(s.measureTime, LocalTime.of(8, 0)))
            ReminderType.BACKUP ->
                everyNDays(now, ctx.lastBackupDate, s.backupEveryDays, BACKUP_TIME)
        }
    }

    /** Re-checked when the alarm fires: false when the reminder would be pointless now. */
    fun shouldPost(type: ReminderType, s: ReminderSettings, today: LocalDate, ctx: ReminderContext): Boolean {
        if (!isEnabled(type, s)) return false
        return when (type) {
            ReminderType.WORKOUT -> today.dayOfWeek.value in ctx.plannedWeekdays && today !in ctx.workoutDates
            ReminderType.WEIGH_IN -> ctx.lastWeighInDate != today
            ReminderType.MEASURE -> dueAfter(ctx.lastMeasurementDate, s.measureEveryDays, today)
            ReminderType.WEEKLY_SUMMARY -> true
            ReminderType.BACKUP -> dueAfter(ctx.lastBackupDate, s.backupEveryDays, today)
        }
    }

    private fun dueAfter(last: LocalDate?, everyDays: Int, today: LocalDate): Boolean =
        last == null || ChronoUnit.DAYS.between(last, today) >= everyDays.coerceAtLeast(1)

    private fun onWeekdays(now: ZonedDateTime, weekdays: Set<Int>, time: LocalTime): ZonedDateTime? {
        if (weekdays.isEmpty()) return null
        for (offset in 0L..7L) {
            val day = now.toLocalDate().plusDays(offset)
            if (day.dayOfWeek.value !in weekdays) continue
            val at = day.atTime(time).atZone(now.zone)
            if (at.isAfter(now)) return at
        }
        return null
    }

    private fun everyNDays(now: ZonedDateTime, last: LocalDate?, everyDays: Int, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate()
        var day = last?.plusDays(everyDays.coerceAtLeast(1).toLong())?.let { if (it.isBefore(today)) today else it } ?: today
        while (true) {
            val at = day.atTime(time).atZone(now.zone)
            if (at.isAfter(now)) return at
            day = day.plusDays(1)
        }
    }
}
