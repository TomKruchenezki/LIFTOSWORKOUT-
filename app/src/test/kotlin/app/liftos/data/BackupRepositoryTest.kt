package app.liftos.data

import app.liftos.data.repo.ImportPreview
import app.liftos.data.repo.MeasurementInput
import app.liftos.domain.backup.BackupCodec
import app.liftos.domain.backup.ImportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var env: TestEnv

    @Before
    fun setUp() = runBlocking {
        env = TestEnv()
        env.seeder.seedIfNeeded()
    }

    @After
    fun tearDown() = env.close()

    /** Real data: a finished workout, a measurement, an event, a custom exercise and changed settings. */
    private suspend fun populate(): String {
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        val id = env.workouts.startFromPlan(plan.workouts[0].workout.id)
        env.workouts.getDetail(id)!!.exercises.flatMap { it.sets }.forEach {
            env.workouts.setWeight(it.id, 60.0); env.workouts.setReps(it.id, 10); env.workouts.toggleSetCompleted(it.id)
        }
        env.workouts.setSessionNotes(id, "אימון לשחזור")
        env.workouts.setRatings(id, 4, 3)
        env.workouts.finishWorkout(id)
        env.measurements.add(MeasurementInput(weightKg = 82.4, waistCm = 91.0))
        env.activities.save(app.liftos.domain.model.ActivityEvent("", app.liftos.domain.model.ActivityType.RUN, "ריצה", startsAt = env.now, localDate = "", zoneId = ""))
        env.exercises.createCustom(env.exercises.get("plank")!!.copy(id = "", nameHe = "תרגיל מותאם", nameEn = "Custom", isBuiltIn = false))
        env.settings.update { it.copy(themeMode = app.liftos.domain.model.ThemeMode.LIGHT, defaultRestCompoundSec = 200) }
        return id
    }

    @Test
    fun `export then restore into a fresh install gives identical data`() = runBlocking {
        val sessionId = populate()
        val json = env.backup.exportJson()
        val original = env.backup.snapshot()

        val fresh = TestEnv(idPrefix = "fresh")
        fresh.seeder.seedIfNeeded()
        val preview = fresh.backup.preview(json) as ImportPreview.Ready
        assertTrue(preview.canImport)
        assertEquals(1, preview.summary.sessions)
        assertEquals(1, preview.summary.measurements)
        assertEquals(1, preview.summary.customExercises)
        fresh.backup.import(preview, ImportMode.REPLACE_ALL)

        val restored = fresh.backup.snapshot()
        assertEquals(original.data.sessions, restored.data.sessions)
        assertEquals(original.data.sessionExercises, restored.data.sessionExercises)
        assertEquals(original.data.sets, restored.data.sets)
        assertEquals(original.data.measurements, restored.data.measurements)
        assertEquals(original.data.events.map { it.title }, restored.data.events.map { it.title })
        assertEquals(original.data.plans, restored.data.plans)
        assertEquals(original.data.plannedExercises, restored.data.plannedExercises)
        assertEquals(original.data.exercises.map { it.id }.toSet(), restored.data.exercises.map { it.id }.toSet())
        assertEquals(original.data.settings, restored.data.settings)
        assertEquals("אימון לשחזור", fresh.workouts.getDetail(sessionId)!!.session.notes)
        fresh.close()
    }

    @Test
    fun `restoring over existing data replaces it and writes a safety copy first`() = runBlocking {
        populate()
        val json = env.backup.exportJson()

        // Change things after the backup was taken.
        env.measurements.add(MeasurementInput(weightKg = 70.0))
        env.settings.update { it.copy(defaultRestCompoundSec = 90) }
        assertEquals(2, env.measurements.observeAll().first().size)

        val preview = env.backup.preview(json) as ImportPreview.Ready
        val result = env.backup.import(preview, ImportMode.REPLACE_ALL)
        assertEquals(1, env.measurements.observeAll().first().size)
        assertEquals(200, env.settings.get().defaultRestCompoundSec)

        // The state from just before the restore was saved automatically.
        assertNotNull(result.safetyCopyName)
        val copy = File(File(env.context.filesDir, "backups"), result.safetyCopyName!!)
        assertTrue(copy.exists())
        val saved = BackupCodec.parse(copy.readText(), emptyList()) as app.liftos.domain.backup.ParseResult.Success
        assertEquals(2, saved.file.data.measurements.size)
    }

    @Test
    fun `merge adds only what is missing and is idempotent`() = runBlocking {
        populate()
        val json = env.backup.exportJson()

        // Same backup merged into the same database: nothing changes.
        val before = env.backup.snapshot().data
        env.backup.import(env.backup.preview(json) as ImportPreview.Ready, ImportMode.MERGE)
        val after = env.backup.snapshot().data
        assertEquals(before.sessions.size, after.sessions.size)
        assertEquals(before.sets.size, after.sets.size)
        assertEquals(before.measurements.size, after.measurements.size)

        // Merge into a different device that has its own data: both are kept.
        val other = TestEnv(idPrefix = "other")
        other.seeder.seedIfNeeded()
        other.measurements.add(MeasurementInput(weightKg = 75.0))
        other.backup.import(other.backup.preview(json) as ImportPreview.Ready, ImportMode.MERGE)
        assertEquals(2, other.measurements.observeAll().first().size)
        assertEquals(1, other.workouts.observeHistory().first().size)
        assertEquals(2, other.plans.observePlans().first().size) // its own default plan + the imported copy
        other.close()
    }

    @Test
    fun `an invalid or foreign file is rejected before anything changes`() = runBlocking {
        populate()
        val before = env.backup.snapshot().data
        assertTrue(env.backup.preview("garbage") is ImportPreview.Failure)
        assertTrue(env.backup.preview("""{"format":"liftos-backup","schemaVersion":42}""") is ImportPreview.Failure)

        // Valid JSON but referentially broken: validation fails, import is refused.
        val broken = env.backup.exportJson().replace("\"sessionExerciseId\": \"", "\"sessionExerciseId\": \"missing-")
        val preview = env.backup.preview(broken) as ImportPreview.Ready
        assertFalse(preview.canImport)
        assertTrue(preview.report.errors.isNotEmpty())
        val failed = runCatching { env.backup.import(preview, ImportMode.REPLACE_ALL) }
        assertTrue(failed.isFailure)
        assertEquals(before, env.backup.snapshot().data)
    }

    @Test
    fun `a failure in the middle of an import rolls everything back`() = runBlocking {
        populate()
        val before = env.backup.snapshot().data
        val preview = env.backup.preview(env.backup.exportJson()) as ImportPreview.Ready
        // Sabotage the data after validation so the database itself rejects it (foreign key violation).
        val sabotaged = preview.copy(
            report = preview.report.copy(
                cleaned = preview.report.cleaned.copy(
                    plannedExercises = preview.report.cleaned.plannedExercises.map { it.copy(exerciseId = "does-not-exist") },
                ),
            ),
        )
        val result = runCatching { env.backup.import(sabotaged, ImportMode.REPLACE_ALL) }
        assertTrue(result.isFailure)
        assertEquals(before, env.backup.snapshot().data) // transaction rolled back - nothing was lost
    }

    @Test
    fun `the earlier app's export can be previewed and merged`() = runBlocking {
        val floot = """
        {"version":1,"name":"x","plans":[],"customExercises":[],
         "sessions":[{"id":"s1","title":"Upper A","start":1760000000000,"end":1760003600000,"notes":"ישן","energy":3,
           "exercises":[{"exerciseId":"barbell-bench-press","sets":[{"id":"a","weight":60,"reps":10,"rir":2,"done":true}]}]}],
         "measurements":[{"id":"m1","date":"2026-10-05","weight":83.1}],"events":[],"active":null}
        """.trimIndent()
        val preview = env.backup.preview(floot) as ImportPreview.Ready
        assertEquals(app.liftos.domain.backup.BackupSource.FLOOT, preview.source)
        assertTrue(preview.canImport)
        env.backup.import(preview, ImportMode.MERGE)
        val history = env.workouts.observeHistory().first().single()
        assertEquals("ישן", history.session.notes)
        assertEquals(600.0, history.volumeKg, 0.0)
        assertEquals(83.1, env.measurements.observeAll().first().single().weightKg!!, 0.0)
        // Importing again changes nothing.
        env.backup.import(env.backup.preview(floot) as ImportPreview.Ready, ImportMode.MERGE)
        assertEquals(1, env.workouts.observeHistory().first().size)
    }

    @Test
    fun `merge skips an in-progress workout from the backup`() = runBlocking {
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        env.workouts.startFromPlan(plan.workouts[0].workout.id)
        val json = env.backup.exportJson()

        val other = TestEnv(idPrefix = "o2")
        other.seeder.seedIfNeeded()
        val result = other.backup.import(other.backup.preview(json) as ImportPreview.Ready, ImportMode.MERGE)
        assertEquals(1, result.skippedActiveSessions)
        assertNull(other.workouts.observeActive().first())
        other.close()
    }

    @Test
    fun `an active workout is preserved by a full backup and restore`() = runBlocking {
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        val id = env.workouts.startFromPlan(plan.workouts[0].workout.id)
        val json = env.backup.exportJson()
        val other = TestEnv(idPrefix = "o3")
        other.seeder.seedIfNeeded()
        other.backup.import(other.backup.preview(json) as ImportPreview.Ready, ImportMode.REPLACE_ALL)
        assertEquals(id, other.workouts.observeActive().first()?.id)
        other.close()
    }

    @Test
    fun `restore fixes an active plan id that no longer exists`() = runBlocking {
        val json = env.backup.exportJson().replace(
            Regex("\"activePlanId\": \"[^\"]*\""), "\"activePlanId\": \"ghost-plan\"",
        )
        env.backup.import(env.backup.preview(json) as ImportPreview.Ready, ImportMode.REPLACE_ALL)
        assertNull(env.settings.get().activePlanId)
    }

    @Test
    fun `csv and text exports can be produced from stored data`() = runBlocking {
        val id = populate()
        val d = env.workouts.getDetail(id)!!
        val csv = app.liftos.domain.export.CsvExporter.sessions(listOf(d))
        assertTrue(csv.lines().first().startsWith("date,start_time"))
        assertEquals(1 + d.exercises.sumOf { it.sets.size }, csv.trimEnd().split("\r\n").size)
        val report = app.liftos.domain.export.TextReport.aiReport(d, null, null)
        assertTrue(report.contains("אימון לשחזור"))
        assertTrue(report.trimEnd().endsWith(app.liftos.domain.export.TextReport.AI_INSTRUCTION))
    }
}
