package app.liftos.domain.reminders

import app.liftos.domain.model.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Jerusalem")

    // 2026-10-14 is a Wednesday (ISO 3).
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDateTime.parse("${date}T$time:00"), zone)

    @Test
    fun `nothing is scheduled while a reminder is off`() {
        val s = ReminderSettings()
        ReminderType.entries.forEach { assertNull(ReminderPlanner.nextFire(it, s, at("2026-10-14", "10:00"), ReminderContext(plannedWeekdays = setOf(1, 3)))) }
    }

    @Test
    fun `workout reminder fires today if the time has not passed, otherwise on the next planned day`() {
        val s = ReminderSettings(workoutEnabled = true, workoutTime = "17:30")
        val ctx = ReminderContext(plannedWeekdays = setOf(3, 6)) // Wed, Sat
        assertEquals(at("2026-10-14", "17:30"), ReminderPlanner.nextFire(ReminderType.WORKOUT, s, at("2026-10-14", "09:00"), ctx))
        assertEquals(at("2026-10-17", "17:30"), ReminderPlanner.nextFire(ReminderType.WORKOUT, s, at("2026-10-14", "17:30"), ctx)) // exactly at the time: next one
        assertEquals(at("2026-10-17", "17:30"), ReminderPlanner.nextFire(ReminderType.WORKOUT, s, at("2026-10-14", "20:00"), ctx))
    }

    @Test
    fun `workout reminder needs a plan with scheduled days`() {
        val s = ReminderSettings(workoutEnabled = true)
        assertNull(ReminderPlanner.nextFire(ReminderType.WORKOUT, s, at("2026-10-14", "09:00"), ReminderContext()))
    }

    @Test
    fun `weigh in reminder uses the chosen weekdays`() {
        val s = ReminderSettings(weighInEnabled = true, weighInWeekdays = listOf(7, 4), weighInTime = "07:30") // Sun, Thu
        assertEquals(at("2026-10-15", "07:30"), ReminderPlanner.nextFire(ReminderType.WEIGH_IN, s, at("2026-10-14", "12:00"), ReminderContext()))
        assertEquals(at("2026-10-18", "07:30"), ReminderPlanner.nextFire(ReminderType.WEIGH_IN, s, at("2026-10-15", "08:00"), ReminderContext()))
    }

    @Test
    fun `weekly summary fires once a week on its day`() {
        val s = ReminderSettings(weeklySummaryEnabled = true, weeklySummaryWeekday = 6, weeklySummaryTime = "20:00") // Saturday
        assertEquals(at("2026-10-17", "20:00"), ReminderPlanner.nextFire(ReminderType.WEEKLY_SUMMARY, s, at("2026-10-14", "12:00"), ReminderContext()))
        assertEquals(at("2026-10-24", "20:00"), ReminderPlanner.nextFire(ReminderType.WEEKLY_SUMMARY, s, at("2026-10-17", "20:00"), ReminderContext()))
    }

    @Test
    fun `measurement reminder counts days from the last measurement`() {
        val s = ReminderSettings(measureEnabled = true, measureEveryDays = 14, measureTime = "08:00")
        val ctx = ReminderContext(lastMeasurementDate = LocalDate.parse("2026-10-10"))
        assertEquals(at("2026-10-24", "08:00"), ReminderPlanner.nextFire(ReminderType.MEASURE, s, at("2026-10-14", "12:00"), ctx))
    }

    @Test
    fun `an overdue measurement reminder fires at the next occurrence of its time, not in the past`() {
        val s = ReminderSettings(measureEnabled = true, measureEveryDays = 14, measureTime = "08:00")
        val ctx = ReminderContext(lastMeasurementDate = LocalDate.parse("2026-08-01"))
        assertEquals(at("2026-10-15", "08:00"), ReminderPlanner.nextFire(ReminderType.MEASURE, s, at("2026-10-14", "12:00"), ctx))
        assertEquals(at("2026-10-14", "08:00"), ReminderPlanner.nextFire(ReminderType.MEASURE, s, at("2026-10-14", "06:00"), ctx))
    }

    @Test
    fun `backup reminder with no backup yet fires at the next 10 00`() {
        val s = ReminderSettings(backupEnabled = true, backupEveryDays = 14)
        assertEquals(at("2026-10-14", "10:00"), ReminderPlanner.nextFire(ReminderType.BACKUP, s, at("2026-10-14", "08:00"), ReminderContext()))
        assertEquals(at("2026-10-15", "10:00"), ReminderPlanner.nextFire(ReminderType.BACKUP, s, at("2026-10-14", "11:00"), ReminderContext()))
    }

    @Test
    fun `reminders keep their wall clock time across the daylight saving change`() {
        // Israel moves from summer to winter time on Sunday 2026-10-25 (02:00 -> 01:00), so that day has 25 hours.
        val s = ReminderSettings(weighInEnabled = true, weighInWeekdays = listOf(1, 2, 3, 4, 5, 6, 7), weighInTime = "07:30")
        val saturday = ReminderPlanner.nextFire(ReminderType.WEIGH_IN, s, at("2026-10-23", "12:00"), ReminderContext())!!
        val sunday = ReminderPlanner.nextFire(ReminderType.WEIGH_IN, s, saturday, ReminderContext())!!
        assertEquals(at("2026-10-24", "07:30"), saturday)
        assertEquals(at("2026-10-25", "07:30"), sunday)
        assertEquals(java.time.LocalTime.of(7, 30), sunday.toLocalTime())
        assertEquals(25L, java.time.Duration.between(saturday, sunday).toHours())
    }

    @Test
    fun `a bad time string falls back to the default instead of crashing`() {
        val s = ReminderSettings(weighInEnabled = true, weighInWeekdays = listOf(3), weighInTime = "soon")
        assertEquals(at("2026-10-14", "07:30"), ReminderPlanner.nextFire(ReminderType.WEIGH_IN, s, at("2026-10-14", "06:00"), ReminderContext()))
    }

    // ---- when the alarm fires ------------------------------------------------------------------

    private val today = LocalDate.parse("2026-10-14")

    @Test
    fun `no workout reminder after the workout was already done today`() {
        val s = ReminderSettings(workoutEnabled = true)
        val planned = ReminderContext(plannedWeekdays = setOf(3))
        assertTrue(ReminderPlanner.shouldPost(ReminderType.WORKOUT, s, today, planned))
        assertFalse(ReminderPlanner.shouldPost(ReminderType.WORKOUT, s, today, planned.copy(workoutDates = setOf(today))))
        assertFalse(ReminderPlanner.shouldPost(ReminderType.WORKOUT, s, today, planned.copy(plannedWeekdays = setOf(4))))
    }

    @Test
    fun `no weigh in reminder after weighing in today`() {
        val s = ReminderSettings(weighInEnabled = true)
        assertTrue(ReminderPlanner.shouldPost(ReminderType.WEIGH_IN, s, today, ReminderContext(lastWeighInDate = today.minusDays(1))))
        assertFalse(ReminderPlanner.shouldPost(ReminderType.WEIGH_IN, s, today, ReminderContext(lastWeighInDate = today)))
    }

    @Test
    fun `measurement and backup reminders wait until enough days passed`() {
        val m = ReminderSettings(measureEnabled = true, measureEveryDays = 14, backupEnabled = true, backupEveryDays = 7)
        assertFalse(ReminderPlanner.shouldPost(ReminderType.MEASURE, m, today, ReminderContext(lastMeasurementDate = today.minusDays(5))))
        assertTrue(ReminderPlanner.shouldPost(ReminderType.MEASURE, m, today, ReminderContext(lastMeasurementDate = today.minusDays(14))))
        assertTrue(ReminderPlanner.shouldPost(ReminderType.MEASURE, m, today, ReminderContext()))
        assertFalse(ReminderPlanner.shouldPost(ReminderType.BACKUP, m, today, ReminderContext(lastBackupDate = today.minusDays(3))))
        assertTrue(ReminderPlanner.shouldPost(ReminderType.BACKUP, m, today, ReminderContext(lastBackupDate = today.minusDays(8))))
    }

    @Test
    fun `a disabled reminder never posts`() {
        ReminderType.entries.forEach { assertFalse(ReminderPlanner.shouldPost(it, ReminderSettings(), today, ReminderContext(plannedWeekdays = setOf(3)))) }
    }

    @Test
    fun `request codes are unique so the alarms do not replace each other`() {
        assertEquals(ReminderType.entries.size, ReminderType.entries.map { it.requestCode }.toSet().size)
    }
}
