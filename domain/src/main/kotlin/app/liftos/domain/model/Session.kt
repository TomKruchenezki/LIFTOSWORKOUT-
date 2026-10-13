package app.liftos.domain.model

import app.liftos.domain.logic.RestTimerState
import kotlinx.serialization.Serializable

/**
 * A performed (or in-progress) workout. Everything needed to display it later is
 * snapshotted here, so editing or deleting a plan never changes history.
 */
@Serializable
data class WorkoutSession(
    val id: String,
    val planId: String? = null,
    val plannedWorkoutId: String? = null,
    val planName: String = "",
    val title: String,
    val status: SessionStatus = SessionStatus.ACTIVE,
    /** Epoch millis, UTC. */
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Local calendar date (ISO yyyy-MM-dd) at the time of the workout, in [zoneId]. */
    val localDate: String,
    val zoneId: String,
    val notes: String = "",
    /** 1..5 */
    val energyRating: Int? = null,
    /** 1..5 */
    val moodRating: Int? = null,
    val rest: RestTimerState? = null,
    val source: SessionSource = SessionSource.LIVE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val isActive: Boolean get() = status == SessionStatus.ACTIVE

    /** Wall-clock duration in millis, or null while the workout is still running. */
    val durationMs: Long? get() = endedAt?.let { (it - startedAt).coerceAtLeast(0) }
}

@Serializable
data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String? = null,
    val plannedExerciseId: String? = null,
    // Snapshots - history must not change when the library entry changes.
    val nameHe: String,
    val nameEn: String = "",
    val primaryMuscle: MuscleGroup = MuscleGroup.OTHER,
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val isUnilateral: Boolean = false,
    val orderIndex: Int = 0,
    val plannedSets: Int = 0,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val targetWeightKg: Double? = null,
    val targetRir: Int? = null,
    val targetRpe: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    val skipped: Boolean = false,
    val skipReason: String = "",
    val supersetGroup: Int? = null,
)

@Serializable
data class ExerciseSet(
    val id: String,
    val sessionExerciseId: String,
    val orderIndex: Int = 0,
    val setType: SetType = SetType.WORKING,
    /** Only set for unilateral exercises. */
    val side: Side? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val rir: Int? = null,
    val rpe: Double? = null,
    val isCompleted: Boolean = false,
    val isFailure: Boolean = false,
    val completedAt: Long? = null,
    val notes: String = "",
)

data class ExerciseWithSets(
    val exercise: SessionExercise,
    val sets: List<ExerciseSet>,
)

data class WorkoutDetail(
    val session: WorkoutSession,
    val exercises: List<ExerciseWithSets>,
)
