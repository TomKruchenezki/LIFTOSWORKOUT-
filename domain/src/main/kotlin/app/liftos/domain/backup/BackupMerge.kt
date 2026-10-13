package app.liftos.domain.backup

/** Ids that already exist in the database, per table. */
data class ExistingIds(
    val exercises: Set<String> = emptySet(),
    val plans: Set<String> = emptySet(),
    val plannedWorkouts: Set<String> = emptySet(),
    val plannedExercises: Set<String> = emptySet(),
    val sessions: Set<String> = emptySet(),
    val sessionExercises: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val measurements: Set<String> = emptySet(),
    val events: Set<String> = emptySet(),
    val customMetrics: Set<String> = emptySet(),
    val plannedOverrides: Set<String> = emptySet(),
    val goals: Set<String> = emptySet(),
    val goalChanges: Set<String> = emptySet(),
    val photos: Set<String> = emptySet(),
    val exercisePrefs: Set<String> = emptySet(),
    val progressionDecisions: Set<String> = emptySet(),
)

/**
 * "Merge" import: adds only what does not exist yet, never overwrites or deletes existing data.
 * A record whose id already exists is skipped together with its children, so a workout can never
 * end up with a mixture of old and imported sets. Importing the same file twice is a no-op.
 */
object BackupMerge {

    fun delta(incoming: BackupData, existing: ExistingIds): BackupData {
        val exercises = incoming.exercises.filter { it.id !in existing.exercises }

        val plans = incoming.plans.filter { it.id !in existing.plans }
        val newPlanIds = plans.map { it.id }.toSet()
        val workouts = incoming.plannedWorkouts.filter { it.id !in existing.plannedWorkouts && it.planId in newPlanIds }
        val newWorkoutIds = workouts.map { it.id }.toSet()
        val plannedExercises = incoming.plannedExercises
            .filter { it.id !in existing.plannedExercises && it.plannedWorkoutId in newWorkoutIds }

        val sessions = incoming.sessions.filter { it.id !in existing.sessions }
        val newSessionIds = sessions.map { it.id }.toSet()
        val sessionExercises = incoming.sessionExercises
            .filter { it.id !in existing.sessionExercises && it.sessionId in newSessionIds }
        val newSessionExerciseIds = sessionExercises.map { it.id }.toSet()
        val sets = incoming.sets.filter { it.id !in existing.sets && it.sessionExerciseId in newSessionExerciseIds }

        val goals = incoming.goals.filter { it.id !in existing.goals }
        val newGoalIds = goals.map { it.id }.toSet()
        val metrics = incoming.customMetrics.filter { it.id !in existing.customMetrics }
        val knownMetricIds = existing.customMetrics + metrics.map { it.id }

        return BackupData(
            exercises = exercises,
            plans = plans,
            plannedWorkouts = workouts,
            plannedExercises = plannedExercises,
            sessions = sessions,
            sessionExercises = sessionExercises,
            sets = sets,
            // A measurement keeps only the custom values whose metric exists after the merge.
            measurements = incoming.measurements.filter { it.id !in existing.measurements }
                .map { m -> m.copy(customValues = m.customValues.filterKeys { it in knownMetricIds }) },
            events = incoming.events.filter { it.id !in existing.events },
            settings = null, // local settings are never replaced by a merge
            customMetrics = metrics,
            plannedOverrides = incoming.plannedOverrides.filter { it.id !in existing.plannedOverrides },
            goals = goals,
            goalChanges = incoming.goalChanges.filter { it.id !in existing.goalChanges && it.goalId in newGoalIds },
            photos = incoming.photos.filter { it.id !in existing.photos },
            // Personal preferences and decisions already on this device win over the imported ones.
            exercisePrefs = incoming.exercisePrefs.filter { it.exerciseKey !in existing.exercisePrefs },
            progressionDecisions = incoming.progressionDecisions.filter { it.id !in existing.progressionDecisions },
        )
    }
}
