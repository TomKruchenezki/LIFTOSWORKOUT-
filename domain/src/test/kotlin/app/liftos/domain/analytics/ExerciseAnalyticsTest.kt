package app.liftos.domain.analytics

import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExerciseAnalyticsTest {
    private val today = LocalDate.parse("2026-10-13")

    private fun bench(session: String, date: String, w: List<Double>, r: List<Int>) =
        Fx.sets(session, date, w, r)

    @Test
    fun `session stats count volume, top set and estimated 1RM from working sets only`() {
        val facts = bench("s1", "2026-10-01", listOf(60.0, 60.0, 60.0), listOf(10, 9, 8)) +
            Fx.fact("s1", "2026-10-01", 20.0, 12, type = SetType.WARMUP, order = 9)
        val stat = ExerciseAnalytics.sessionStats(facts).single()
        assertEquals(3, stat.workingSets)
        assertEquals(60.0 * 27, stat.volumeKg, 1e-9)
        assertEquals(60.0, stat.topWeightKg!!, 0.0)
        assertEquals(10, stat.repsAtTopWeight)
        assertEquals(60.0 * (1 + 10 / 30.0), stat.bestE1rmKg!!, 1e-9)
        assertEquals(60.0, stat.avgWorkingWeightKg!!, 1e-9)
        assertEquals(9.0, stat.avgReps!!, 1e-9)
    }

    @Test
    fun `average working weight is weighted by reps when weights differ`() {
        val facts = Fx.sets("s1", "2026-10-01", listOf(100.0, 80.0), listOf(5, 10))
        val stat = ExerciseAnalytics.sessionStats(facts).single()
        assertEquals((500.0 + 800.0) / 15.0, stat.avgWorkingWeightKg!!, 1e-9)
    }

    @Test
    fun `overview separates logged singles from estimates and finds rep maxes`() {
        val facts = bench("s1", "2026-09-01", listOf(80.0, 80.0), listOf(8, 6)) +
            bench("s2", "2026-09-20", listOf(90.0), listOf(5)) +
            bench("s3", "2026-10-05", listOf(100.0, 70.0), listOf(1, 12))
        val o = ExerciseAnalytics.overview(facts, "bench", today)!!
        assertEquals(100.0, o.maxWeight!!.value, 0.0)
        assertEquals(100.0, o.actualOneRm!!.value, 0.0)
        assertEquals(90.0, o.repMax[5]!!.value, 0.0) // heaviest weight for at least 5 reps
        assertEquals(80.0, o.repMax[8]!!.value, 0.0)
        assertEquals(70.0, o.repMax[10]!!.value, 0.0)
        // 1RM estimate: best of 100x1=100, 90x5=105, 70x12=98, 80x8=101.33
        assertEquals(90.0 * (1 + 5 / 30.0), o.bestE1rm!!.value, 1e-9)
        assertEquals(3, o.sessionCount)
    }

    @Test
    fun `no actual 1RM is reported when no single was logged`() {
        val facts = bench("s1", "2026-10-01", listOf(80.0), listOf(5))
        assertNull(ExerciseAnalytics.overview(facts, "bench", today)!!.actualOneRm)
    }

    @Test
    fun `trend needs two workouts in the window and reports first versus last`() {
        val facts = bench("s1", "2026-09-20", listOf(60.0), listOf(10)) +
            bench("s2", "2026-10-11", listOf(65.0), listOf(10))
        val o = ExerciseAnalytics.overview(facts, "bench", today)!!
        val month = o.trends[0]!!
        assertEquals(30, month.windowDays)
        assertEquals(2, month.sessions)
        assertEquals(65.0 * (1 + 10 / 30.0) - 60.0 * (1 + 10 / 30.0), month.delta, 1e-9)
        assertTrue(month.percent!! > 0)

        val single = ExerciseAnalytics.overview(bench("s1", "2026-10-01", listOf(60.0), listOf(10)), "bench", today)!!
        assertNull(single.trends[0])
        assertNull(single.lastVsPrevious)
    }

    @Test
    fun `last versus previous gives a verdict`() {
        fun verdict(prev: Pair<Double, Int>, cur: Pair<Double, Int>): ChangeVerdict {
            val facts = bench("a", "2026-10-01", listOf(prev.first), listOf(prev.second)) +
                bench("b", "2026-10-08", listOf(cur.first), listOf(cur.second))
            return ExerciseAnalytics.overview(facts, "bench", today)!!.lastVsPrevious!!.verdict
        }
        assertEquals(ChangeVerdict.UP, verdict(60.0 to 8, 60.0 to 9))
        assertEquals(ChangeVerdict.DOWN, verdict(60.0 to 8, 57.5 to 8))
        assertEquals(ChangeVerdict.SAME, verdict(60.0 to 8, 60.0 to 8))
    }

    @Test
    fun `weight table groups reps per weight`() {
        val facts = bench("a", "2026-10-01", listOf(60.0, 60.0, 62.5), listOf(10, 8, 6)) +
            bench("b", "2026-10-08", listOf(60.0), listOf(12))
        val table = ExerciseAnalytics.weightTable(facts, "bench")
        assertEquals(listOf(62.5, 60.0), table.map { it.weightKg })
        val row60 = table.first { it.weightKg == 60.0 }
        assertEquals(3, row60.sets)
        assertEquals(12, row60.bestReps)
        assertEquals(30, row60.totalReps)
        assertEquals(2, row60.sessions)
    }

    @Test
    fun `a unilateral pair counts as one set but volume sums both sides`() {
        val facts = listOf(
            Fx.fact("s", "2026-10-01", 20.0, 10, exercise = "row", unilateral = true, side = Side.RIGHT, order = 0),
            Fx.fact("s", "2026-10-01", 20.0, 10, exercise = "row", unilateral = true, side = Side.LEFT, order = 1),
            Fx.fact("s", "2026-10-01", 20.0, 9, exercise = "row", unilateral = true, side = Side.RIGHT, order = 2),
            Fx.fact("s", "2026-10-01", 20.0, 9, exercise = "row", unilateral = true, side = Side.LEFT, order = 3),
        )
        val stat = ExerciseAnalytics.sessionStats(facts).single()
        assertEquals(2, stat.workingSets)
        assertEquals(20.0 * 38, stat.volumeKg, 1e-9)
    }

    @Test
    fun `bodyweight exercises report max reps and no volume`() {
        val facts = listOf(
            Fx.fact("s1", "2026-10-01", null, 8, exercise = "pullup", name = "מתח", tracking = TrackingType.BODYWEIGHT_REPS),
            Fx.fact("s1", "2026-10-01", null, 6, exercise = "pullup", name = "מתח", tracking = TrackingType.BODYWEIGHT_REPS, order = 1),
        )
        val o = ExerciseAnalytics.overview(facts, "pullup", today)!!
        assertEquals(8.0, o.maxRepsUnloaded!!.value, 0.0)
        assertEquals(TrendMetric.REPS, o.metric)
        assertEquals(0.0, o.totalVolumeKg, 0.0)
        assertNull(o.maxWeight)
        assertNull(o.bestE1rm)
    }

    @Test
    fun `duration exercises are measured in seconds`() {
        val facts = listOf(
            Fx.fact("s1", "2026-10-01", null, null, exercise = "plank", name = "פלאנק", tracking = TrackingType.DURATION, duration = 45),
            Fx.fact("s2", "2026-10-08", null, null, exercise = "plank", name = "פלאנק", tracking = TrackingType.DURATION, duration = 60),
        )
        val o = ExerciseAnalytics.overview(facts, "plank", today)!!
        assertEquals(TrendMetric.DURATION, o.metric)
        assertEquals(60.0, o.maxDuration!!.value, 0.0)
        assertEquals(ChangeVerdict.UP, o.lastVsPrevious!!.verdict)
    }

    @Test
    fun `list orders exercises by most recent and ignores warm-up only exercises`() {
        val facts = bench("a", "2026-10-01", listOf(60.0), listOf(10)) +
            Fx.sets("b", "2026-10-08", listOf(50.0), listOf(10), exercise = "squat", name = "סקוואט") +
            listOf(Fx.fact("c", "2026-10-09", 20.0, 10, exercise = "curl", type = SetType.WARMUP))
        val rows = ExerciseAnalytics.list(facts)
        assertEquals(listOf("squat", "bench"), rows.map { it.key })
        assertNotNull(rows[0].lastTop)
    }

    @Test
    fun `exercises without a library id are matched by name`() {
        val a = Fx.fact("a", "2026-10-01", 40.0, 10).copy(exerciseId = null, exerciseNameEn = "Cable Fly")
        val b = Fx.fact("b", "2026-10-08", 42.5, 10).copy(exerciseId = null, exerciseNameEn = "cable fly")
        val rows = ExerciseAnalytics.list(listOf(a, b))
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].sessions)
    }
}
