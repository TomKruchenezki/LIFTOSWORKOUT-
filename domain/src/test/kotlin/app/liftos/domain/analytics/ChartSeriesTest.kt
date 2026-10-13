package app.liftos.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartSeriesTest {
    private val range = DateRange(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"))

    private val facts = Fx.sets("s1", "2026-09-10", listOf(60.0, 60.0), listOf(10, 8)) +
        Fx.sets("s2", "2026-10-05", listOf(62.5, 60.0), listOf(8, 10)) +
        Fx.sets("old", "2025-01-01", listOf(40.0), listOf(10))

    @Test
    fun `metric points carry exact value, date and workout`() {
        val stats = ExerciseAnalytics.sessionStats(facts.filter { it.key == "bench" })
        val pts = ChartSeries.exerciseMetric(stats, TrendMetric.TOP_WEIGHT, range)
        assertEquals(2, pts.size) // the 2025 workout is outside the range
        assertEquals(listOf(60.0, 62.5), pts.map { it.value })
        assertEquals("s2", pts[1].sessionId)
        assertTrue(pts[1].title.contains("62.5"))
        assertTrue(pts[1].detail.contains("05/10/2026"))
    }

    @Test
    fun `reps at a fixed weight use the best set of each workout`() {
        val pts = ChartSeries.repsAtWeight(facts, "bench", 60.0, range)
        assertEquals(listOf(10.0, 10.0), pts.map { it.value })
        assertEquals(listOf("s1", "s2"), pts.map { it.sessionId })
    }

    @Test
    fun `bucket series name the period and skip missing durations`() {
        val sessions = listOf(Fx.session("s1", "2026-09-10", 50), Fx.session("s2", "2026-10-05", null))
        val buckets = VolumeAnalytics.buckets(facts, sessions, range, Granularity.WEEK)
        val volume = ChartSeries.bucketSeries(buckets, ChartSeries.BucketMetric.VOLUME, Granularity.WEEK)
        assertEquals(buckets.size, volume.size)
        val durations = ChartSeries.bucketSeries(buckets, ChartSeries.BucketMetric.DURATION, Granularity.WEEK)
        assertEquals(1, durations.size)
        assertEquals(50.0, durations[0].value, 1e-9)
    }

    @Test
    fun `distribution fractions add up to one`() {
        val loads = VolumeAnalytics.muscleLoads(facts, range)
        val slices = ChartSeries.muscleDistribution(loads, includeIndirect = false)
        assertEquals(1.0, slices.sumOf { it.fraction }, 1e-9)
        assertTrue(ChartSeries.muscleDistribution(emptyList(), false).isEmpty())
    }

    @Test
    fun `workout volume points skip workouts without volume`() {
        val sessions = listOf(Fx.session("s1", "2026-09-10"), Fx.session("empty", "2026-09-12"))
        val pts = ChartSeries.workoutVolume(facts, sessions, range)
        assertEquals(1, pts.size)
        assertEquals(60.0 * 18, pts[0].value, 1e-9)
    }
}
