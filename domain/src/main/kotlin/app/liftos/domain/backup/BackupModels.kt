package app.liftos.domain.backup

import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.WorkoutPlan
import app.liftos.domain.model.WorkoutSession
import kotlinx.serialization.Serializable

/**
 * Full backup file. [schemaVersion] is bumped whenever the format changes in a way old code
 * could not read; [BackupMigrator] upgrades older files step by step.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val schemaVersion: Int = CURRENT_SCHEMA,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    val data: BackupData = BackupData(),
) {
    companion object {
        const val FORMAT = "liftos-backup"
        const val CURRENT_SCHEMA = 1
    }
}

@Serializable
data class BackupData(
    val exercises: List<Exercise> = emptyList(),
    val plans: List<WorkoutPlan> = emptyList(),
    val plannedWorkouts: List<PlannedWorkout> = emptyList(),
    val plannedExercises: List<PlannedExercise> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val sessionExercises: List<SessionExercise> = emptyList(),
    val sets: List<ExerciseSet> = emptyList(),
    val measurements: List<BodyMeasurement> = emptyList(),
    val events: List<ActivityEvent> = emptyList(),
    val settings: AppSettings? = null,
)

/** Counts shown in the import preview dialog. */
data class BackupSummary(
    val customExercises: Int,
    val plans: Int,
    val plannedWorkouts: Int,
    val sessions: Int,
    val completedSessions: Int,
    val sets: Int,
    val measurements: Int,
    val events: Int,
    val hasSettings: Boolean,
    val firstSessionDate: String?,
    val lastSessionDate: String?,
) {
    companion object {
        fun of(data: BackupData): BackupSummary {
            val dates = data.sessions.map { it.localDate }.sorted()
            return BackupSummary(
                customExercises = data.exercises.count { !it.isBuiltIn },
                plans = data.plans.size,
                plannedWorkouts = data.plannedWorkouts.size,
                sessions = data.sessions.size,
                completedSessions = data.sessions.count { !it.isActive },
                sets = data.sets.size,
                measurements = data.measurements.size,
                events = data.events.size,
                hasSettings = data.settings != null,
                firstSessionDate = dates.firstOrNull(),
                lastSessionDate = dates.lastOrNull(),
            )
        }
    }
}

enum class ImportMode { REPLACE_ALL, MERGE }
