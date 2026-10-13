package app.liftos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String,
    val nameHe: String,
    val nameEn: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment = Equipment.OTHER,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val type: ExerciseType = ExerciseType.COMPOUND,
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val isUnilateral: Boolean = false,
    val description: String = "",
    val instructions: List<String> = emptyList(),
    val mistakes: List<String> = emptyList(),
    val safety: List<String> = emptyList(),
    val alternativeIds: List<String> = emptyList(),
    /** A user-supplied, direct video link. Never invented by the app. */
    val videoUrl: String? = null,
    val isBuiltIn: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
