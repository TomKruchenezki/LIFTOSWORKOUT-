package app.liftos.data

import app.liftos.domain.calendar.CalendarBuilder
import app.liftos.domain.calendar.RecurrenceRule
import app.liftos.domain.calendar.Frequency
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.OverrideAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class CalendarRepositoryTest {
    private lateinit var env: TestEnv
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Before
    fun setUp() {
        env = TestEnv()
    }

    @After
    fun tearDown() = env.close()

    private fun ms(date: LocalDate, hour: Int = 18) = ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun event(date: LocalDate, recurrence: String = "", title: String = "ריצה") =
        ActivityEvent(id = "", type = ActivityType.RUN, title = title, startsAt = ms(date), localDate = date.toString(), zoneId = zone.id, status = EventStatus.PLANNED, recurrence = recurrence)

    private fun weekly(vararg days: Int) = RecurrenceRule(Frequency.WEEKLY, 1, days.toList()).toText()

    @Test
    fun `an event is stored with its local date and can be deleted`() = runBlocking {
        val id = env.activities.save(event(LocalDate.of(2026, 10, 20)))
        assertEquals("2026-10-20", env.activities.get(id)!!.localDate)
        env.activities.delete(id)
        assertNull(env.activities.get(id))
    }

    @Test
    fun `marking one occurrence of a repeating event leaves the rest of the series alone`() = runBlocking {
        val start = LocalDate.of(2026, 10, 19) // Monday
        val id = env.activities.save(event(start, weekly(1)))
        val second = start.plusWeeks(1)
        env.activities.setOccurrenceStatus(id, second, EventStatus.DONE)

        val all = env.activities.observeAll().first()
        assertEquals(2, all.size)
        val series = all.first { it.id == id }
        val single = all.first { it.id != id }
        assertEquals(second.toString(), single.localDate)
        assertEquals(EventStatus.DONE, single.status)
        assertEquals("", single.recurrence)
        assertEquals(EventStatus.PLANNED, series.status)

        // The calendar shows the series on the other Mondays, and the single done event on the second one.
        val range = DateRange(start, start.plusWeeks(3))
        val items = CalendarBuilder.build(range, start, emptyList(), null, emptyList(), all, emptyList())
        assertEquals(listOf(start, second, start.plusWeeks(2), start.plusWeeks(3)), items.map { it.date })
        assertEquals(EventStatus.DONE, items.first { it.date == second }.eventStatus)
        assertEquals(EventStatus.PLANNED, items.first { it.date == start.plusWeeks(2) }.eventStatus)
    }

    @Test
    fun `moving one occurrence keeps the time of day and the series`() = runBlocking {
        val start = LocalDate.of(2026, 10, 19)
        val id = env.activities.save(event(start, weekly(1)))
        val target = LocalDate.of(2026, 10, 28)
        env.activities.moveOccurrence(id, start.plusWeeks(1), ms(target))

        val all = env.activities.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("2026-10-28", all.first { it.id != id }.localDate)
        assertTrue(all.first { it.id == id }.recurrenceExceptions.contains("2026-10-26"))
    }

    @Test
    fun `deleting one occurrence only hides that date`() = runBlocking {
        val start = LocalDate.of(2026, 10, 19)
        val id = env.activities.save(event(start, weekly(1)))
        env.activities.deleteOccurrence(id, start.plusWeeks(1))
        val all = env.activities.observeAll().first()
        assertEquals(1, all.size)
        val items = CalendarBuilder.build(DateRange(start, start.plusWeeks(2)), start, emptyList(), null, emptyList(), all, emptyList())
        assertEquals(listOf(start, start.plusWeeks(2)), items.map { it.date })
    }

    @Test
    fun `linking an occurrence to a workout marks it done and can be undone`() = runBlocking {
        val id = env.activities.save(event(LocalDate.of(2026, 10, 20)))
        env.activities.linkOccurrence(id, LocalDate.of(2026, 10, 20), "session-1")
        assertEquals("session-1", env.activities.get(id)!!.linkedSessionId)
        assertEquals(EventStatus.DONE, env.activities.get(id)!!.status)
        env.activities.linkOccurrence(id, LocalDate.of(2026, 10, 20), null)
        assertNull(env.activities.get(id)!!.linkedSessionId)
    }

    @Test
    fun `moving a planned workout never touches recorded workouts`() = runBlocking {
        env.seeder.seedIfNeeded()
        val plan = env.plans.observePlanDetail(env.settings.observe().first().activePlanId!!).first()!!
        val workout = plan.workouts.first { it.workout.weekday != null }.workout
        val original = LocalDate.of(2026, 10, 20)
        val before = env.workouts.observeHistory().first().size

        env.activities.moveWorkout(workout.id, original, original.plusDays(2))
        var overrides = env.activities.observeOverrides().first()
        assertEquals(1, overrides.size)
        assertEquals(OverrideAction.MOVED, overrides[0].action)
        assertEquals("2026-10-22", overrides[0].newDate)

        // Marking the same occurrence replaces the override rather than stacking another one.
        env.activities.markWorkout(workout.id, original, OverrideAction.SKIPPED)
        overrides = env.activities.observeOverrides().first()
        assertEquals(1, overrides.size)
        assertEquals(OverrideAction.SKIPPED, overrides[0].action)

        env.activities.clearOverride(workout.id, original)
        assertTrue(env.activities.observeOverrides().first().isEmpty())
        assertEquals(before, env.workouts.observeHistory().first().size)
    }
}
