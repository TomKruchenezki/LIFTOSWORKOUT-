package app.liftos.data

import app.liftos.data.repo.MeasurementInput
import app.liftos.domain.logic.BodyTrends
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
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class MeasurementTest {
    private lateinit var env: TestEnv
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Before
    fun setUp() {
        env = TestEnv()
    }

    @After
    fun tearDown() = env.close()

    private fun ms(y: Int, m: Int, d: Int, h: Int = 8) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `not every metric has to be entered`() = runBlocking {
        val id = env.measurements.add(MeasurementInput(weightKg = 82.4), ms(2026, 10, 1))
        val m = env.measurements.get(id)!!
        assertEquals(82.4, m.weightKg!!, 0.0)
        assertNull(m.waistCm); assertNull(m.bodyFatPct)
        assertEquals("2026-10-01", m.localDate)
    }

    @Test
    fun `all metrics are stored and a note only entry is allowed`() = runBlocking {
        val full = env.measurements.add(
            MeasurementInput(weightKg = 82.4, waistCm = 91.0, chestCm = 104.5, armCm = 36.0, thighCm = 60.0, hipsCm = 99.0, neckCm = 39.0, bodyFatPct = 18.5, notes = "אחרי אימון"), ms(2026, 10, 2),
        )
        val m = env.measurements.get(full)!!
        assertEquals(listOf(82.4, 91.0, 104.5, 36.0, 60.0, 99.0, 39.0, 18.5), listOf(m.weightKg, m.waistCm, m.chestCm, m.armCm, m.thighCm, m.hipsCm, m.neckCm, m.bodyFatPct))
        assertEquals("אחרי אימון", m.notes)

        val note = env.measurements.add(MeasurementInput(notes = "התחלתי קריאטין"), ms(2026, 10, 3))
        assertTrue(!env.measurements.get(note)!!.hasAnyValue)
        assertEquals("התחלתי קריאטין", env.measurements.get(note)!!.notes)
    }

    @Test
    fun `a measurement can be edited and its date moved`() = runBlocking {
        val id = env.measurements.add(MeasurementInput(weightKg = 80.0), ms(2026, 10, 1))
        env.measurements.update(id, MeasurementInput(weightKg = 79.5, waistCm = 90.0), ms(2026, 9, 28))
        val m = env.measurements.get(id)!!
        assertEquals(79.5, m.weightKg!!, 0.0); assertEquals(90.0, m.waistCm!!, 0.0)
        assertEquals("2026-09-28", m.localDate)
        assertEquals(1, env.measurements.observeAll().first().size)
    }

    @Test
    fun `a measurement can be deleted and stays gone`() = runBlocking {
        val a = env.measurements.add(MeasurementInput(weightKg = 80.0), ms(2026, 10, 1))
        val b = env.measurements.add(MeasurementInput(weightKg = 81.0), ms(2026, 10, 2))
        env.measurements.delete(a)
        assertEquals(listOf(b), env.measurements.observeAll().first().map { it.id })
    }

    @Test
    fun `list is newest first and latest weight skips entries without weight`() = runBlocking {
        env.measurements.add(MeasurementInput(weightKg = 82.0), ms(2026, 10, 1))
        env.measurements.add(MeasurementInput(weightKg = 81.0), ms(2026, 10, 5))
        env.measurements.add(MeasurementInput(waistCm = 90.0), ms(2026, 10, 6))
        val all = env.measurements.observeAll().first()
        assertEquals(listOf("2026-10-06", "2026-10-05", "2026-10-01"), all.map { it.localDate })
        assertEquals(81.0, env.measurements.observeLatestWeight().first()!!.weightKg!!, 0.0)
    }

    @Test
    fun `late evening measurement keeps the local date of the zone it was taken in`() = runBlocking {
        val id = env.measurements.add(MeasurementInput(weightKg = 80.0), ms(2026, 10, 14, 0).let { it + 5 * 60_000 })
        assertEquals("2026-10-14", env.measurements.get(id)!!.localDate) // 00:05 local, still 21:05 UTC the day before
    }

    @Test
    fun `weight change over a week is computed from stored weigh-ins`() = runBlocking {
        env.measurements.add(MeasurementInput(weightKg = 82.0), ms(2026, 10, 1))
        env.measurements.add(MeasurementInput(weightKg = 81.2), ms(2026, 10, 8))
        val change = BodyTrends.weightChange(env.measurements.observeAll().first(), 7)!!
        assertEquals(-0.8, change.deltaKg, 0.0001)
    }

    @Test
    fun `activity events can be saved and removed`() = runBlocking {
        val id = env.activities.save(
            app.liftos.domain.model.ActivityEvent(
                id = "", type = app.liftos.domain.model.ActivityType.WALK, title = "הליכה", startsAt = ms(2026, 10, 3, 7),
                localDate = "", zoneId = "", durationMin = 40,
            ),
        )
        val saved = env.activities.observeAll().first().single()
        assertEquals(id, saved.id); assertEquals("2026-10-03", saved.localDate); assertEquals(40, saved.durationMin)
        env.activities.delete(id)
        assertTrue(env.activities.observeAll().first().isEmpty())
    }
}
