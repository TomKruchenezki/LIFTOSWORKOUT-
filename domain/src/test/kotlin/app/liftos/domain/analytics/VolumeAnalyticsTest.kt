package app.liftos.domain.analytics

import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VolumeAnalyticsTest {
    private val range = DateRange(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-10")) // Sun..Sat

    @Test
    fun `period stats sum only working sets and workouts in range`() {
        val facts = Fx.sets("s1", "2026-10-05", listOf(60.0, 60.0), listOf(10, 10)) +
            Fx.fact("s1", "2026-10-05", 20.0, 15, type = SetType.WARMUP, order = 5) +
            Fx.sets("old", "2026-09-01", listOf(100.0), listOf(5))
        val sessions = listOf(Fx.session("s1", "2026-10-05", 50), Fx.session("old", "2026-09-01", 70))
        val p = VolumeAnalytics.periodStats(facts, sessions, range)
        assertEquals(1, p.workouts)
        assertEquals(2, p.workingSets)
        assertEquals(20, p.totalReps)
        assertEquals(1200.0, p.volumeKg, 1e-9)
        assertEquals(50 * 60_000L, p.avgDurationMs)
        assertEquals(1, p.trainedDays)
    }

    @Test
    fun `comparison against the previous equal period`() {
        val facts = Fx.sets("now", "2026-10-05", listOf(60.0), listOf(10)) + Fx.sets("then", "2026-09-29", listOf(50.0), listOf(10))
        val sessions = listOf(Fx.session("now", "2026-10-05"), Fx.session("then", "2026-09-29"))
        val c = VolumeAnalytics.compare(facts, sessions, range)
        assertEquals(600.0, c.volumeKg.current, 1e-9)
        assertEquals(500.0, c.volumeKg.previous, 1e-9)
        assertEquals(20.0, c.volumeKg.percent!!, 1e-9)
        assertEquals(1.0, c.workouts.current, 0.0)
    }

    @Test
    fun `percent change is undefined when the previous period had nothing`() {
        val facts = Fx.sets("now", "2026-10-05", listOf(60.0), listOf(10))
        val c = VolumeAnalytics.compare(facts, listOf(Fx.session("now", "2026-10-05")), range)
        assertNull(c.volumeKg.percent)
        assertEquals(0.0, c.volumeKg.previous, 0.0)
    }

    @Test
    fun `buckets keep empty weeks and start on Sunday`() {
        val facts = Fx.sets("a", "2026-10-05", listOf(60.0), listOf(10)) + Fx.sets("b", "2026-10-20", listOf(60.0), listOf(10))
        val sessions = listOf(Fx.session("a", "2026-10-05"), Fx.session("b", "2026-10-20"))
        val buckets = VolumeAnalytics.buckets(facts, sessions, DateRange(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-24")), Granularity.WEEK)
        assertEquals(3, buckets.size)
        assertEquals(LocalDate.parse("2026-10-04"), buckets[0].start)
        assertEquals(1, buckets[0].workouts)
        assertEquals(0, buckets[1].workouts)
        assertEquals(0.0, buckets[1].volumeKg, 0.0)
        assertEquals(1, buckets[2].workouts)
    }

    @Test
    fun `direct and indirect muscle sets follow the stated model`() {
        val bench = Fx.sets("s", "2026-10-05", listOf(60.0, 60.0, 60.0, 60.0), listOf(8, 8, 8, 8),
            secondary = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS))
        val press = Fx.sets("s", "2026-10-05", listOf(30.0, 30.0), listOf(10, 10), exercise = "ohp", name = "לחיצת כתפיים",
            muscle = MuscleGroup.SHOULDERS, secondary = listOf(MuscleGroup.TRICEPS))
        val loads = VolumeAnalytics.muscleLoads(bench + press, range).associateBy { it.muscle }
        assertEquals(4, loads[MuscleGroup.CHEST]!!.directSets)
        assertEquals(2, loads[MuscleGroup.SHOULDERS]!!.directSets)
        assertEquals(2.0, loads[MuscleGroup.SHOULDERS]!!.indirectSets, 1e-9) // 4 bench sets x 0.5
        assertEquals(0, loads[MuscleGroup.TRICEPS]!!.directSets)
        assertEquals(3.0, loads[MuscleGroup.TRICEPS]!!.indirectSets, 1e-9) // (4 + 2) x 0.5
        assertEquals(4.0, loads[MuscleGroup.SHOULDERS]!!.sets(includeIndirect = true), 1e-9)
        assertEquals(2.0, loads[MuscleGroup.SHOULDERS]!!.sets(includeIndirect = false), 1e-9)
        assertEquals(1, loads[MuscleGroup.CHEST]!!.workouts)
        assertTrue(MuscleModel.ASSUMPTIONS.contains("חצי סט"))
    }

    @Test
    fun `warm-ups never count toward muscle sets`() {
        val facts = listOf(Fx.fact("s", "2026-10-05", 20.0, 10, type = SetType.WARMUP))
        assertTrue(VolumeAnalytics.muscleLoads(facts, range).isEmpty())
    }

    @Test
    fun `earliest date comes from sessions or facts`() {
        val facts = Fx.sets("a", "2026-10-05", listOf(60.0), listOf(10))
        assertEquals(LocalDate.parse("2026-09-30"), VolumeAnalytics.earliestDate(listOf(Fx.session("x", "2026-09-30")), facts))
        assertNotNull(VolumeAnalytics.earliestDate(emptyList(), facts))
        assertNull(VolumeAnalytics.earliestDate(emptyList(), emptyList()))
    }

    @Test
    fun `periods resolve to concrete ranges`() {
        val today = LocalDate.parse("2026-10-13")
        assertEquals(DateRange(LocalDate.parse("2026-10-07"), today), Period(PeriodPreset.DAYS_7).resolve(today, null))
        assertEquals(30, Period(PeriodPreset.DAYS_30).resolve(today, null).days)
        assertEquals(LocalDate.parse("2026-01-02"), Period(PeriodPreset.ALL).resolve(today, LocalDate.parse("2026-01-02")).start)
        val custom = DateRange(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"))
        assertEquals(custom, Period(PeriodPreset.CUSTOM, custom).resolve(today, null))
        assertEquals(30, Period(PeriodPreset.CUSTOM, null).resolve(today, null).days)
    }

    @Test
    fun `granularity grows with the range`() {
        val today = LocalDate.parse("2026-10-13")
        assertEquals(Granularity.DAY, Buckets.granularityFor(DateRange.lastDays(30, today)))
        assertEquals(Granularity.WEEK, Buckets.granularityFor(DateRange.lastDays(90, today)))
        assertEquals(Granularity.MONTH, Buckets.granularityFor(DateRange.lastDays(800, today)))
    }
}
