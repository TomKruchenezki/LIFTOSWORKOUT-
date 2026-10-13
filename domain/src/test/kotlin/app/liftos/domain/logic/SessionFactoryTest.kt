package app.liftos.domain.logic

import app.liftos.domain.SeqIds
import app.liftos.domain.TestData
import app.liftos.domain.TestData.set
import app.liftos.domain.library.DefaultPlan
import app.liftos.domain.model.SessionStatus
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SessionFactoryTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val library = TestData.library.exercises.associateBy { it.id }
    private val plan = DefaultPlan.create(0L, SeqIds("p"))
    private val upperA = plan.workouts[0]
    private val lowerB = plan.workouts[3]

    private fun start(
        w: app.liftos.domain.model.PlannedWorkoutDetail = upperA,
        previous: Map<String, PreviousPerformance> = emptyMap(),
        now: Long = 1_760_000_000_000L,
    ) = SessionFactory.fromPlan(plan.plan, w.workout, w.exercises, library, previous, now, zone, SeqIds("s"))

    @Test
    fun `new session is active and snapshots plan data`() {
        val d = start()
        assertEquals(SessionStatus.ACTIVE, d.session.status)
        assertEquals("Upper A", d.session.title)
        assertEquals(plan.plan.name, d.session.planName)
        assertEquals(7, d.exercises.size)
        val bench = d.exercises[0]
        assertEquals("לחיצת חזה במוט", bench.exercise.nameHe)
        assertEquals("Barbell Bench Press", bench.exercise.nameEn)
        assertEquals(3, bench.exercise.plannedSets)
        assertEquals(5, bench.exercise.repsMin)
        assertEquals(8, bench.exercise.repsMax)
        assertEquals(180, bench.exercise.restSeconds)
        assertEquals(3, bench.sets.size)
    }

    @Test
    fun `no set is pre-marked as completed`() {
        val d = start()
        assertTrue(d.exercises.flatMap { it.sets }.none { it.isCompleted })
    }

    @Test
    fun `sets are pre-filled from the previous performance by index`() {
        val prev = PreviousPerformance(
            "barbell-bench-press", "2026-10-04", "Upper A",
            listOf(
                set("p1", order = 0, weight = 60.0, reps = 10, rir = 2),
                set("p2", order = 1, weight = 60.0, reps = 9, rir = 1),
                set("p3", order = 2, weight = 62.5, reps = 8, rir = 1),
            ),
        )
        val d = start(previous = mapOf(prev.exerciseId to prev))
        val sets = d.exercises[0].sets
        assertEquals(listOf(60.0, 60.0, 62.5), sets.map { it.weightKg })
        assertEquals(listOf(10, 9, 8), sets.map { it.reps })
        assertEquals(listOf(2, 1, 1), sets.map { it.rir })
        assertTrue(sets.none { it.isCompleted })
    }

    @Test
    fun `when fewer previous sets exist the last one is reused`() {
        val prev = PreviousPerformance("barbell-bench-press", "2026-10-04", "Upper A", listOf(set("p1", weight = 70.0, reps = 6)))
        val sets = start(previous = mapOf(prev.exerciseId to prev)).exercises[0].sets
        assertTrue(sets.all { it.weightKg == 70.0 && it.reps == 6 })
    }

    @Test
    fun `without history the sets are empty (no invented numbers)`() {
        val sets = start().exercises[0].sets
        assertTrue(sets.all { it.weightKg == null && it.reps == null })
    }

    @Test
    fun `previous warm-ups are not copied into working sets`() {
        val prev = PreviousPerformance(
            "barbell-bench-press", "2026-10-04", "Upper A",
            listOf(set("w", order = 0, weight = 20.0, reps = 12, type = SetType.WARMUP), set("a", order = 1, weight = 80.0, reps = 5)),
        )
        val sets = start(previous = mapOf(prev.exerciseId to prev)).exercises[0].sets
        assertTrue(sets.all { it.weightKg == 80.0 })
    }

    @Test
    fun `unilateral exercise creates right and left rows per planned set`() {
        val split = start(lowerB).exercises.first { it.exercise.exerciseId == "bulgarian-split-squat" }
        assertEquals(6, split.sets.size)
        assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT), split.sets.map { it.side })
    }

    @Test
    fun `duration exercise keeps weight and reps empty`() {
        val plank = start(lowerB).exercises.first { it.exercise.exerciseId == "plank" }
        assertTrue(plank.sets.all { it.reps == null && it.weightKg == null })
        assertEquals(30, plank.exercise.repsMin)
        assertEquals(60, plank.exercise.repsMax)
    }

    @Test
    fun `later plan edits do not change an existing session snapshot`() {
        val d = start()
        val snapshotName = d.exercises[0].exercise.nameHe
        val snapshotSets = d.exercises[0].exercise.plannedSets
        // Simulate editing the plan afterwards.
        val edited = upperA.exercises[0].copy(targetSets = 10, repsMin = 1, repsMax = 2)
        assertEquals(10, edited.targetSets)
        assertEquals(snapshotName, d.exercises[0].exercise.nameHe)
        assertEquals(snapshotSets, d.exercises[0].exercise.plannedSets)
        assertEquals(3, d.exercises[0].exercise.plannedSets)
    }

    @Test
    fun `local date is taken from the start instant and zone`() {
        val ms = java.time.Instant.parse("2026-10-13T22:30:00Z").toEpochMilli()
        assertEquals("2026-10-14", start(now = ms).session.localDate)
    }

    @Test
    fun `warm-up rows from the plan are created first`() {
        val ex = upperA.exercises[0].copy(warmupSets = 2)
        val d = SessionFactory.fromPlan(plan.plan, upperA.workout, listOf(ex), library, emptyMap(), 0L, zone, SeqIds("w"))
        val sets = d.exercises[0].sets
        assertEquals(listOf(SetType.WARMUP, SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.WORKING), sets.map { it.setType })
        assertNull(sets[0].reps)
        assertFalse(sets.any { it.isCompleted })
    }

    @Test
    fun `exercises missing from the library are skipped instead of crashing`() {
        val ghost = upperA.exercises[0].copy(id = "ghost", exerciseId = "does-not-exist", orderIndex = 99)
        val d = SessionFactory.fromPlan(plan.plan, upperA.workout, upperA.exercises + ghost, library, emptyMap(), 0L, zone, SeqIds("g"))
        assertEquals(7, d.exercises.size)
    }

    @Test
    fun `an exercise added on the fly has no link to a planned exercise`() {
        val hammer = library.getValue("hammer-curl")
        val prev = PreviousPerformance("hammer-curl", "2026-10-04", "Upper B", listOf(set("p1", weight = 14.0, reps = 12)))
        val built = SessionFactory.adHocExercise("s1", 4, hammer, targetSets = 3, restSeconds = 90, previous = prev, idGen = SeqIds("a"))
        assertNull(built.exercise.plannedExerciseId)
        assertEquals(4, built.exercise.orderIndex)
        assertEquals(3, built.sets.size)
        assertTrue(built.sets.all { it.weightKg == 14.0 && it.reps == 12 && !it.isCompleted })
        assertEquals(90, built.exercise.restSeconds)
        assertEquals(10, built.exercise.repsMin) // isolation exercises start at 10-15
    }

    @Test
    fun `blank workout has no exercises`() {
        val d = SessionFactory.blank("אימון חופשי", 0L, zone, SeqIds("b"))
        assertTrue(d.exercises.isEmpty())
        assertNull(d.session.planId)
    }
}
