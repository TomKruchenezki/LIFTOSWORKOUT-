package app.liftos.domain.goals

import app.liftos.domain.analytics.Fx
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalStatus
import app.liftos.domain.model.GoalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalEngineTest {
    private val today = LocalDate.parse("2026-10-14")

    private fun weigh(date: String, kg: Double?, waist: Double? = null) = BodyMeasurement(
        id = "m-$date", measuredAt = Fx.startOf(date), localDate = date, zoneId = "UTC", weightKg = kg, waistCm = waist,
    )

    private fun goal(
        type: GoalType,
        start: Double,
        target: Double,
        key: String = "",
        startDate: String = "2026-09-14",
        targetDate: String? = "2026-12-14",
        current: Double? = null,
    ) = Goal("g", "יעד", type, key, "ק״ג", start, target, current, startDate, targetDate)

    @Test
    fun `weight loss goal measures progress from the latest weigh-in`() {
        val ctx = GoalContext(today, measurements = listOf(weigh("2026-09-14", 90.0), weigh("2026-10-10", 86.0)))
        val p = GoalEngine.progress(goal(GoalType.BODY_WEIGHT, 90.0, 80.0), ctx)
        assertEquals(86.0, p.current!!, 0.0)
        assertEquals(40.0, p.percent!!, 1e-9)
        assertEquals(6.0, p.remaining!!, 1e-9)
        assertFalse(p.achieved)
    }

    @Test
    fun `no data means no progress, not zero percent`() {
        val p = GoalEngine.progress(goal(GoalType.BODY_WEIGHT, 90.0, 80.0), GoalContext(today))
        assertNull(p.current)
        assertNull(p.percent)
        assertNull(p.onTrack)
        assertTrue(p.message.contains("אין עדיין נתונים"))
    }

    @Test
    fun `a goal is achieved when the target is reached in the right direction`() {
        val ctx = GoalContext(today, measurements = listOf(weigh("2026-10-10", 79.5)))
        assertTrue(GoalEngine.progress(goal(GoalType.BODY_WEIGHT, 90.0, 80.0), ctx).achieved)
        val gain = GoalContext(today, measurements = listOf(weigh("2026-10-10", 79.5)))
        assertFalse(GoalEngine.progress(goal(GoalType.BODY_WEIGHT, 70.0, 80.0), gain).achieved)
    }

    @Test
    fun `body measure goals use the chosen field`() {
        val ctx = GoalContext(today, measurements = listOf(weigh("2026-10-01", 80.0, waist = 92.0), weigh("2026-10-10", 80.0, waist = 90.0)))
        val p = GoalEngine.progress(goal(GoalType.BODY_MEASURE, 94.0, 86.0, key = "waist"), ctx)
        assertEquals(90.0, p.current!!, 0.0)
        assertEquals(50.0, p.percent!!, 1e-9)
    }

    @Test
    fun `strength goals read the best weight from the log`() {
        val facts = Fx.sets("s1", "2026-10-01", listOf(90.0, 95.0), listOf(5, 3)) + Fx.sets("s2", "2026-10-08", listOf(97.5), listOf(2))
        val p = GoalEngine.progress(goal(GoalType.EXERCISE_WEIGHT, 90.0, 100.0, key = "bench"), GoalContext(today, facts = facts))
        assertEquals(97.5, p.current!!, 0.0)
        assertEquals(75.0, p.percent!!, 1e-9)
    }

    @Test
    fun `rep goals use unloaded reps`() {
        val facts = listOf(Fx.fact("s1", "2026-10-01", null, 7, exercise = "pullup"), Fx.fact("s2", "2026-10-08", null, 9, exercise = "pullup"))
        val p = GoalEngine.progress(goal(GoalType.EXERCISE_REPS, 5.0, 10.0, key = "pullup"), GoalContext(today, facts = facts))
        assertEquals(9.0, p.current!!, 0.0)
        assertEquals(80.0, p.percent!!, 1e-9)
    }

    @Test
    fun `frequency goals count workouts in the current month and week`() {
        val sessions = listOf("2026-10-01", "2026-10-03", "2026-10-11", "2026-10-13").map { Fx.session("s$it", it) } +
            Fx.session("old", "2026-09-30")
        val ctx = GoalContext(today, sessions = sessions)
        assertEquals(4.0, GoalEngine.progress(goal(GoalType.WORKOUTS_PER_MONTH, 0.0, 16.0), ctx).current!!, 0.0)
        // Current week is Sun 11 .. Sat 17 October.
        assertEquals(2.0, GoalEngine.progress(goal(GoalType.WORKOUTS_PER_WEEK, 0.0, 4.0), ctx).current!!, 0.0)
        assertEquals(25.0, GoalEngine.progress(goal(GoalType.WORKOUTS_PER_MONTH, 0.0, 16.0), ctx).percent!!, 1e-9)
    }

    @Test
    fun `program weeks count only weeks that met the minimum`() {
        // Goal started Sun 2026-09-20; weeks: Sep 20-26 (3 workouts), Sep 27-Oct 3 (1 workout), Oct 4-10 (3), running Oct 11-17 (3).
        val dates = listOf(
            "2026-09-20", "2026-09-22", "2026-09-24",
            "2026-09-28",
            "2026-10-04", "2026-10-06", "2026-10-08",
            "2026-10-11", "2026-10-12", "2026-10-13",
        )
        val ctx = GoalContext(today, sessions = dates.map { Fx.session("s$it", it) })
        val g = goal(GoalType.PROGRAM_WEEKS, 0.0, 12.0, key = "3", startDate = "2026-09-20")
        assertEquals(3.0, GoalEngine.progress(g, ctx).current!!, 0.0)
    }

    @Test
    fun `custom goals use the value the user typed`() {
        val none = goal(GoalType.CUSTOM, 0.0, 10.0)
        assertNull(GoalEngine.progress(none, GoalContext(today)).percent)
        val some = goal(GoalType.CUSTOM, 0.0, 10.0, current = 4.0)
        assertEquals(40.0, GoalEngine.progress(some, GoalContext(today)).percent!!, 1e-9)
    }

    @Test
    fun `on track compares progress with the dates`() {
        // 2026-09-14 -> 2026-12-14 is 91 days; on 2026-10-14 that is ~33% elapsed.
        val behind = GoalContext(today, measurements = listOf(weigh("2026-10-10", 89.0)))
        val p1 = GoalEngine.progress(goal(GoalType.BODY_WEIGHT, 90.0, 80.0), behind)
        assertEquals(false, p1.onTrack)
        val ahead = GoalContext(today, measurements = listOf(weigh("2026-10-10", 86.0)))
        assertEquals(true, GoalEngine.progress(goal(GoalType.BODY_WEIGHT, 90.0, 80.0), ahead).onTrack)
        assertNotNull(p1.daysLeft)
        assertEquals(61L, p1.daysLeft)
    }

    @Test
    fun `newly achieved lists only active goals that reached the target`() {
        val ctx = GoalContext(today, measurements = listOf(weigh("2026-10-10", 79.0)))
        val done = goal(GoalType.BODY_WEIGHT, 90.0, 80.0)
        val alreadyMarked = done.copy(id = "h", status = GoalStatus.ACHIEVED)
        val open = goal(GoalType.BODY_WEIGHT, 90.0, 70.0).copy(id = "o")
        val result = GoalEngine.newlyAchieved(listOf(done, alreadyMarked, open), ctx)
        assertEquals(listOf("g"), result.map { it.first.id })
    }

    @Test
    fun `a suggested start comes from the current data`() {
        val ctx = GoalContext(today, measurements = listOf(weigh("2026-10-10", 84.0)))
        assertEquals(84.0, GoalEngine.suggestedStart(GoalType.BODY_WEIGHT, "", ctx)!!, 0.0)
        assertNull(GoalEngine.suggestedStart(GoalType.CUSTOM, "", ctx))
        assertEquals(0.0, GoalEngine.suggestedStart(GoalType.WORKOUTS_PER_MONTH, "", ctx)!!, 0.0)
    }
}
