package app.liftos.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {

    private fun bench(session: String, date: String, w: List<Double>, r: List<Int>) = Fx.sets(session, date, w, r)

    @Test
    fun `the first workout creates a baseline, not a record`() {
        val result = PersonalRecordEngine.compute(bench("s1", "2026-10-01", listOf(80.0), listOf(5)))
        assertTrue(result.events.isEmpty())
        val kinds = result.currentFor("bench").map { it.kind }
        assertTrue(RecordKind.MAX_WEIGHT in kinds)
        assertTrue(RecordKind.BEST_E1RM in kinds)
        assertTrue(RecordKind.REP_MAX_5 in kinds)
    }

    @Test
    fun `a heavier weight is a record and a tie is not`() {
        val facts = bench("s1", "2026-10-01", listOf(80.0), listOf(5)) +
            bench("s2", "2026-10-08", listOf(80.0), listOf(5)) +
            bench("s3", "2026-10-15", listOf(82.5), listOf(5))
        val result = PersonalRecordEngine.compute(facts)
        assertTrue(result.eventsFor("s2").none { it.kind == RecordKind.MAX_WEIGHT })
        val e = result.eventsFor("s3").first { it.kind == RecordKind.MAX_WEIGHT }
        assertEquals(82.5, e.newValue, 0.0)
        assertEquals(80.0, e.previousValue, 0.0)
        assertEquals("s3", result.currentFor("bench").first { it.kind == RecordKind.MAX_WEIGHT }.sessionId)
    }

    @Test
    fun `progressive sets inside one workout produce a single event with the final value`() {
        val facts = bench("s1", "2026-10-01", listOf(60.0), listOf(8)) +
            bench("s2", "2026-10-08", listOf(62.5, 65.0, 67.5), listOf(5, 5, 5))
        val weightEvents = PersonalRecordEngine.compute(facts).events.filter { it.kind == RecordKind.MAX_WEIGHT }
        assertEquals(1, weightEvents.size)
        assertEquals(67.5, weightEvents[0].newValue, 0.0)
    }

    @Test
    fun `more reps at the same weight beat the estimated 1RM and reps at that weight`() {
        val facts = bench("s1", "2026-10-01", listOf(60.0), listOf(8)) + bench("s2", "2026-10-08", listOf(60.0), listOf(10))
        val kinds = PersonalRecordEngine.compute(facts).eventsFor("s2").map { it.kind }
        assertTrue(RecordKind.BEST_E1RM in kinds)
        assertTrue(RecordKind.REPS_AT_WEIGHT in kinds)
        assertTrue(RecordKind.MAX_WEIGHT !in kinds)
        val reps = PersonalRecordEngine.compute(facts).eventsFor("s2").first { it.kind == RecordKind.REPS_AT_WEIGHT }
        assertEquals(60.0, reps.weightKg!!, 0.0)
        assertEquals(8.0, reps.previousValue, 0.0)
    }

    @Test
    fun `a new weight does not create a reps-at-weight record the first time`() {
        val facts = bench("s1", "2026-10-01", listOf(60.0), listOf(8)) + bench("s2", "2026-10-08", listOf(62.5), listOf(6))
        assertTrue(PersonalRecordEngine.compute(facts).eventsFor("s2").none { it.kind == RecordKind.REPS_AT_WEIGHT })
    }

    @Test
    fun `rep max records use the heaviest weight for at least n reps`() {
        val facts = bench("s1", "2026-10-01", listOf(70.0), listOf(5)) + bench("s2", "2026-10-08", listOf(72.5), listOf(4), ) +
            bench("s3", "2026-10-15", listOf(72.5), listOf(5))
        val result = PersonalRecordEngine.compute(facts)
        assertTrue(result.eventsFor("s2").none { it.kind == RecordKind.REP_MAX_5 }) // only 4 reps
        assertEquals(72.5, result.eventsFor("s3").first { it.kind == RecordKind.REP_MAX_5 }.newValue, 0.0)
    }

    @Test
    fun `best exercise volume is tracked per workout`() {
        val facts = bench("s1", "2026-10-01", listOf(60.0, 60.0), listOf(10, 10)) + bench("s2", "2026-10-08", listOf(60.0, 60.0, 60.0), listOf(10, 10, 10))
        val e = PersonalRecordEngine.compute(facts).eventsFor("s2").first { it.kind == RecordKind.BEST_VOLUME }
        assertEquals(1800.0, e.newValue, 0.0)
        assertEquals(1200.0, e.previousValue, 0.0)
    }

    @Test
    fun `unloaded exercises track max reps and durations track seconds`() {
        val pull1 = Fx.fact("s1", "2026-10-01", null, 6, exercise = "pullup", name = "מתח")
        val pull2 = Fx.fact("s2", "2026-10-08", null, 8, exercise = "pullup", name = "מתח")
        val plank1 = Fx.fact("s1", "2026-10-01", null, null, exercise = "plank", name = "פלאנק", duration = 40)
        val plank2 = Fx.fact("s2", "2026-10-08", null, null, exercise = "plank", name = "פלאנק", duration = 55)
        val result = PersonalRecordEngine.compute(listOf(pull1, pull2, plank1, plank2))
        assertEquals(8.0, result.events.first { it.kind == RecordKind.MAX_REPS }.newValue, 0.0)
        assertEquals(55.0, result.events.first { it.kind == RecordKind.MAX_DURATION }.newValue, 0.0)
    }

    @Test
    fun `warm-ups cannot set records`() {
        val facts = bench("s1", "2026-10-01", listOf(60.0), listOf(8)) +
            listOf(Fx.fact("s2", "2026-10-08", 100.0, 3, type = app.liftos.domain.model.SetType.WARMUP))
        assertTrue(PersonalRecordEngine.compute(facts).events.isEmpty())
    }

    @Test
    fun `live detection compares the active workout against earlier history only`() {
        val history = bench("old", "2026-10-01", listOf(80.0), listOf(5))
        val active = bench("now", "2026-10-08", listOf(85.0), listOf(3))
        val events = PersonalRecordEngine.eventsForSession(history, active, "now")
        assertTrue(events.any { it.kind == RecordKind.MAX_WEIGHT && it.newValue == 85.0 })
        // The same session id already present in history must not be compared against itself.
        val again = PersonalRecordEngine.eventsForSession(history + active, active, "now")
        assertEquals(events.map { it.kind }, again.map { it.kind })
    }

    @Test
    fun `headline keeps the most important kinds per exercise`() {
        val facts = bench("s1", "2026-10-01", listOf(60.0, 60.0), listOf(8, 8)) + bench("s2", "2026-10-08", listOf(70.0, 70.0, 70.0), listOf(10, 10, 10))
        val all = PersonalRecordEngine.compute(facts).eventsFor("s2")
        assertTrue(all.size > 3)
        val head = PersonalRecordEngine.headline(all, 3)
        assertEquals(3, head.size)
        assertEquals(RecordKind.MAX_WEIGHT, head[0].kind)
    }

    @Test
    fun `editing history recomputes consistently`() {
        val before = bench("s1", "2026-10-01", listOf(80.0), listOf(5)) + bench("s2", "2026-10-08", listOf(82.5), listOf(5))
        assertEquals(1, PersonalRecordEngine.compute(before).events.count { it.kind == RecordKind.MAX_WEIGHT })
        val edited = bench("s1", "2026-10-01", listOf(85.0), listOf(5)) + bench("s2", "2026-10-08", listOf(82.5), listOf(5))
        assertEquals(0, PersonalRecordEngine.compute(edited).events.count { it.kind == RecordKind.MAX_WEIGHT })
        assertNull(PersonalRecordEngine.compute(edited).events.firstOrNull { it.kind == RecordKind.MAX_WEIGHT })
    }
}
