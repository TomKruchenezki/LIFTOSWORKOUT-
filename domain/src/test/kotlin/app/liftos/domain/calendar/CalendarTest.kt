package app.liftos.domain.calendar

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.Fx
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.OverrideAction
import app.liftos.domain.model.PlannedOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarTest {
    private val today = LocalDate.parse("2026-10-14") // Wednesday
    private fun d(s: String) = LocalDate.parse(s)
    private fun range(a: String, b: String) = DateRange(d(a), d(b))

    // ---- recurrence ----------------------------------------------------------------

    @Test
    fun `weekly rule on chosen weekdays`() {
        val rule = RecurrenceRule(Frequency.WEEKLY, 1, listOf(1, 3)) // Mon, Wed
        val dates = rule.occurrences(d("2026-10-05"), range("2026-10-05", "2026-10-18"))
        assertEquals(listOf("2026-10-05", "2026-10-07", "2026-10-12", "2026-10-14").map(::d), dates)
    }

    @Test
    fun `every other week skips the odd weeks`() {
        val rule = RecurrenceRule(Frequency.WEEKLY, 2, listOf(7)) // Sundays, every 2 weeks
        val dates = rule.occurrences(d("2026-10-04"), range("2026-10-01", "2026-11-15"))
        assertEquals(listOf("2026-10-04", "2026-10-18", "2026-11-01", "2026-11-15").map(::d), dates)
    }

    @Test
    fun `daily rule with interval, until and count`() {
        val daily = RecurrenceRule(Frequency.DAILY, 3)
        assertEquals(4, daily.occurrences(d("2026-10-01"), range("2026-10-01", "2026-10-10")).size) // 1,4,7,10
        val until = RecurrenceRule(Frequency.DAILY, 1, until = d("2026-10-03"))
        assertEquals(3, until.occurrences(d("2026-10-01"), range("2026-10-01", "2026-10-31")).size)
        val count = RecurrenceRule(Frequency.DAILY, 1, count = 2)
        assertEquals(2, count.occurrences(d("2026-10-01"), range("2026-10-01", "2026-10-31")).size)
    }

    @Test
    fun `monthly rule clamps to the end of short months`() {
        val rule = RecurrenceRule(Frequency.MONTHLY)
        val dates = rule.occurrences(d("2026-01-31"), range("2026-01-01", "2026-04-30"))
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30").map(::d), dates)
    }

    @Test
    fun `exceptions are skipped and the range limits the result`() {
        val rule = RecurrenceRule(Frequency.DAILY)
        val dates = rule.occurrences(d("2026-10-01"), range("2026-10-03", "2026-10-05"), setOf(d("2026-10-04")))
        assertEquals(listOf(d("2026-10-03"), d("2026-10-05")), dates)
    }

    @Test
    fun `rules survive a text round trip and bad text is ignored`() {
        val rule = RecurrenceRule(Frequency.WEEKLY, 2, listOf(3, 1), d("2026-12-31"))
        assertEquals(rule.copy(weekdays = listOf(3, 1)), RecurrenceRule.parse(rule.toText())!!.copy(weekdays = listOf(3, 1)))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=1,3;UNTIL=2026-12-31", rule.toText())
        assertNull(RecurrenceRule.parse(""))
        assertNull(RecurrenceRule.parse("garbage"))
        assertNull(RecurrenceRule.parse("FREQ=YEARLY"))
        assertTrue(rule.describe().contains("כל 2 שבועות"))
    }

    @Test
    fun `an exception can be added to a series`() {
        val e = ActivityEvent("e", startsAt = 0, localDate = "2026-10-01", zoneId = "Asia/Jerusalem")
        val withEx = Exceptions.withException(Exceptions.withException(e, d("2026-10-08")), d("2026-10-02"))
        assertEquals("2026-10-02,2026-10-08", withEx.recurrenceExceptions)
    }

    // ---- calendar --------------------------------------------------------------------

    private val schedule = listOf(
        ScheduledWorkout("upperA", "Upper A", 7), // Sunday
        ScheduledWorkout("lowerA", "Lower A", 1), // Monday
        ScheduledWorkout("upperB", "Upper B", 3), // Wednesday
    )

    private fun build(
        r: DateRange,
        sessions: List<app.liftos.domain.analytics.SessionFact> = emptyList(),
        events: List<ActivityEvent> = emptyList(),
        overrides: List<PlannedOverride> = emptyList(),
        start: LocalDate? = d("2026-09-01"),
    ) = CalendarBuilder.build(r, today, schedule, start, sessions, events, overrides)

    @Test
    fun `future workouts of the plan appear as planned`() {
        val items = build(range("2026-10-14", "2026-10-20"))
        val planned = items.filter { it.kind == CalendarItemKind.STRENGTH_PLANNED }
        assertEquals(listOf("Upper B", "Upper A", "Lower A"), planned.map { it.title }) // Wed, Sun, Mon
    }

    @Test
    fun `a recorded workout replaces the planned entry of that day`() {
        val sessions = listOf(Fx.session("s1", "2026-10-14", title = "Upper B"))
        val items = build(range("2026-10-14", "2026-10-14"), sessions)
        assertEquals(listOf(CalendarItemKind.SESSION_DONE), items.map { it.kind })
    }

    @Test
    fun `a recent past planned day without a workout is flagged as missed, an old one is not`() {
        val recent = build(range("2026-10-11", "2026-10-12")) // Sun, Mon
        assertTrue(recent.all { it.kind == CalendarItemKind.STRENGTH_MISSED && it.automatic })
        assertEquals(2, recent.size)
        val old = build(range("2026-08-02", "2026-08-03"), start = d("2026-07-01"))
        assertTrue(old.isEmpty())
    }

    @Test
    fun `days before the plan existed are never missed`() {
        val items = build(range("2026-10-11", "2026-10-12"), start = d("2026-10-12"))
        assertEquals(listOf(d("2026-10-12")), items.map { it.date })
    }

    @Test
    fun `moving a planned workout shows it on the new day only`() {
        val ov = PlannedOverride("o1", "upperB", "2026-10-14", OverrideAction.MOVED, newDate = "2026-10-16", note = "מסיבה")
        val items = build(range("2026-10-14", "2026-10-17"), overrides = listOf(ov))
        val planned = items.filter { it.kind == CalendarItemKind.STRENGTH_PLANNED }
        assertEquals(1, planned.size)
        assertEquals(d("2026-10-16"), planned[0].date)
        assertTrue(planned[0].moved)
        assertEquals(d("2026-10-14"), planned[0].originalDate)
        assertEquals("מסיבה", planned[0].subtitle)
    }

    @Test
    fun `skipped and missed marks are explicit and never automatic`() {
        val overrides = listOf(
            PlannedOverride("o1", "upperA", "2026-10-11", OverrideAction.SKIPPED),
            PlannedOverride("o2", "lowerA", "2026-10-12", OverrideAction.MISSED),
        )
        val items = build(range("2026-10-11", "2026-10-12"), overrides = overrides)
        assertEquals(CalendarItemKind.STRENGTH_SKIPPED, items.first { it.date == d("2026-10-11") }.kind)
        assertEquals(CalendarItemKind.STRENGTH_MISSED, items.first { it.date == d("2026-10-12") }.kind)
        assertFalse(items.any { it.automatic })
    }

    @Test
    fun `repeating events expand and honour exceptions`() {
        val e = ActivityEvent(
            id = "walk", type = ActivityType.WALK, title = "הליכה", startsAt = Fx.startOf("2026-10-05", 7),
            localDate = "2026-10-05", zoneId = "UTC", durationMin = 30, status = EventStatus.PLANNED,
            recurrence = RecurrenceRule(Frequency.WEEKLY, 1, listOf(1, 4)).toText(), recurrenceExceptions = "2026-10-12",
        )
        val items = build(range("2026-10-05", "2026-10-18"), events = listOf(e)).filter { it.kind == CalendarItemKind.EVENT }
        assertEquals(listOf("2026-10-05", "2026-10-08", "2026-10-15").map(::d), items.map { it.date })
        assertTrue(items.all { it.recurring && it.startMinutes == 7 * 60 && it.durationMin == 30 })
    }

    @Test
    fun `rest days and single events are classified`() {
        val rest = ActivityEvent("r", type = ActivityType.REST, startsAt = Fx.startOf("2026-10-14"), localDate = "2026-10-14", zoneId = "UTC", allDay = true)
        val coach = ActivityEvent("c", type = ActivityType.COACH, title = "אימון אישי", startsAt = Fx.startOf("2026-10-15"), localDate = "2026-10-15", zoneId = "UTC")
        val items = build(range("2026-10-14", "2026-10-15"), events = listOf(rest, coach))
        assertEquals(CalendarItemKind.REST_DAY, items.first { it.eventId == "r" }.kind)
        assertNull(items.first { it.eventId == "r" }.startMinutes)
        assertNotNull(items.first { it.eventId == "c" }.startMinutes)
    }

    @Test
    fun `moving a plan day does not touch recorded workouts`() {
        val sessions = listOf(Fx.session("s1", "2026-10-11", title = "Upper A"))
        val ov = PlannedOverride("o1", "upperA", "2026-10-11", OverrideAction.MOVED, newDate = "2026-10-13")
        val items = build(range("2026-10-11", "2026-10-13"), sessions, overrides = listOf(ov))
        assertEquals(1, items.count { it.kind == CalendarItemKind.SESSION_DONE && it.date == d("2026-10-11") })
    }

    @Test
    fun `markers summarise each day for the month grid`() {
        val sessions = listOf(Fx.session("s1", "2026-10-11"))
        val items = build(range("2026-10-11", "2026-10-14"), sessions)
        val m = CalendarBuilder.markers(items)
        assertEquals(listOf(CalendarItemKind.SESSION_DONE), m[d("2026-10-11")])
        assertTrue(m[d("2026-10-14")]!!.contains(CalendarItemKind.STRENGTH_PLANNED))
    }
}
