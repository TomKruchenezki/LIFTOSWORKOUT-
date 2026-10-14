package app.liftos.data

import app.liftos.domain.backup.ImportMode
import app.liftos.data.repo.ImportPreview
import app.liftos.data.repo.MeasurementInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Acceptance scenario E ("backup and restore") with the owner's ten steps. The source database and the restore target
 * are two separate in-memory databases (TestEnv): nothing real is touched.
 */
@RunWith(RobolectricTestRunner::class)
class ScenarioETest {
    private lateinit var source: TestEnv
    private lateinit var target: TestEnv

    @Before
    fun setUp() = runBlocking {
        source = TestEnv(idPrefix = "src")
        source.seeder.seedIfNeeded()
        target = TestEnv(idPrefix = "dst")
        target.seeder.seedIfNeeded()
    }

    @After
    fun tearDown() {
        source.close(); target.close()
    }

    @Test
    fun `a new plan, a workout and body metrics survive backup and restore into an isolated database`() = runBlocking {
        // 1 a new plan
        val planId = source.plans.createPlan("Push Pull", "בדיקה", "כוח", 3)
        val workoutId = source.plans.addWorkout(planId, "Push", weekday = 1)
        source.plans.addExercise(workoutId, source.exercises.get("barbell-bench-press")!!, defaultRestSec = 150)
        source.plans.addExercise(workoutId, source.exercises.get("triceps-pushdown")!!, defaultRestSec = 90)

        // 2 a workout from that plan
        val sessionId = source.workouts.startFromPlan(workoutId)
        source.workouts.getDetail(sessionId)!!.exercises.flatMap { it.sets }.forEachIndexed { i, s ->
            source.workouts.setWeight(s.id, 60.0 + i); source.workouts.setReps(s.id, 8); source.workouts.setRir(s.id, 2)
            source.workouts.toggleSetCompleted(s.id)
        }
        source.workouts.setSessionNotes(sessionId, "הערה לגיבוי")
        source.workouts.finishWorkout(sessionId)

        // 3 body metrics
        source.measurements.add(MeasurementInput(weightKg = 82.4, waistCm = 90.5, chestCm = 103.0, notes = "בבוקר"))

        // 4 create the backup
        val json = source.backup.exportJson()
        assertTrue("the file is not empty", json.length > 1000)
        val original = source.backup.snapshot()

        // 5 verify the file before importing it
        val preview = target.backup.preview(json) as ImportPreview.Ready
        assertTrue(preview.canImport)
        assertEquals(1, preview.summary.sessions)
        assertEquals(1, preview.summary.measurements)
        assertEquals(2, preview.summary.plans)

        // 6 restore in the isolated database
        target.backup.import(preview, ImportMode.REPLACE_ALL)
        val restored = target.backup.snapshot()

        // 7 everything came back
        assertEquals(original.data.plans, restored.data.plans)
        assertEquals(original.data.plannedWorkouts, restored.data.plannedWorkouts)
        assertEquals(original.data.plannedExercises, restored.data.plannedExercises)
        assertEquals(original.data.sessions, restored.data.sessions)
        assertEquals(original.data.sessionExercises, restored.data.sessionExercises)
        assertEquals(original.data.sets, restored.data.sets)
        assertEquals(original.data.measurements, restored.data.measurements)
        assertEquals("הערה לגיבוי", target.workouts.getDetail(sessionId)!!.session.notes)

        // 8 the links between exercises, sets and workouts are intact
        val sessionIds = restored.data.sessions.map { it.id }.toSet()
        val exerciseIds = restored.data.sessionExercises.map { it.id }.toSet()
        assertTrue(restored.data.sessionExercises.all { it.sessionId in sessionIds })
        assertTrue(restored.data.sets.all { it.sessionExerciseId in exerciseIds })
        assertTrue(restored.data.plannedExercises.all { pe -> restored.data.plannedWorkouts.any { it.id == pe.plannedWorkoutId } })
        val detail = target.workouts.getDetail(sessionId)!!
        assertEquals(2, detail.exercises.size)
        assertEquals(60.0, detail.exercises[0].sets[0].weightKg!!, 0.0)

        // 9 no duplicates: merging the same file again adds nothing
        val again = target.backup.preview(json) as ImportPreview.Ready
        assertEquals(0, again.mergeNew.sessions)
        assertEquals(0, again.mergeNew.measurements)
        target.backup.import(again, ImportMode.MERGE)
        val afterMerge = target.backup.snapshot()
        assertEquals(restored.data.sessions.size, afterMerge.data.sessions.size)
        assertEquals(restored.data.sets.size, afterMerge.data.sets.size)
        assertEquals(restored.data.measurements.size, afterMerge.data.measurements.size)
        assertEquals(restored.data.plans.size, afterMerge.data.plans.size)

        // 10 no data was lost: the source is exactly as it was (the backup never changes it)
        assertEquals(original.data.sessions, source.backup.snapshot().data.sessions)
        assertEquals(1, source.measurements.observeAll().first().size)
    }
}
