package app.liftos.domain.backup

import app.liftos.domain.TestData
import app.liftos.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class FlootImporterTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val library = TestData.library.exercises

    // Built from the structure described for the earlier prototype:
    // version, plans, customExercises, sessions[id, planId, title, start, end, notes, energy, exercises[{exerciseId, sets[{id, weight, reps, rir, done}]}]],
    // measurements, events, active, name.
    private val sample = """
    {
      "version": 3,
      "name": "Tom",
      "plans": [
        {"id": "plan-1", "name": "Upper Lower", "days": [
          {"id": "d1", "name": "Upper A", "weekday": 0, "exercises": [
            {"exerciseId": "barbell-bench-press", "sets": 3, "repsMin": 5, "repsMax": 8, "rest": 180}
          ]}
        ]}
      ],
      "customExercises": [{"id": "ce1", "name": "תרגיל שלי", "muscle": "CHEST"}],
      "sessions": [
        {"id": "s1", "planId": "plan-1", "title": "Upper A", "start": 1760000000000, "end": 1760003600000,
         "notes": "הרגשה טובה", "energy": 4,
         "exercises": [
           {"exerciseId": "barbell-bench-press", "sets": [
             {"id": "a", "weight": 60, "reps": 10, "rir": 2, "done": true},
             {"id": "b", "weight": "62.5", "reps": "8", "rir": null, "done": true},
             {"id": "c", "weight": 62.5, "reps": 6, "rir": 1, "done": false}
           ]},
           {"exerciseId": "ce1", "sets": [{"id": "d", "weight": 20, "reps": 12, "done": true}]},
           {"exerciseId": "mystery_machine", "sets": [{"id": "e", "weight": 10, "reps": 15, "done": true}]}
         ]},
        {"id": "s2", "title": "ISO date", "start": "2026-10-04T16:30:00Z", "exercises": []},
        {"id": "bad", "title": "no start", "exercises": []}
      ],
      "measurements": [
        {"id": "m1", "date": "2026-10-05", "weight": 82.3, "waist": "91,5"},
        {"id": "m2", "date": "2026-10-06"}
      ],
      "events": [{"id": "ev1", "type": "walk", "title": "הליכה", "date": "2026-10-07T07:00:00"}],
      "active": {"id": "x"}
    }
    """.trimIndent()

    private fun import(text: String = sample): ParseResult.Success =
        BackupCodec.parse(text, library, zone, 1_000L) as ParseResult.Success

    @Test
    fun `is detected as the earlier app format`() {
        val r = import()
        assertEquals(BackupSource.FLOOT, r.source)
        assertEquals(BackupFile.FORMAT, r.file.format)
    }

    @Test
    fun `sessions keep dates, notes, energy and the link to exercises`() {
        val d = import().file.data
        assertEquals(2, d.sessions.size) // the session without a start time is skipped
        val s = d.sessions.first { it.id == "floot:s1" }
        assertEquals("Upper A", s.title)
        assertEquals(1_760_000_000_000L, s.startedAt)
        assertEquals(1_760_003_600_000L, s.endedAt)
        assertEquals(4, s.energyRating)
        assertEquals("הרגשה טובה", s.notes)
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals("floot:plan-1", s.planId)
        assertEquals(3, d.sessionExercises.count { it.sessionId == "floot:s1" })
    }

    @Test
    fun `sets keep weight reps rir and done even when stored as strings`() {
        val d = import().file.data
        val bench = d.sessionExercises.first { it.exerciseId == "barbell-bench-press" }
        val sets = d.sets.filter { it.sessionExerciseId == bench.id }.sortedBy { it.orderIndex }
        assertEquals(listOf(60.0, 62.5, 62.5), sets.map { it.weightKg })
        assertEquals(listOf(10, 8, 6), sets.map { it.reps })
        assertEquals(listOf(2, null, 1), sets.map { it.rir })
        assertEquals(listOf(true, true, false), sets.map { it.isCompleted })
        assertEquals("לחיצת חזה במוט", bench.nameHe)
    }

    @Test
    fun `known library ids map to library exercises, unknown ones become custom exercises with a warning`() {
        val r = import()
        val d = r.file.data
        assertEquals("barbell-bench-press", d.sessionExercises.first { it.nameEn == "Barbell Bench Press" }.exerciseId)
        assertTrue(d.exercises.any { it.id == "floot:ce1" && it.nameHe == "תרגיל שלי" })
        assertTrue(d.exercises.any { it.id == "floot:mystery_machine" })
        assertTrue(r.warnings.any { it.contains("mystery_machine") })
        assertFalse(d.exercises.any { it.isBuiltIn })
    }

    @Test
    fun `plans become plan, workout and planned exercises`() {
        val d = import().file.data
        assertEquals("Upper Lower", d.plans.single().name)
        val w = d.plannedWorkouts.single()
        assertEquals("Upper A", w.name)
        assertEquals(7, w.weekday) // foreign weekday 0 = Sunday -> ISO 7
        val pe = d.plannedExercises.single()
        assertEquals(3, pe.targetSets); assertEquals(5, pe.repsMin); assertEquals(8, pe.repsMax); assertEquals(180, pe.restSeconds)
    }

    @Test
    fun `measurements and events are converted, empty measurements are dropped`() {
        val d = import().file.data
        val m = d.measurements.single()
        assertEquals(82.3, m.weightKg!!, 0.0)
        assertEquals(91.5, m.waistCm!!, 0.0)
        assertEquals("2026-10-05", m.localDate)
        val e = d.events.single()
        assertEquals("הליכה", e.title)
        assertEquals("2026-10-07", e.localDate)
    }

    @Test
    fun `iso timestamps are understood`() {
        val s = import().file.data.sessions.first { it.id == "floot:s2" }
        assertEquals(java.time.Instant.parse("2026-10-04T16:30:00Z").toEpochMilli(), s.startedAt)
        assertEquals("2026-10-04", s.localDate)
    }

    @Test
    fun `an active workout is not imported but the user is told`() {
        assertTrue(import().warnings.any { it.contains("אימון פעיל") })
        assertTrue(import().file.data.sessions.none { it.status == SessionStatus.ACTIVE })
    }

    @Test
    fun `converted data passes validation`() {
        val r = import()
        val v = BackupValidator.validate(r.file, library.map { it.id }.toSet())
        assertTrue(v.errors.joinToString("\n"), v.isValid)
    }

    @Test
    fun `importing twice via merge adds nothing the second time`() {
        val d = import().file.data
        val existing = ExistingIds(
            exercises = d.exercises.map { it.id }.toSet(), plans = d.plans.map { it.id }.toSet(),
            plannedWorkouts = d.plannedWorkouts.map { it.id }.toSet(), plannedExercises = d.plannedExercises.map { it.id }.toSet(),
            sessions = d.sessions.map { it.id }.toSet(), sessionExercises = d.sessionExercises.map { it.id }.toSet(),
            sets = d.sets.map { it.id }.toSet(), measurements = d.measurements.map { it.id }.toSet(), events = d.events.map { it.id }.toSet(),
        )
        val again = BackupMerge.delta(import().file.data, existing)
        assertTrue(again.sessions.isEmpty() && again.sets.isEmpty() && again.measurements.isEmpty() && again.exercises.isEmpty())
    }

    @Test
    fun `a plan with an unrecognised layout is imported empty with a warning`() {
        val text = """{"plans":[{"id":"p","name":"Weird","mystery":[1]}],"sessions":[]}"""
        val r = import(text)
        assertEquals(1, r.file.data.plans.size)
        assertTrue(r.file.data.plannedWorkouts.isEmpty())
        assertTrue(r.warnings.any { it.contains("Weird") })
    }

    @Test
    fun `time parsing handles seconds, millis, dates and junk`() {
        assertEquals(1_760_000_000_000L, FlootImporter.parseTime("1760000000", zone))
        assertEquals(1_760_000_000_000L, FlootImporter.parseTime("1760000000000", zone))
        assertEquals(java.time.LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli(), FlootImporter.parseTime("2026-10-05", zone))
        assertNull(FlootImporter.parseTime("yesterday", zone))
        assertNull(FlootImporter.parseTime("-5", zone))
    }
}
