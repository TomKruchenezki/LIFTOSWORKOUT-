package app.liftos.domain.logic

import app.liftos.domain.TestData
import app.liftos.domain.model.BodyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {

    @Test
    fun `comparison reports volume change versus previous session`() {
        val prev = TestData.benchDetail("p", "2026-10-04", listOf(60.0, 60.0, 60.0), listOf(10, 9, 8))
        val cur = TestData.benchDetail("c", "2026-10-11", listOf(60.0, 60.0, 60.0), listOf(11, 10, 9), start = 1_761_000_000_000L)
        val c = SessionComparator.compare(cur, prev)
        assertEquals(60.0 * 3, c.volumeDeltaKg, 0.0001)
        assertEquals(60.0 * 3 / (60.0 * 27) * 100, c.volumePercent!!, 0.0001)
        assertEquals(0, c.workingSetsDelta)
        assertEquals(Verdict.UP, c.exercises.single().verdict)
    }

    @Test
    fun `identical performance is reported as the same - never as improvement`() {
        val prev = TestData.benchDetail("p")
        val cur = TestData.benchDetail("c")
        assertEquals(Verdict.SAME, SessionComparator.compare(cur, prev).exercises.single().verdict)
    }

    @Test
    fun `lower performance is reported as down`() {
        val prev = TestData.benchDetail("p", weights = listOf(80.0, 80.0, 80.0), reps = listOf(8, 8, 8))
        val cur = TestData.benchDetail("c", weights = listOf(70.0, 70.0, 70.0), reps = listOf(8, 8, 8))
        val c = SessionComparator.compare(cur, prev)
        assertEquals(Verdict.DOWN, c.exercises.single().verdict)
        assertTrue(c.volumeDeltaKg < 0)
    }

    @Test
    fun `an exercise that did not exist before is new`() {
        val prev = TestData.benchDetail("p").copy(exercises = emptyList())
        val cur = TestData.benchDetail("c")
        assertEquals(Verdict.NEW, SessionComparator.compare(cur, prev).exercises.single().verdict)
        assertNull(SessionComparator.compare(cur, prev).volumePercent)
    }

    // ---- body trends ------------------------------------------------------------------------

    private fun m(date: String, kg: Double?) = BodyMeasurement(
        id = date, measuredAt = 1_700_000_000_000L, localDate = date, zoneId = "Asia/Jerusalem", weightKg = kg,
    )

    @Test
    fun `weight change over 7 days uses the closest earlier weigh-in`() {
        val list = listOf(m("2026-10-01", 82.0), m("2026-10-06", 81.4), m("2026-10-08", 81.0))
        val c = BodyTrends.weightChange(list, 7)!!
        assertEquals(-1.0, c.deltaKg, 0.0001) // 81.0 vs 82.0 (Oct 1 is exactly 7 days before Oct 8)
        assertEquals(LocalDate.of(2026, 10, 1), c.fromDate)
    }

    @Test
    fun `no change is reported without a suitable baseline`() {
        assertNull(BodyTrends.weightChange(listOf(m("2026-10-08", 81.0)), 7))
        // Only a 3-day-old baseline for a 30-day question.
        assertNull(BodyTrends.weightChange(listOf(m("2026-10-05", 82.0), m("2026-10-08", 81.0)), 30))
        // Measurements without weight are ignored.
        assertNull(BodyTrends.weightChange(listOf(m("2026-10-01", null), m("2026-10-08", 81.0)), 7))
    }

    @Test
    fun `moving average smooths daily noise`() {
        val pts = listOf(
            BodyTrends.Point(LocalDate.of(2026, 10, 1), 80.0),
            BodyTrends.Point(LocalDate.of(2026, 10, 2), 82.0),
            BodyTrends.Point(LocalDate.of(2026, 10, 3), 81.0),
        )
        val avg = BodyTrends.movingAverage(pts, 7)
        assertEquals(80.0, avg[0].value, 0.0001)
        assertEquals(81.0, avg[1].value, 0.0001)
        assertEquals(81.0, avg[2].value, 0.0001)
        assertNotNull(avg.last())
    }
}
