package app.liftos.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.liftos.domain.model.Difficulty
import app.liftos.domain.model.EffortMode
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.SessionSource
import app.liftos.domain.model.SessionStatus
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.ThemeMode
import app.liftos.domain.model.TrackingType
import app.liftos.domain.model.ActivityType

/*
 * Room entities. They mirror the domain models 1:1 (see Mappers.kt). List-valued fields are stored
 * as JSON/CSV strings so no TypeConverters are needed. Enums are stored by name.
 *
 * Foreign keys:
 *  - plan content cascades with its plan;
 *  - a workout's exercises cascade with the workout;
 *  - session history references plans only softly (planId/plannedWorkoutId have no FK) so deleting a
 *    plan never touches history;
 *  - session_exercises.exerciseId is SET NULL: history keeps its own name snapshot.
 */

@Entity(
    tableName = "exercises",
    indices = [Index("primaryMuscle"), Index("isArchived"), Index("nameEn")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val nameHe: String,
    val nameEn: String,
    val primaryMuscle: MuscleGroup,
    /** Comma separated MuscleGroup names. */
    val secondaryMuscles: String,
    val equipment: Equipment,
    val difficulty: Difficulty,
    val type: ExerciseType,
    val trackingType: TrackingType,
    val isUnilateral: Boolean,
    val description: String,
    /** JSON array of strings. */
    val instructions: String,
    val mistakes: String,
    val safety: String,
    val alternativeIds: String,
    val videoUrl: String?,
    val isBuiltIn: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "workout_plans", indices = [Index("isTemplate"), Index("sortOrder")])
data class WorkoutPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val goal: String,
    val workoutsPerWeek: Int,
    val isTemplate: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "planned_workouts",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId"), Index("weekday")],
)
data class PlannedWorkoutEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val name: String,
    val description: String,
    val orderIndex: Int,
    val weekday: Int?,
    val notes: String,
)

@Entity(
    tableName = "planned_exercises",
    foreignKeys = [
        ForeignKey(
            entity = PlannedWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedWorkoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("plannedWorkoutId"), Index("exerciseId")],
)
data class PlannedExerciseEntity(
    @PrimaryKey val id: String,
    val plannedWorkoutId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val targetWeightKg: Double?,
    val targetRir: Int?,
    val targetRpe: Double?,
    val restSeconds: Int,
    val notes: String,
    val warmupSets: Int,
    val supersetGroup: Int?,
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index("status"), Index("startedAt"), Index("localDate"), Index("plannedWorkoutId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val planId: String?,
    val plannedWorkoutId: String?,
    val planName: String,
    val title: String,
    val status: SessionStatus,
    val startedAt: Long,
    val endedAt: Long?,
    val localDate: String,
    val zoneId: String,
    val notes: String,
    val energyRating: Int?,
    val moodRating: Int?,
    val restTotalMs: Long?,
    val restEndsAt: Long?,
    val restPausedRemainingMs: Long?,
    val restLabel: String,
    val source: SessionSource,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String?,
    val plannedExerciseId: String?,
    val nameHe: String,
    val nameEn: String,
    val primaryMuscle: MuscleGroup,
    val trackingType: TrackingType,
    val isUnilateral: Boolean,
    val orderIndex: Int,
    val plannedSets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val targetWeightKg: Double?,
    val targetRir: Int?,
    val targetRpe: Double?,
    val restSeconds: Int,
    val notes: String,
    val skipped: Boolean,
    val skipReason: String,
    val supersetGroup: Int?,
)

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId"), Index("isCompleted")],
)
data class ExerciseSetEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val orderIndex: Int,
    val setType: SetType,
    val side: Side?,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val rir: Int?,
    val rpe: Double?,
    val isCompleted: Boolean,
    val isFailure: Boolean,
    val completedAt: Long?,
    val notes: String,
)

@Entity(tableName = "body_measurements", indices = [Index("measuredAt"), Index("localDate")])
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val measuredAt: Long,
    val localDate: String,
    val zoneId: String,
    val weightKg: Double?,
    val waistCm: Double?,
    val chestCm: Double?,
    val armCm: Double?,
    val thighCm: Double?,
    val hipsCm: Double?,
    val neckCm: Double?,
    val bodyFatPct: Double?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "activity_events", indices = [Index("startsAt"), Index("localDate")])
data class ActivityEventEntity(
    @PrimaryKey val id: String,
    val type: ActivityType,
    val title: String,
    val notes: String,
    val startsAt: Long,
    val localDate: String,
    val zoneId: String,
    val durationMin: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Single-row table (id is always 1). */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: ThemeMode,
    val effortMode: EffortMode,
    val vibrateOnSetComplete: Boolean,
    val vibrateOnRestEnd: Boolean,
    val restNotificationEnabled: Boolean,
    val autoStartRest: Boolean,
    val keepScreenOn: Boolean,
    val defaultRestCompoundSec: Int,
    val defaultRestIsolationSec: Int,
    val activePlanId: String?,
    val weeklyGoalWorkouts: Int,
    val language: String,
)
