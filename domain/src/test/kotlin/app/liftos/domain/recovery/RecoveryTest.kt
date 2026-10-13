package app.liftos.domain.recovery

import app.liftos.domain.analytics.Fx
import app.liftos.domain.analytics.SessionFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecoveryTest {
    private val today = LocalDate.parse("2026-10-14")

    private fun s(date: String, energy: Int? = null, fatigue: Int? = null, sleep: Double? = null, id: String = "s$date") =
        Fx.session(id, date, energy = energy).copy(fatigueRating = fatigue, sleepHours = sleep)

    private fun week(vol: Double, vararg dates: String) =
        dates.flatMap { d -> Fx.sets("w$d", d, listOf(vol / 10.0), listOf(10)) }

    @Test
    fun `without enough workouts it says there is not enough data`() {
        val r = RecoveryAnalytics.report(emptyList(), listOf(s("2026-10-10")), today)
        assertFalse(r.enoughData)
        assertEquals("נדרשים אימונים נוספים כדי לזהות מגמה.", r.summaryLine)
        assertTrue(r.signals.isEmpty())
    }

    @Test
    fun `falling energy across six rated workouts is a signal`() {
        val sessions = listOf(
            s("2026-09-20", 5), s("2026-09-23", 4), s("2026-09-26", 5),
            s("2026-10-05", 3), s("2026-10-08", 3), s("2026-10-12", 2),
        )
        val r = RecoveryAnalytics.report(emptyList(), sessions, today)
        assertTrue(r.signals.any { it.kind == SignalKind.ENERGY_FALLING })
        assertEquals(6, r.ratedEnergySessions)
    }

    @Test
    fun `stable energy raises nothing`() {
        val sessions = listOf("2026-10-01", "2026-10-04", "2026-10-07", "2026-10-10", "2026-10-12", "2026-10-13").map { s(it, 4) }
        val r = RecoveryAnalytics.report(emptyList(), sessions, today)
        assertTrue(r.signals.isEmpty())
        assertTrue(r.enoughData)
        assertEquals("לא זוהו אותות חריגים בנתונים שהזנת.", r.summaryLine)
    }

    @Test
    fun `high fatigue and short sleep are reported as signals not diagnoses`() {
        val sessions = listOf(
            s("2026-10-05", fatigue = 4, sleep = 5.0), s("2026-10-08", fatigue = 5, sleep = 5.5),
            s("2026-10-11", fatigue = 4, sleep = 5.0), s("2026-10-13", fatigue = 4, sleep = 5.5),
        )
        val r = RecoveryAnalytics.report(emptyList(), sessions, today)
        assertTrue(r.signals.any { it.kind == SignalKind.FATIGUE_HIGH })
        assertTrue(r.signals.any { it.kind == SignalKind.SHORT_SLEEP })
        assertNotNull(r.deloadHint) // two kinds of signals
        assertTrue(r.deloadHint!!.contains("לא אבחנה"))
        assertTrue(RecoveryAnalytics.DISCLAIMER.contains("לא אבחנה רפואית"))
        assertFalse(r.signals.any { it.text.contains("אימון יתר") })
    }

    @Test
    fun `a single signal does not suggest a deload`() {
        val sessions = listOf(s("2026-10-05", fatigue = 4), s("2026-10-08", fatigue = 5), s("2026-10-11"), s("2026-10-13"))
        val r = RecoveryAnalytics.report(emptyList(), sessions, today)
        assertEquals(1, r.signals.size)
        assertNull(r.deloadHint)
    }

    @Test
    fun `training six days in a row is flagged`() {
        val dates = (8..13).map { "2026-10-${it.toString().padStart(2, '0')}" }
        val r = RecoveryAnalytics.report(emptyList(), dates.map { s(it) }, today)
        assertTrue(r.signals.any { it.kind == SignalKind.NO_REST })
        assertEquals(6, r.workoutsLast7Days)
        assertEquals(1, r.restDaysLast7Days)
    }

    @Test
    fun `a sudden jump in weekly volume is flagged`() {
        // three earlier weeks of 1000 kg-reps each, then 2000 in the last 7 days
        val facts = week(1000.0, "2026-09-18") + week(1000.0, "2026-09-25") + week(1000.0, "2026-10-02") + week(2000.0, "2026-10-10")
        val sessions = listOf("2026-09-18", "2026-09-25", "2026-10-02", "2026-10-10").map { s(it) }
        val r = RecoveryAnalytics.report(facts, sessions, today)
        assertTrue(r.signals.any { it.kind == SignalKind.LOAD_SPIKE })
        assertEquals(4, r.weeklyLoads.size)
        assertEquals(2000.0, r.weeklyLoads.last().volumeKg, 1e-9)
    }

    @Test
    fun `days since the last workout`() {
        assertEquals(4L, RecoveryAnalytics.daysSinceLastWorkout(listOf(s("2026-10-10")), today))
        assertNull(RecoveryAnalytics.daysSinceLastWorkout(emptyList<SessionFact>(), today))
    }
}
