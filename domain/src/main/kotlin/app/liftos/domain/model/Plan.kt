package app.liftos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutPlan(
    val id: String,
    val name: String,
    val description: String = "",
    /** Free text goal, e.g. "ירידה בשומן ושיפור מסת שריר". Shown on the dashboard. */
    val goal: String = "",
    /** Target workouts per week; 0 means "derive from the number of workouts". */
    val workoutsPerWeek: Int = 0,
    val isTemplate: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun targetPerWeek(workoutCount: Int): Int = if (workoutsPerWeek > 0) workoutsPerWeek else workoutCount
}

/** One training day of a plan (e.g. "Upper A"). */
@Serializable
data class PlannedWorkout(
    val id: String,
    val planId: String,
    val name: String,
    val description: String = "",
    val orderIndex: Int = 0,
    /** ISO day of week: 1 = Monday ... 7 = Sunday. Null = not scheduled on a fixed day. */
    val weekday: Int? = null,
    val notes: String = "",
)

/**
 * A planned exercise inside a [PlannedWorkout].
 * [repsMin]/[repsMax] are repetitions, or seconds when the exercise is tracked by duration.
 */
@Serializable
data class PlannedExercise(
    val id: String,
    val plannedWorkoutId: String,
    val exerciseId: String,
    val orderIndex: Int = 0,
    val targetSets: Int = 3,
    val repsMin: Int = 8,
    val repsMax: Int = 12,
    val targetWeightKg: Double? = null,
    val targetRir: Int? = null,
    val targetRpe: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    /** Number of warm-up rows added automatically when a workout starts. */
    val warmupSets: Int = 0,
    /** Reserved for supersets (same non-null value = same group). No UI in v1. */
    val supersetGroup: Int? = null,
)

/** A plan with all of its content, used by the editor and by duplication. */
data class PlanDetail(
    val plan: WorkoutPlan,
    val workouts: List<PlannedWorkoutDetail>,
)

data class PlannedWorkoutDetail(
    val workout: PlannedWorkout,
    val exercises: List<PlannedExercise>,
)
