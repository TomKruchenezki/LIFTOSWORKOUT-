package app.liftos.domain.library

import app.liftos.domain.SeqIds
import app.liftos.domain.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The starting program exactly as the owner wrote it in prompt 1 (section 4): every exercise with its sets and rep
 * range, in order. The expected table below is typed from the prompt, not read from the plan, so a typo in the seed
 * data cannot hide behind itself.
 */
class DefaultPlanTableTest {
    private class Line(val englishName: String, val sets: Int, val min: Int, val max: Int)

    private val expected: Map<String, List<Line>> = mapOf(
        "Upper A" to listOf(
            Line("Bench Press", 3, 5, 8),
            Line("Chest-Supported Row", 3, 6, 10),
            Line("Incline Dumbbell Press", 2, 8, 12),
            Line("Lat Pulldown", 3, 8, 12),
            Line("Lateral Raise", 3, 12, 20),
            Line("Triceps Pushdown", 2, 10, 15),
            Line("EZ Bar Curl", 2, 10, 15),
        ),
        "Lower A" to listOf(
            Line("Squat", 3, 5, 8),
            Line("Romanian Deadlift", 3, 6, 10),
            Line("Leg Press", 2, 10, 15),
            Line("Seated Leg Curl", 2, 10, 15),
            Line("Standing Calf Raise", 3, 10, 15),
            Line("Cable Crunch", 2, 10, 15),
        ),
        "Upper B" to listOf(
            Line("Incline Dumbbell Press", 3, 8, 12),
            Line("Lat Pulldown", 3, 6, 12), // the prompt says "Pull-Up / Lat Pulldown"
            Line("Shoulder Press", 2, 8, 12),
            Line("Seated Cable Row", 3, 8, 12),
            Line("Cable Chest Fly", 2, 12, 15),
            Line("Lateral Raise", 2, 12, 20),
            Line("Reverse Pec Deck", 2, 12, 20),
            Line("Overhead", 2, 10, 15), // Overhead Triceps Extension
            Line("Dumbbell Curl", 2, 10, 15),
        ),
        "Lower B" to listOf(
            Line("Hack Squat", 3, 8, 12), // the prompt says "Hack Squat / Leg Press"
            Line("Bulgarian Split Squat", 3, 8, 12),
            Line("Hip Thrust", 2, 8, 12),
            Line("Lying Leg Curl", 3, 10, 15),
            Line("Seated Calf Raise", 3, 12, 20),
            Line("Plank", 2, 30, 60), // seconds
        ),
    )

    @Test
    fun `every exercise of the four workouts has the sets and reps the owner asked for`() {
        val plan = DefaultPlan.create(0L, SeqIds())
        val byId = TestData.library.exercises.associateBy { it.id }
        assertEquals(expected.keys.toList(), plan.workouts.map { it.workout.name })
        plan.workouts.forEach { w ->
            val want = expected.getValue(w.workout.name)
            assertEquals("${w.workout.name}: number of exercises", want.size, w.exercises.size)
            w.exercises.zip(want).forEachIndexed { i, (got, line) ->
                val ex = byId.getValue(got.exerciseId)
                val label = "${w.workout.name} #${i + 1} (${ex.nameEn})"
                assertTrue("$label should be '${line.englishName}'", ex.nameEn.contains(line.englishName, ignoreCase = true))
                assertEquals("$label sets", line.sets, got.targetSets)
                assertEquals("$label min reps", line.min, got.repsMin)
                assertEquals("$label max reps", line.max, got.repsMax)
            }
        }
    }

    @Test
    fun `the week is Sunday Monday rest Wednesday Thursday`() {
        val plan = DefaultPlan.create(0L, SeqIds())
        val days = plan.workouts.associate { it.workout.name to it.workout.weekday }
        assertEquals(mapOf("Upper A" to 7, "Lower A" to 1, "Upper B" to 3, "Lower B" to 4), days)
        assertTrue("Tuesday is a rest day", days.values.none { it == 2 })
    }
}
