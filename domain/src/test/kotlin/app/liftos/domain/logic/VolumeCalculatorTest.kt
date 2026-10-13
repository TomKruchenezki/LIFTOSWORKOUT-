package app.liftos.domain.logic

import app.liftos.domain.TestData
import app.liftos.domain.TestData.set
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.SetType
import app.liftos.domain.model.WorkoutDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeCalculatorTest {

    @Test
    fun `set volume is weight times reps for completed working sets`() {
        assertEquals(600.0, VolumeCalculator.setVolume(set("a", weight = 60.0, reps = 10)), 0.0001)
        assertEquals(500.0, VolumeCalculator.setVolume(set("b", weight = 62.5, reps = 8)), 0.0001)
    }

    @Test
    fun `warm-up sets and incomplete sets are excluded from volume`() {
        assertEquals(0.0, VolumeCalculator.setVolume(set("w", type = SetType.WARMUP)), 0.0)
        assertEquals(0.0, VolumeCalculator.setVolume(set("n", done = false)), 0.0)
    }

    @Test
    fun `missing weight or reps contributes zero`() {
        assertEquals(0.0, VolumeCalculator.setVolume(set("a", weight = null)), 0.0)
        assertEquals(0.0, VolumeCalculator.setVolume(set("b", reps = null)), 0.0)
    }

    @Test
    fun `session totals separate working sets from warm-ups`() {
        val se = TestData.sessionExercise()
        val sets = listOf(
            set("w1", order = 0, weight = 40.0, reps = 10, type = SetType.WARMUP),
            set("a", order = 1, weight = 80.0, reps = 5),
            set("b", order = 2, weight = 80.0, reps = 5),
            set("c", order = 3, weight = 80.0, reps = 4, done = false),
        )
        val detail = WorkoutDetail(TestData.session(), listOf(ExerciseWithSets(se, sets)))
        val t = VolumeCalculator.totals(detail)
        assertEquals(800.0, t.volumeKg, 0.0001)
        assertEquals(2, t.workingSets)
        assertEquals(1, t.warmupSets)
        assertEquals(10, t.totalReps)
        assertEquals(1, t.exercisesPerformed)
    }

    @Test
    fun `editing a historical weight changes volume (70 instead of 60)`() {
        val original = TestData.benchDetail(weights = listOf(70.0, 70.0, 70.0), reps = listOf(10, 10, 10))
        assertEquals(2100.0, VolumeCalculator.sessionVolume(original), 0.0001)
        val fixed = original.copy(
            exercises = original.exercises.map { ex ->
                ex.copy(sets = ex.sets.map { it.copy(weightKg = 60.0) })
            },
        )
        assertEquals(1800.0, VolumeCalculator.sessionVolume(fixed), 0.0001)
    }

    @Test
    fun `top set picks the heaviest then highest reps`() {
        val sets = listOf(
            set("a", weight = 60.0, reps = 12),
            set("b", weight = 80.0, reps = 5),
            set("c", weight = 80.0, reps = 6),
            set("d", weight = 100.0, reps = 1, done = false),
        )
        assertEquals("c", VolumeCalculator.topSet(sets)?.id)
        assertNull(VolumeCalculator.topSet(emptyList()))
    }
}
