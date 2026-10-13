package app.liftos.domain.library

import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.model.PlanDetail
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.PlannedWorkoutDetail
import app.liftos.domain.model.WorkoutPlan

/**
 * The user's starting program: 4 days a week, fat loss + muscle gain.
 * It is only seed data - every part of it can be edited or deleted in the app.
 *
 * Weekdays use ISO numbers: 7 = Sunday, 1 = Monday, 3 = Wednesday, 4 = Thursday.
 */
object DefaultPlan {

    private data class Row(
        val exerciseId: String,
        val sets: Int,
        val min: Int,
        val max: Int,
        val rest: Int,
        val notes: String = "",
    )

    private val upperA = listOf(
        Row("barbell-bench-press", 3, 5, 8, 180),
        Row("chest-supported-row", 3, 6, 10, 150),
        Row("incline-dumbbell-press", 2, 8, 12, 150),
        Row("lat-pulldown", 3, 8, 12, 120),
        Row("dumbbell-lateral-raise", 3, 12, 20, 60),
        Row("triceps-pushdown", 2, 10, 15, 90),
        Row("ez-bar-curl", 2, 10, 15, 90),
    )

    private val lowerA = listOf(
        Row("barbell-back-squat", 3, 5, 8, 180),
        Row("romanian-deadlift", 3, 6, 10, 180),
        Row("leg-press", 2, 10, 15, 150),
        Row("seated-leg-curl", 2, 10, 15, 90),
        Row("standing-calf-raise", 3, 10, 15, 90),
        Row("cable-crunch", 2, 10, 15, 90),
    )

    private val upperB = listOf(
        Row("incline-dumbbell-press", 3, 8, 12, 150),
        Row("lat-pulldown", 3, 6, 12, 120, "אפשר להחליף למתח (Pull-Up)"),
        Row("seated-dumbbell-shoulder-press", 2, 8, 12, 150),
        Row("seated-cable-row", 3, 8, 12, 120),
        Row("cable-chest-fly", 2, 12, 15, 90),
        Row("dumbbell-lateral-raise", 2, 12, 20, 60),
        Row("reverse-pec-deck", 2, 12, 20, 60),
        Row("overhead-cable-triceps-extension", 2, 10, 15, 90),
        Row("dumbbell-curl", 2, 10, 15, 90),
    )

    private val lowerB = listOf(
        Row("hack-squat", 3, 8, 12, 180, "אפשר להחליף ל-Leg Press"),
        Row("bulgarian-split-squat", 3, 8, 12, 150, "מספר הסטים והחזרות הם לכל רגל"),
        Row("hip-thrust", 2, 8, 12, 150),
        Row("lying-leg-curl", 3, 10, 15, 90),
        Row("seated-calf-raise", 3, 12, 20, 90),
        Row("plank", 2, 30, 60, 60, "משך בשניות"),
    )

    /** All exercise ids the default plan depends on (the library must contain every one). */
    val requiredExerciseIds: Set<String> =
        (upperA + lowerA + upperB + lowerB).map { it.exerciseId }.toSet()

    fun create(nowMs: Long, idGen: IdGenerator = IdGenerator.Random): PlanDetail {
        val plan = WorkoutPlan(
            id = idGen.newId(),
            name = "4 ימים בשבוע - פלג עליון / תחתון",
            description = "פיצול פלג גוף עליון ותחתון, ארבעה אימונים בשבוע, עם דגש כוח בחלק מהאימונים ודגש נפח באחרים.",
            goal = "ירידה בשומן ושיפור מסת שריר",
            workoutsPerWeek = 4,
            createdAt = nowMs,
            updatedAt = nowMs,
        )
        fun day(index: Int, name: String, description: String, weekday: Int, rows: List<Row>): PlannedWorkoutDetail {
            val w = PlannedWorkout(
                id = idGen.newId(),
                planId = plan.id,
                name = name,
                description = description,
                orderIndex = index,
                weekday = weekday,
            )
            val exercises = rows.mapIndexed { i, r ->
                PlannedExercise(
                    id = idGen.newId(),
                    plannedWorkoutId = w.id,
                    exerciseId = r.exerciseId,
                    orderIndex = i,
                    targetSets = r.sets,
                    repsMin = r.min,
                    repsMax = r.max,
                    restSeconds = r.rest,
                    notes = r.notes,
                )
            }
            return PlannedWorkoutDetail(w, exercises)
        }
        return PlanDetail(
            plan,
            listOf(
                day(0, "Upper A", "פלג גוף עליון - דגש כוח", 7, upperA),
                day(1, "Lower A", "פלג גוף תחתון - דגש כוח", 1, lowerA),
                day(2, "Upper B", "פלג גוף עליון - דגש נפח שריר", 3, upperB),
                day(3, "Lower B", "פלג גוף תחתון - דגש נפח ויציבות", 4, lowerB),
            ),
        )
    }
}
