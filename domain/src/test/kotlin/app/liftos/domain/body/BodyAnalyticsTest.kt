package app.liftos.domain.body

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.Fx
import app.liftos.domain.goals.BodyField
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.MeasurementCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyAnalyticsTest {
    private val today = LocalDate.parse("2026-10-14")

    private fun m(date: String, kg: Double? = null, waist: Double? = null, fat: Double? = null, cond: List<MeasurementCondition> = emptyList()) =
        BodyMeasurement("m-$date", Fx.startOf(date), date, "UTC", weightKg = kg, waistCm = waist, bodyFatPct = fat, conditions = cond)

    private fun daily(start: String, values: List<Double>) =
        values.mapIndexed { i, v -> m(LocalDate.parse(start).plusDays(i.toLong() * 2).toString(), kg = v) }

    @Test
    fun `series keeps only measurements that have the field`() {
        val ms = listOf(m("2026-10-01", kg = 80.0), m("2026-10-05", waist = 90.0), m("2026-10-08", kg = 79.5, cond = listOf(MeasurementCondition.MORNING)))
        val pts = BodyAnalytics.series(ms, BodyField.WEIGHT)
        assertEquals(listOf(80.0, 79.5), pts.map { it.value })
        assertEquals(listOf(MeasurementCondition.MORNING), pts[1].conditions)
        assertEquals(1, BodyAnalytics.series(ms, BodyField.WAIST).size)
        assertEquals(1, BodyAnalytics.series(ms, BodyField.WEIGHT, DateRange(LocalDate.parse("2026-10-05"), today)).size)
    }

    @Test
    fun `two measurements compare field by field with percent`() {
        val a = m("2026-09-14", kg = 90.0, waist = 100.0)
        val b = m("2026-10-14", kg = 87.0, waist = 96.0, fat = 20.0)
        val cmp = BodyAnalytics.compare(a, b).associateBy { it.field }
        assertEquals(-3.0, cmp[BodyField.WEIGHT]!!.change!!.delta, 1e-9)
        assertEquals(-4.0, cmp[BodyField.WAIST]!!.change!!.percent!!, 1e-9)
        assertNull(cmp[BodyField.BODY_FAT]!!.change) // only one side has it
        assertNull(cmp[BodyField.CHEST])
    }

    @Test
    fun `period averages are compared`() {
        val ms = listOf(m("2026-09-02", kg = 90.0), m("2026-09-20", kg = 88.0), m("2026-10-05", kg = 86.0), m("2026-10-12", kg = 85.0))
        val r = BodyAnalytics.comparePeriods(
            ms, BodyField.WEIGHT,
            DateRange(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
            DateRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")),
        )
        assertEquals(89.0, r.a.average!!, 1e-9)
        assertEquals(85.5, r.b.average!!, 1e-9)
        assertEquals(-3.5, r.change!!.delta, 1e-9)
        assertEquals(2, r.b.count)
    }

    @Test
    fun `a steady decline is a falling trend`() {
        val ms = daily("2026-09-18", listOf(90.0, 89.6, 89.3, 88.9, 88.6, 88.2, 87.9, 87.5, 87.2, 86.8))
        val t = BodyAnalytics.trend(ms, BodyField.WEIGHT, today)
        assertEquals(TrendDirection.FALLING, t.direction)
        assertTrue(t.slopePerWeek!! < -1.0)
        assertTrue(t.message.contains("לא מבדילות בין שומן, שריר ונוזלים"))
    }

    @Test
    fun `daily noise without a direction is stable`() {
        val ms = daily("2026-09-18", listOf(80.0, 80.8, 79.5, 80.6, 79.7, 80.5, 79.8, 80.4, 80.0, 80.2))
        val t = BodyAnalytics.trend(ms, BodyField.WEIGHT, today)
        assertEquals(TrendDirection.STABLE, t.direction)
        assertTrue(t.message.contains("אין מגמה ברורה"))
    }

    @Test
    fun `too few readings give no trend`() {
        val t = BodyAnalytics.trend(listOf(m("2026-10-10", kg = 80.0), m("2026-10-12", kg = 79.0)), BodyField.WEIGHT, today)
        assertEquals(TrendDirection.UNCLEAR, t.direction)
        assertNull(t.slopePerWeek)
    }

    @Test
    fun `unusual values trigger a gentle warning`() {
        val ms = listOf(m("2026-10-01", kg = 80.0), m("2026-10-05", kg = 80.5), m("2026-10-08", kg = 79.8))
        assertNull(BodyAnalytics.outlierWarning(ms, BodyField.WEIGHT, 80.2, today))
        assertNotNull(BodyAnalytics.outlierWarning(ms, BodyField.WEIGHT, 8.0, today))
        assertNotNull(BodyAnalytics.outlierWarning(ms, BodyField.WEIGHT, 95.0, today))
        assertNotNull(BodyAnalytics.outlierWarning(emptyList(), BodyField.WEIGHT, 800.0, today))
        assertNull(BodyAnalytics.outlierWarning(emptyList(), BodyField.WEIGHT, 82.0, today))
        assertNotNull(BodyAnalytics.outlierWarning(ms, BodyField.WAIST, -3.0, today))
    }

    @Test
    fun `custom metrics have their own series`() {
        val ms = listOf(
            m("2026-10-01").copy(customValues = mapOf("knee" to 38.0)),
            m("2026-10-08").copy(customValues = mapOf("knee" to 38.5, "calf" to 36.0)),
        )
        assertEquals(listOf(38.0, 38.5), BodyAnalytics.customSeries(ms, "knee").map { it.value })
        assertEquals(1, BodyAnalytics.customSeries(ms, "calf").size)
        assertTrue(ms[0].hasAnyValue)
    }
}
