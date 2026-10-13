package app.liftos.domain.backup

import app.liftos.domain.SeqIds
import app.liftos.domain.TestData
import app.liftos.domain.library.DefaultPlan
import app.liftos.domain.logic.SessionFactory
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BackupTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val library = TestData.library.exercises

    /** A realistic dataset: default plan + two completed workouts + measurement + event + settings. */
    private fun sampleData(): BackupData {
        val plan = DefaultPlan.create(1_000L, SeqIds("plan"))
        val byId = library.associateBy { it.id }
        val upperA = plan.workouts[0]
        val sessions = (1..2).map { n ->
            val d = SessionFactory.fromPlan(
                plan.plan, upperA.workout, upperA.exercises, byId, emptyMap(),
                1_760_000_000_000L + n * 86_400_000L, zone, SeqIds("sess$n"),
            )
            d.copy(
                session = d.session.copy(status = app.liftos.domain.model.SessionStatus.COMPLETED, endedAt = d.session.startedAt + 3_000_000L),
                exercises = d.exercises.map { ex ->
                    ex.copy(sets = ex.sets.map { it.copy(weightKg = 50.0, reps = 8, isCompleted = true) })
                },
            )
        }
        return BackupData(
            exercises = library + app.liftos.domain.model.Exercise(
                id = "custom-1", nameHe = "תרגיל אישי", nameEn = "My Move",
                primaryMuscle = app.liftos.domain.model.MuscleGroup.CHEST,
            ),
            plans = listOf(plan.plan),
            plannedWorkouts = plan.workouts.map { it.workout },
            plannedExercises = plan.workouts.flatMap { it.exercises },
            sessions = sessions.map { it.session },
            sessionExercises = sessions.flatMap { s -> s.exercises.map { it.exercise } },
            sets = sessions.flatMap { s -> s.exercises.flatMap { it.sets } },
            measurements = listOf(
                BodyMeasurement("m1", 1_760_000_000_000L, "2026-10-09", "Asia/Jerusalem", weightKg = 82.4, waistCm = 91.0),
            ),
            events = listOf(ActivityEvent("e1", ActivityType.WALK, "הליכה", startsAt = 1_760_000_000_000L, localDate = "2026-10-09", zoneId = "Asia/Jerusalem", durationMin = 40)),
            settings = AppSettings(themeMode = ThemeMode.LIGHT, defaultRestCompoundSec = 200),
        )
    }

    private fun file(data: BackupData = sampleData()) = BackupFile(exportedAt = 5L, appVersion = "0.1.0", data = data)

    // ---- codec -----------------------------------------------------------------------------

    @Test
    fun `export then import restores identical data`() {
        val original = file()
        val text = BackupCodec.encode(original)
        val parsed = BackupCodec.parse(text, library, zone, 0L) as ParseResult.Success
        assertEquals(BackupSource.LIFTOS, parsed.source)
        assertEquals(original, parsed.file)
    }

    @Test
    fun `backup declares an explicit schema version and format`() {
        val text = BackupCodec.encode(file())
        assertTrue(text.contains("\"format\": \"liftos-backup\""))
        assertTrue(text.contains("\"schemaVersion\": ${BackupFile.CURRENT_SCHEMA}"))
    }

    @Test
    fun `unknown fields and a utf-8 BOM are tolerated`() {
        val text = "﻿" + BackupCodec.encode(file()).replaceFirst("{", "{ \"futureField\": [1,2,3],")
        assertTrue(BackupCodec.parse(text, library, zone, 0L) is ParseResult.Success)
    }

    @Test
    fun `invalid input is rejected with a clear message`() {
        assertTrue(BackupCodec.parse("not json", library, zone, 0L) is ParseResult.Failure)
        assertTrue(BackupCodec.parse("[1,2]", library, zone, 0L) is ParseResult.Failure)
        assertTrue(BackupCodec.parse("{\"hello\":1}", library, zone, 0L) is ParseResult.Failure)
        val other = BackupCodec.parse("{\"format\":\"something-else\"}", library, zone, 0L) as ParseResult.Failure
        assertTrue(other.message.contains("something-else"))
    }

    @Test
    fun `a backup from a newer schema is refused instead of misread`() {
        val text = BackupCodec.encode(file()).replace("\"schemaVersion\": ${BackupFile.CURRENT_SCHEMA}", "\"schemaVersion\": 99")
        val r = BackupCodec.parse(text, library, zone, 0L) as ParseResult.Failure
        assertTrue(r.message.contains("99"))
    }

    @Test
    fun `a corrupt native backup fails cleanly`() {
        val broken = "{\"format\":\"liftos-backup\",\"schemaVersion\":1,\"data\":{\"plans\":[{\"nope\":1}]}}"
        assertTrue(BackupCodec.parse(broken, library, zone, 0L) is ParseResult.Failure)
    }

    // ---- validation ------------------------------------------------------------------------

    @Test
    fun `a good backup validates and summarises its content`() {
        val r = BackupValidator.validate(file())
        assertTrue(r.errors.joinToString(), r.isValid)
        assertEquals(1, r.summary.customExercises)
        assertEquals(1, r.summary.plans)
        assertEquals(4, r.summary.plannedWorkouts)
        assertEquals(2, r.summary.sessions)
        assertEquals(2, r.summary.completedSessions)
        assertEquals(1, r.summary.measurements)
        assertEquals(1, r.summary.events)
        assertTrue(r.summary.hasSettings)
        assertEquals(r.summary.firstSessionDate!! <= r.summary.lastSessionDate!!, true)
    }

    @Test
    fun `duplicate ids are errors`() {
        val d = sampleData()
        val bad = d.copy(sessions = d.sessions + d.sessions.first())
        assertFalse(BackupValidator.validate(file(bad)).isValid)
    }

    @Test
    fun `dangling references are errors`() {
        val d = sampleData()
        val noSessions = d.copy(sessions = emptyList())
        assertTrue(BackupValidator.validate(file(noSessions)).errors.any { it.contains("אימון שאינו קיים") })

        val orphanSet = d.copy(sets = d.sets + d.sets.first().copy(id = "x", sessionExerciseId = "missing"))
        assertTrue(BackupValidator.validate(file(orphanSet)).errors.any { it.contains("סט שאינו משויך") })

        val noPlans = d.copy(plans = emptyList())
        assertTrue(BackupValidator.validate(file(noPlans)).errors.any { it.contains("תוכנית שאינה קיימת") })
    }

    @Test
    fun `implausible values are errors`() {
        val d = sampleData()
        val heavy = d.copy(sets = d.sets.map { it.copy(weightKg = 99999.0) })
        assertFalse(BackupValidator.validate(file(heavy)).isValid)
        val negativeRir = d.copy(sets = d.sets.map { it.copy(rir = -3) })
        assertFalse(BackupValidator.validate(file(negativeRir)).isValid)
        val endsBeforeStart = d.copy(sessions = d.sessions.map { it.copy(endedAt = it.startedAt - 1) })
        assertFalse(BackupValidator.validate(file(endsBeforeStart)).isValid)
    }

    @Test
    fun `history for an exercise unknown to the library is kept with a warning`() {
        val d = sampleData()
        val ghost = d.copy(sessionExercises = d.sessionExercises.map { it.copy(exerciseId = "gone-forever") })
        val r = BackupValidator.validate(file(ghost))
        assertTrue(r.isValid)
        assertTrue(r.warnings.isNotEmpty())
        assertTrue(r.cleaned.sessionExercises.all { it.exerciseId == null })
    }

    @Test
    fun `a wrong format tag or unsupported schema is an error`() {
        assertFalse(BackupValidator.validate(file().copy(format = "x")).isValid)
        assertFalse(BackupValidator.validate(file().copy(schemaVersion = 0)).isValid)
    }

    // ---- merge -----------------------------------------------------------------------------

    private fun idsOf(d: BackupData) = ExistingIds(
        exercises = d.exercises.map { it.id }.toSet(), plans = d.plans.map { it.id }.toSet(),
        plannedWorkouts = d.plannedWorkouts.map { it.id }.toSet(), plannedExercises = d.plannedExercises.map { it.id }.toSet(),
        sessions = d.sessions.map { it.id }.toSet(), sessionExercises = d.sessionExercises.map { it.id }.toSet(),
        sets = d.sets.map { it.id }.toSet(), measurements = d.measurements.map { it.id }.toSet(), events = d.events.map { it.id }.toSet(),
    )

    @Test
    fun `merging into an empty database adds everything except settings`() {
        val d = sampleData()
        val delta = BackupMerge.delta(d, ExistingIds())
        assertEquals(d.sessions.size, delta.sessions.size)
        assertEquals(d.sets.size, delta.sets.size)
        assertEquals(d.plannedExercises.size, delta.plannedExercises.size)
        assertNull(delta.settings)
    }

    @Test
    fun `importing the same backup twice is a no-op`() {
        val d = sampleData()
        val delta = BackupMerge.delta(d, idsOf(d))
        assertTrue(delta.sessions.isEmpty() && delta.sets.isEmpty() && delta.plans.isEmpty() &&
            delta.measurements.isEmpty() && delta.events.isEmpty() && delta.exercises.isEmpty())
    }

    @Test
    fun `an existing workout is skipped together with all its children`() {
        val d = sampleData()
        val keep = d.sessions.first().id
        val keptExercises = d.sessionExercises.filter { it.sessionId == keep }
        val existing = ExistingIds(
            sessions = setOf(keep),
            sessionExercises = keptExercises.map { it.id }.toSet(),
            sets = d.sets.filter { s -> keptExercises.any { it.id == s.sessionExerciseId } }.map { it.id }.toSet(),
        )
        val delta = BackupMerge.delta(d, existing)
        assertEquals(listOf(d.sessions[1].id), delta.sessions.map { it.id })
        assertTrue(delta.sessionExercises.all { it.sessionId != keep })
        val seIds = delta.sessionExercises.map { it.id }.toSet()
        assertTrue(delta.sets.all { it.sessionExerciseId in seIds })
        assertNotNull(delta.sets.firstOrNull())
    }

    @Test
    fun `new measurements are added even when workouts already exist`() {
        val d = sampleData()
        val incoming = d.copy(measurements = d.measurements + d.measurements.first().copy(id = "m2", localDate = "2026-10-10"))
        val delta = BackupMerge.delta(incoming, idsOf(d))
        assertEquals(listOf("m2"), delta.measurements.map { it.id })
    }
}
