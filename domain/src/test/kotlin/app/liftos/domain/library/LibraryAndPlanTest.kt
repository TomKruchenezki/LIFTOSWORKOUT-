package app.liftos.domain.library

import app.liftos.domain.SeqIds
import app.liftos.domain.TestData
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAndPlanTest {

    private val lib = TestData.library

    @Test
    fun `library has at least 100 exercises`() {
        assertTrue("found ${lib.exercises.size}", lib.exercises.size >= 100)
    }

    @Test
    fun `library passes every consistency rule`() {
        val problems = ExerciseLibrary.validate(lib.exercises)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `built-in exercises never carry an invented video link`() {
        assertTrue(lib.exercises.all { it.videoUrl == null && it.isBuiltIn })
    }

    @Test
    fun `every muscle group used is covered and key groups have several exercises`() {
        val byMuscle = lib.exercises.groupBy { it.primaryMuscle }
        listOf(
            MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS,
            MuscleGroup.TRICEPS, MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
            MuscleGroup.CALVES, MuscleGroup.ABS,
        ).forEach { assertTrue("$it", (byMuscle[it]?.size ?: 0) >= 4) }
    }

    @Test
    fun `plank is tracked by duration and bulgarian split squat is unilateral`() {
        assertEquals(TrackingType.DURATION, lib.exercises.first { it.id == "plank" }.trackingType)
        assertTrue(lib.exercises.first { it.id == "bulgarian-split-squat" }.isUnilateral)
    }

    @Test
    fun `content hash is stable and changes with content`() {
        val files = TestData.libraryDir().listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { it.readText() }
        assertEquals(lib.contentHash, ExerciseLibrary.parse(files).contentHash)
        val changed = files.toMutableList().also { it[0] = it[0] + " " }
        assertFalse(lib.contentHash == ExerciseLibrary.parse(changed).contentHash)
    }

    @Test
    fun `validator catches duplicates, bad alternatives and missing content`() {
        val e = lib.exercises.first()
        val broken = listOf(
            e,
            e,
            e.copy(id = "other", nameEn = "Other", alternativeIds = listOf("nope"), instructions = listOf("a"), safety = emptyList()),
        )
        val problems = ExerciseLibrary.validate(broken)
        assertTrue(problems.any { it.contains("מזהה כפול") })
        assertTrue(problems.any { it.contains("חלופה לא קיימת") })
        assertTrue(problems.any { it.contains("פחות מ-3 שלבי ביצוע") })
        assertTrue(problems.any { it.contains("חסרים דגשי בטיחות") })
    }

    @Test
    fun `youtube search link is a search and url validation is strict`() {
        val url = YouTube.searchUrl("Barbell Bench Press")
        assertTrue(url.startsWith("https://www.youtube.com/results?search_query="))
        assertTrue(url.contains("Barbell+Bench+Press"))
        assertTrue(YouTube.isValidWebUrl("https://youtu.be/abc"))
        assertFalse(YouTube.isValidWebUrl("javascript:alert(1)"))
        assertFalse(YouTube.isValidWebUrl("not a url"))
        assertFalse(YouTube.isValidWebUrl(null))
    }

    // ---- default plan -----------------------------------------------------------------------

    @Test
    fun `default plan references only exercises that exist in the library`() {
        val ids = lib.exercises.map { it.id }.toSet()
        assertTrue((DefaultPlan.requiredExerciseIds - ids).isEmpty())
    }

    @Test
    fun `default plan matches the requested program`() {
        val plan = DefaultPlan.create(0L, SeqIds())
        assertEquals(4, plan.workouts.size)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), plan.workouts.map { it.workout.name })
        assertEquals(listOf(7, 1, 3, 4), plan.workouts.map { it.workout.weekday })
        assertEquals(listOf(7, 6, 9, 6), plan.workouts.map { it.exercises.size })
        assertEquals("ירידה בשומן ושיפור מסת שריר", plan.plan.goal)

        val upperA = plan.workouts[0].exercises
        assertEquals("barbell-bench-press", upperA[0].exerciseId)
        assertEquals(3, upperA[0].targetSets); assertEquals(5, upperA[0].repsMin); assertEquals(8, upperA[0].repsMax)
        assertEquals(2, upperA[2].targetSets); assertEquals(8, upperA[2].repsMin); assertEquals(12, upperA[2].repsMax)
        assertEquals(12, upperA[4].repsMin); assertEquals(20, upperA[4].repsMax)

        val lowerB = plan.workouts[3].exercises
        assertEquals("plank", lowerB.last().exerciseId)
        assertEquals(30, lowerB.last().repsMin); assertEquals(60, lowerB.last().repsMax)
    }

    @Test
    fun `default plan rest times follow the compound vs isolation guideline`() {
        val plan = DefaultPlan.create(0L, SeqIds())
        val all = plan.workouts.flatMap { it.exercises }
        val byEx = lib.exercises.associateBy { it.id }
        all.forEach { p ->
            val ex = byEx.getValue(p.exerciseId)
            assertTrue("${ex.id} rest ${p.restSeconds}", p.restSeconds in 60..180)
            if (ex.type == app.liftos.domain.model.ExerciseType.ISOLATION) assertTrue(p.restSeconds <= 120)
        }
    }

    @Test
    fun `default plan ids are unique and tied together`() {
        val plan = DefaultPlan.create(0L, SeqIds())
        val ids = plan.workouts.flatMap { w -> listOf(w.workout.id) + w.exercises.map { it.id } } + plan.plan.id
        assertEquals(ids.size, ids.toSet().size)
        plan.workouts.forEach { w ->
            assertEquals(plan.plan.id, w.workout.planId)
            w.exercises.forEach { assertEquals(w.workout.id, it.plannedWorkoutId) }
        }
        assertNull(plan.workouts[0].exercises[0].targetWeightKg)
    }
}
