package app.liftos.domain.analytics

import app.liftos.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardTest {
    private val today = LocalDate.parse("2026-10-13") // Tuesday; week is Sun 11 .. Sat 17

    @Test
    fun `strength trend compares equal windows and ignores one-sided exercises`() {
        val facts = Fx.sets("p1", "2026-09-05", listOf(60.0), listOf(8)) + // prior window (Sep 2 .. Sep 15)? no: prior = Aug 19..Sep 15
            Fx.sets("c1", "2026-10-05", listOf(65.0), listOf(8)) +
            Fx.sets("p2", "2026-09-10", listOf(100.0), listOf(5), exercise = "squat", name = "סקוואט", muscle = MuscleGroup.QUADS) +
            Fx.sets("c2", "2026-10-06", listOf(95.0), listOf(5), exercise = "squat", name = "סקוואט", muscle = MuscleGroup.QUADS) +
            Fx.sets("c3", "2026-10-07", listOf(30.0), listOf(10), exercise = "curl", name = "כפיפות", muscle = MuscleGroup.BICEPS)
        val t = DashboardAnalytics.strengthTrend(facts, today)
        assertEquals(2, t.tracked) // curl has no earlier window
        assertEquals(1, t.improved)
        assertEquals(1, t.declined)
        assertTrue(t.hasEnoughData)
    }

    @Test
    fun `no trend is claimed without data in both windows`() {
        val facts = Fx.sets("c1", "2026-10-05", listOf(65.0), listOf(8))
        val t = DashboardAnalytics.strengthTrend(facts, today)
        assertFalse(t.hasEnoughData)
        assertNull(t.medianPercent)
    }

    @Test
    fun `small changes are reported as level`() {
        val facts = Fx.sets("p1", "2026-09-10", listOf(100.0), listOf(5)) + Fx.sets("c1", "2026-10-05", listOf(101.0), listOf(5))
        val t = DashboardAnalytics.strengthTrend(facts, today)
        assertEquals(1, t.same)
        assertEquals(0, t.improved)
    }

    @Test
    fun `adherence caps at 100 percent and needs a target`() {
        val sessions = (1..10).map { Fx.session("s$it", "2026-10-${it.toString().padStart(2, '0')}") }
        val a = DashboardAnalytics.adherence(sessions, 3, today)!!
        assertEquals(12, a.expected) // 3 per week over 4 weeks
        assertEquals(10, a.completed)
        assertEquals(83, a.percent)
        assertEquals(100, Adherence(15, 12, 28).percent)
        assertNull(DashboardAnalytics.adherence(sessions, 0, today))
    }

    @Test
    fun `cards summarise the current week from the data only`() {
        val facts = Fx.sets("now", "2026-10-12", listOf(60.0, 60.0), listOf(10, 10))
        val sessions = listOf(Fx.session("now", "2026-10-12", 45))
        val records = PersonalRecordEngine.compute(facts)
        val cards = DashboardAnalytics.cards(facts, sessions, records, today, 4)
        assertEquals(1, cards.week.workouts)
        assertEquals(2, cards.week.workingSets)
        assertEquals(1200.0, cards.week.volumeKg, 1e-9)
        assertEquals(listOf(MuscleGroup.CHEST), cards.musclesTrainedThisWeek)
        assertEquals(0, cards.newPrsThisWeek)
        assertNotNull(cards.adherence)
        assertEquals(45 * 60_000L, cards.avgDurationMs)
        assertTrue(cards.hasAnyWorkout)
    }

    @Test
    fun `empty database gives empty cards, not made-up numbers`() {
        val cards = DashboardAnalytics.cards(emptyList(), emptyList(), RecordsResult(emptyList(), emptyList()), today, 0)
        assertFalse(cards.hasAnyWorkout)
        assertEquals(0, cards.week.workouts)
        assertEquals(0.0, cards.week.volumeKg, 0.0)
        assertNull(cards.adherence)
        assertNull(cards.avgDurationMs)
        assertFalse(cards.strength.hasEnoughData)
    }
}
