package app.liftos.data

import app.liftos.domain.model.PlannedExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeederAndPlanTest {
    private lateinit var env: TestEnv

    @Before
    fun setUp() {
        env = TestEnv()
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun `first launch seeds settings, the exercise library and the default plan`() = runBlocking {
        env.seeder.seedIfNeeded()
        assertTrue(env.exercises.getAll().size >= 100)
        assertTrue(env.exercises.getAll().all { it.isBuiltIn })

        val plans = env.plans.observePlans().first()
        assertEquals(1, plans.size)
        val detail = env.plans.getPlanDetail(plans[0].id)!!
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), detail.workouts.map { it.workout.name })
        assertEquals(7, detail.workouts[0].exercises.size)
        assertEquals(plans[0].id, env.settings.get().activePlanId)
    }

    @Test
    fun `seeding twice changes nothing`() = runBlocking {
        env.seeder.seedIfNeeded()
        val exercises = env.exercises.getAll().size
        env.seeder.seedIfNeeded()
        assertEquals(exercises, env.exercises.getAll().size)
        assertEquals(1, env.plans.observePlans().first().size)
    }

    @Test
    fun `a deleted default plan is never recreated`() = runBlocking {
        env.seeder.seedIfNeeded()
        val id = env.plans.observePlans().first().single().id
        env.plans.deletePlan(id)
        assertNull(env.settings.get().activePlanId)
        env.seeder.seedIfNeeded()
        assertTrue(env.plans.observePlans().first().isEmpty())
    }

    @Test
    fun `reseeding the library never touches custom exercises`() = runBlocking {
        env.seeder.seedIfNeeded()
        val customId = env.exercises.createCustom(
            app.liftos.domain.model.Exercise(
                id = "", nameHe = "תרגיל שלי", nameEn = "Mine", primaryMuscle = app.liftos.domain.model.MuscleGroup.CHEST,
            ),
        )
        env.prefs.setLibraryHash(null)
        env.seeder.seedIfNeeded()
        val custom = env.exercises.get(customId)!!
        assertEquals("תרגיל שלי", custom.nameHe)
        assertTrue(!custom.isBuiltIn)
    }

    // ---- plan CRUD ----------------------------------------------------------------------------------

    @Test
    fun `create a plan, add a workout and exercises, then edit them`() = runBlocking {
        env.seeder.seedIfNeeded()
        val planId = env.plans.createPlan("Full Body", "תיאור", "כוח", 3)
        val workoutId = env.plans.addWorkout(planId, "Day 1", weekday = 7)
        val bench = env.exercises.get("barbell-bench-press")!!
        val row = env.plans.addExercise(workoutId, bench, defaultRestSec = 150)

        var detail = env.plans.getPlanDetail(planId)!!
        assertEquals("Full Body", detail.plan.name)
        assertEquals(3, detail.plan.workoutsPerWeek)
        assertEquals(7, detail.workouts.single().workout.weekday)
        val pe = detail.workouts.single().exercises.single()
        assertEquals(3, pe.targetSets); assertEquals(150, pe.restSeconds)

        env.plans.updatePlannedExercise(pe.copy(targetSets = 5, repsMin = 3, repsMax = 5, targetWeightKg = 100.0, targetRir = 2, notes = "עצירה בחזה", restSeconds = 200))
        detail = env.plans.getPlanDetail(planId)!!
        val edited = detail.workouts.single().exercises.single()
        assertEquals(row, edited.id)
        assertEquals(5, edited.targetSets); assertEquals(3, edited.repsMin); assertEquals(5, edited.repsMax)
        assertEquals(100.0, edited.targetWeightKg!!, 0.0); assertEquals(2, edited.targetRir); assertEquals("עצירה בחזה", edited.notes)
    }

    @Test
    fun `invalid planned values are clamped to sane limits`() = runBlocking {
        env.seeder.seedIfNeeded()
        val planId = env.plans.createPlan("x")
        val w = env.plans.addWorkout(planId, "d")
        val id = env.plans.addExercise(w, env.exercises.get("plank")!!, 60)
        val pe = env.plans.getPlanDetail(planId)!!.workouts.single().exercises.single()
        env.plans.updatePlannedExercise(pe.copy(targetSets = 0, repsMin = 10, repsMax = 4, restSeconds = -5))
        val fixed = env.plans.getPlanDetail(planId)!!.workouts.single().exercises.single()
        assertEquals(id, fixed.id)
        assertEquals(1, fixed.targetSets); assertTrue(fixed.repsMax >= fixed.repsMin); assertEquals(0, fixed.restSeconds)
    }

    @Test
    fun `exercises can be reordered, duplicated, swapped and removed`() = runBlocking {
        env.seeder.seedIfNeeded()
        val planId = env.plans.createPlan("p")
        val w = env.plans.addWorkout(planId, "d")
        val a = env.plans.addExercise(w, env.exercises.get("barbell-bench-press")!!, 90)
        val b = env.plans.addExercise(w, env.exercises.get("lat-pulldown")!!, 90)
        val c = env.plans.addExercise(w, env.exercises.get("ez-bar-curl")!!, 90)

        env.plans.reorderPlannedExercises(w, listOf(c, a, b))
        assertEquals(listOf(c, a, b), env.plans.getPlanDetail(planId)!!.workouts.single().exercises.map { it.id })

        val copyId = env.plans.duplicatePlannedExercise(a)!!
        assertEquals(listOf(c, a, copyId, b), env.plans.getPlanDetail(planId)!!.workouts.single().exercises.map { it.id })

        env.plans.swapExercise(a, env.exercises.get("dumbbell-bench-press")!!)
        assertEquals("dumbbell-bench-press", env.plans.getPlanDetail(planId)!!.workouts.single().exercises.first { it.id == a }.exerciseId)

        env.plans.deletePlannedExercise(copyId)
        val after = env.plans.getPlanDetail(planId)!!.workouts.single().exercises
        assertEquals(listOf(c, a, b), after.map { it.id })
        assertEquals(listOf(0, 1, 2), after.map { it.orderIndex })
    }

    @Test
    fun `workouts can be reordered and rescheduled`() = runBlocking {
        env.seeder.seedIfNeeded()
        val planId = env.plans.createPlan("p")
        val a = env.plans.addWorkout(planId, "A", 7)
        val b = env.plans.addWorkout(planId, "B", 1)
        env.plans.reorderWorkouts(planId, listOf(b, a))
        assertEquals(listOf("B", "A"), env.plans.getPlanDetail(planId)!!.workouts.map { it.workout.name })
        val w = env.plans.getPlanDetail(planId)!!.workouts.first { it.workout.id == a }.workout
        env.plans.updateWorkout(w.copy(weekday = 3))
        assertEquals(3, env.plans.getPlanDetail(planId)!!.workouts.first { it.workout.id == a }.workout.weekday)
    }

    @Test
    fun `duplicate creates an independent deep copy`() = runBlocking {
        env.seeder.seedIfNeeded()
        val src = env.plans.observePlans().first().single()
        val copyId = env.plans.duplicatePlan(src.id)!!
        val a = env.plans.getPlanDetail(src.id)!!
        val b = env.plans.getPlanDetail(copyId)!!
        assertEquals(a.workouts.size, b.workouts.size)
        assertEquals(a.workouts.flatMap { it.exercises }.size, b.workouts.flatMap { it.exercises }.size)
        val ids = (a.workouts.flatMap { it.exercises.map { e -> e.id } } + b.workouts.flatMap { it.exercises.map { e -> e.id } })
        assertEquals(ids.size, ids.toSet().size)
        // Editing the copy leaves the original alone.
        val firstCopyExercise: PlannedExercise = b.workouts[0].exercises[0]
        env.plans.updatePlannedExercise(firstCopyExercise.copy(targetSets = 9))
        assertEquals(3, env.plans.getPlanDetail(src.id)!!.workouts[0].exercises[0].targetSets)
        assertNotEquals(src.id, copyId)
    }

    @Test
    fun `plans can be saved as a template and instantiated again`() = runBlocking {
        env.seeder.seedIfNeeded()
        val src = env.plans.observePlans().first().single()
        val templateId = env.plans.saveAsTemplate(src.id)!!
        assertTrue(env.plans.getPlanDetail(templateId)!!.plan.isTemplate)
        val fromTemplate = env.plans.createFromTemplate(templateId)!!
        val created = env.plans.getPlanDetail(fromTemplate)!!
        assertTrue(!created.plan.isTemplate)
        assertEquals(src.name, created.plan.name)
        assertEquals(4, created.workouts.size)
    }

    @Test
    fun `deleting a plan keeps workout history`() = runBlocking {
        env.seeder.seedIfNeeded()
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        val sessionId = env.workouts.startFromPlan(plan.workouts[0].workout.id)
        env.workouts.finishWorkout(sessionId, dropIncompleteSets = false)
        env.plans.deletePlan(plan.plan.id)
        val kept = env.workouts.getDetail(sessionId)
        assertNotNull(kept)
        assertEquals("Upper A", kept!!.session.title)
        assertEquals(7, kept.exercises.size)
        assertEquals("לחיצת חזה במוט", kept.exercises[0].exercise.nameHe)
    }

    @Test
    fun `custom exercise lifecycle - create, edit, archive when used, delete when unused`() = runBlocking {
        env.seeder.seedIfNeeded()
        val id = env.exercises.createCustom(
            app.liftos.domain.model.Exercise(
                id = "", nameHe = "משיכה מיוחדת", nameEn = "Special Pull", primaryMuscle = app.liftos.domain.model.MuscleGroup.BACK,
                videoUrl = "https://youtu.be/abc",
            ),
        )
        assertEquals("https://youtu.be/abc", env.exercises.get(id)!!.videoUrl)
        assertTrue(env.exercises.updateCustom(env.exercises.get(id)!!.copy(nameHe = "משיכה חדשה", videoUrl = "javascript:alert(1)")))
        val edited = env.exercises.get(id)!!
        assertEquals("משיכה חדשה", edited.nameHe)
        assertNull(edited.videoUrl)

        // Built-ins can't be edited or deleted.
        val builtIn = env.exercises.get("plank")!!
        assertTrue(!env.exercises.updateCustom(builtIn.copy(nameHe = "x")))
        assertEquals(app.liftos.data.repo.DeleteOutcome.NOT_ALLOWED, env.exercises.delete("plank"))

        // Unused custom exercise -> deleted.
        assertEquals(app.liftos.data.repo.DeleteOutcome.DELETED, env.exercises.delete(id))
        assertNull(env.exercises.get(id))

        // Used custom exercise -> archived, not deleted.
        val used = env.exercises.createCustom(builtIn.copy(id = "", nameHe = "בשימוש", nameEn = "Used", isBuiltIn = false))
        val planId = env.plans.createPlan("p"); val w = env.plans.addWorkout(planId, "d")
        env.plans.addExercise(w, env.exercises.get(used)!!, 60)
        assertEquals(app.liftos.data.repo.DeleteOutcome.ARCHIVED, env.exercises.delete(used))
        assertTrue(env.exercises.get(used)!!.isArchived)
    }
}
