package app.liftos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType
import kotlinx.coroutines.flow.Flow

/** Result row of "find the previous performance of an exercise". */
data class PreviousRef(
    val sessionExerciseId: String,
    val localDate: String,
    val title: String,
)

/** Per exercise-in-workout totals; the history list sums them (a left+right pair counts as one set). */
data class ExerciseTotalsRow(
    val sessionId: String,
    val sessionExerciseId: String,
    val isUnilateral: Boolean,
    val volume: Double,
    val rightRows: Int,
    val leftRows: Int,
    val plainRows: Int,
)

data class CompletedRefRow(
    val plannedWorkoutId: String?,
    val localDate: String,
)

data class PlanStatsRow(
    val planId: String,
    val workouts: Int,
    val exercises: Int,
)

/** One completed set joined with its workout and exercise: the raw material of all analytics. */
data class SetFactRow(
    val sessionId: String,
    val sessionTitle: String,
    val startedAt: Long,
    val localDate: String,
    val sessionExerciseId: String,
    val exerciseId: String?,
    val exerciseName: String,
    val exerciseNameEn: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: String?,
    val trackingType: TrackingType,
    val isUnilateral: Boolean,
    val exerciseOrder: Int,
    val plannedSets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val equipment: Equipment?,
    val exerciseType: ExerciseType?,
    val setId: String,
    val setOrder: Int,
    val setType: SetType,
    val side: Side?,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val rir: Int?,
    val rpe: Double?,
    val isFailure: Boolean,
    val completedAt: Long?,
)

private const val FACT_SELECT =
    "SELECT s.id AS sessionId, s.title AS sessionTitle, s.startedAt AS startedAt, s.localDate AS localDate, " +
        "se.id AS sessionExerciseId, se.exerciseId AS exerciseId, se.nameHe AS exerciseName, se.nameEn AS exerciseNameEn, " +
        "se.primaryMuscle AS primaryMuscle, e.secondaryMuscles AS secondaryMuscles, se.trackingType AS trackingType, " +
        "se.isUnilateral AS isUnilateral, se.orderIndex AS exerciseOrder, se.plannedSets AS plannedSets, " +
        "se.repsMin AS repsMin, se.repsMax AS repsMax, e.equipment AS equipment, e.type AS exerciseType, " +
        "st.id AS setId, st.orderIndex AS setOrder, st.setType AS setType, st.side AS side, st.weightKg AS weightKg, " +
        "st.reps AS reps, st.durationSec AS durationSec, st.rir AS rir, st.rpe AS rpe, st.isFailure AS isFailure, " +
        "st.completedAt AS completedAt " +
        "FROM exercise_sets st " +
        "INNER JOIN session_exercises se ON se.id = st.sessionExerciseId " +
        "INNER JOIN workout_sessions s ON s.id = se.sessionId " +
        "LEFT JOIN exercises e ON e.id = se.exerciseId "

private const val FACT_ORDER = " ORDER BY s.startedAt, se.orderIndex, st.orderIndex"

@Dao
interface AnalyticsDao {
    @Query(FACT_SELECT + "WHERE s.status = 'COMPLETED' AND st.isCompleted = 1 AND se.skipped = 0" + FACT_ORDER)
    fun observeFacts(): Flow<List<SetFactRow>>

    @Query(FACT_SELECT + "WHERE s.status = 'COMPLETED' AND st.isCompleted = 1 AND se.skipped = 0" + FACT_ORDER)
    suspend fun getFacts(): List<SetFactRow>

    /** History of the given library exercises (plus ad-hoc ones, filtered by the caller), before the current workout. */
    @Query(
        FACT_SELECT + "WHERE s.status = 'COMPLETED' AND st.isCompleted = 1 AND se.skipped = 0 AND s.id != :excludeSessionId " +
            "AND (se.exerciseId IN (:exerciseIds) OR se.exerciseId IS NULL)" + FACT_ORDER,
    )
    suspend fun getFactsForExercises(exerciseIds: List<String>, excludeSessionId: String): List<SetFactRow>

    /** Completed sets of one workout - also works for the workout that is still running. */
    @Query(FACT_SELECT + "WHERE s.id = :sessionId AND st.isCompleted = 1 AND se.skipped = 0" + FACT_ORDER)
    suspend fun getFactsOfSession(sessionId: String): List<SetFactRow>

    @Query(FACT_SELECT + "WHERE s.id = :sessionId AND st.isCompleted = 1 AND se.skipped = 0" + FACT_ORDER)
    fun observeFactsOfSession(sessionId: String): Flow<List<SetFactRow>>
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY nameHe")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY nameHe")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<ExerciseEntity>

    @Query("SELECT id FROM exercises")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :id")
    suspend fun sessionUsage(id: String): Int

    @Query("SELECT COUNT(*) FROM planned_exercises WHERE exerciseId = :id")
    suspend fun planUsage(id: String): Int
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM workout_plans ORDER BY isTemplate, sortOrder, createdAt")
    fun observePlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans ORDER BY isTemplate, sortOrder, createdAt")
    suspend fun getPlans(): List<WorkoutPlanEntity>

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    fun observePlan(id: String): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getPlan(id: String): WorkoutPlanEntity?

    @Query("SELECT COUNT(*) FROM workout_plans")
    suspend fun planCount(): Int

    @Query("SELECT id FROM workout_plans")
    suspend fun planIds(): List<String>

    @Query(
        "SELECT p.id AS planId, COUNT(DISTINCT w.id) AS workouts, COUNT(e.id) AS exercises " +
            "FROM workout_plans p LEFT JOIN planned_workouts w ON w.planId = p.id " +
            "LEFT JOIN planned_exercises e ON e.plannedWorkoutId = w.id GROUP BY p.id",
    )
    fun observePlanStats(): Flow<List<PlanStatsRow>>

    @Query("SELECT * FROM planned_workouts ORDER BY planId, orderIndex")
    suspend fun getAllWorkouts(): List<PlannedWorkoutEntity>

    @Query("SELECT * FROM planned_workouts WHERE planId = :planId ORDER BY orderIndex")
    fun observeWorkouts(planId: String): Flow<List<PlannedWorkoutEntity>>

    @Query("SELECT * FROM planned_workouts WHERE planId = :planId ORDER BY orderIndex")
    suspend fun getWorkouts(planId: String): List<PlannedWorkoutEntity>

    @Query("SELECT * FROM planned_workouts WHERE id = :id")
    suspend fun getWorkout(id: String): PlannedWorkoutEntity?

    @Query("SELECT * FROM planned_workouts WHERE id = :id")
    fun observeWorkout(id: String): Flow<PlannedWorkoutEntity?>

    @Query("SELECT id FROM planned_workouts")
    suspend fun workoutIds(): List<String>

    @Query("SELECT * FROM planned_exercises ORDER BY plannedWorkoutId, orderIndex")
    suspend fun getAllPlannedExercises(): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises WHERE plannedWorkoutId = :workoutId ORDER BY orderIndex")
    fun observeExercises(workoutId: String): Flow<List<PlannedExerciseEntity>>

    @Query("SELECT * FROM planned_exercises WHERE plannedWorkoutId = :workoutId ORDER BY orderIndex")
    suspend fun getExercises(workoutId: String): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises WHERE id = :id")
    suspend fun getPlannedExercise(id: String): PlannedExerciseEntity?

    @Query("SELECT id FROM planned_exercises")
    suspend fun plannedExerciseIds(): List<String>

    @Query(
        "SELECT pe.* FROM planned_exercises pe INNER JOIN planned_workouts pw ON pw.id = pe.plannedWorkoutId " +
            "WHERE pw.planId = :planId ORDER BY pw.orderIndex, pe.orderIndex",
    )
    fun observeAllExercisesOfPlan(planId: String): Flow<List<PlannedExerciseEntity>>

    @Upsert
    suspend fun upsertPlan(entity: WorkoutPlanEntity)

    @Upsert
    suspend fun upsertPlans(entities: List<WorkoutPlanEntity>)

    @Upsert
    suspend fun upsertWorkout(entity: PlannedWorkoutEntity)

    @Upsert
    suspend fun upsertWorkouts(entities: List<PlannedWorkoutEntity>)

    @Upsert
    suspend fun upsertPlannedExercise(entity: PlannedExerciseEntity)

    @Upsert
    suspend fun upsertPlannedExercises(entities: List<PlannedExerciseEntity>)

    @Query("DELETE FROM workout_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    @Query("DELETE FROM planned_workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Query("DELETE FROM planned_exercises WHERE id = :id")
    suspend fun deletePlannedExercise(id: String)

    @Query("UPDATE planned_exercises SET orderIndex = :index WHERE id = :id")
    suspend fun setExerciseOrder(id: String, index: Int)

    @Query("UPDATE planned_workouts SET orderIndex = :index WHERE id = :id")
    suspend fun setWorkoutOrder(id: String, index: Int)

    @Query("DELETE FROM planned_exercises")
    suspend fun deleteAllPlannedExercises()

    @Query("DELETE FROM planned_workouts")
    suspend fun deleteAllWorkouts()

    @Query("DELETE FROM workout_plans")
    suspend fun deleteAllPlans()
}

@Dao
interface SessionDao {
    // ---- reading sessions ---------------------------------------------------------------

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("SELECT id FROM workout_sessions")
    suspend fun sessionIds(): List<String>

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    fun observeCompleted(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    suspend fun getCompleted(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC LIMIT 1")
    fun observeLastCompleted(): Flow<WorkoutSessionEntity?>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status = 'COMPLETED' AND localDate BETWEEN :from AND :to")
    fun observeCountBetween(from: String, to: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT plannedWorkoutId, localDate FROM workout_sessions WHERE status = 'COMPLETED' AND localDate >= :since")
    fun observeCompletedRefs(since: String): Flow<List<CompletedRefRow>>

    @Query(
        "SELECT s.id AS sessionId, se.id AS sessionExerciseId, se.isUnilateral AS isUnilateral, " +
            "COALESCE(SUM(CASE WHEN st.isCompleted = 1 AND st.setType != 'WARMUP' THEN st.weightKg * st.reps ELSE 0 END), 0) AS volume, " +
            "COUNT(CASE WHEN st.isCompleted = 1 AND st.setType != 'WARMUP' AND st.side = 'RIGHT' THEN 1 END) AS rightRows, " +
            "COUNT(CASE WHEN st.isCompleted = 1 AND st.setType != 'WARMUP' AND st.side = 'LEFT' THEN 1 END) AS leftRows, " +
            "COUNT(CASE WHEN st.isCompleted = 1 AND st.setType != 'WARMUP' AND st.side IS NULL THEN 1 END) AS plainRows " +
            "FROM workout_sessions s " +
            "INNER JOIN session_exercises se ON se.sessionId = s.id " +
            "LEFT JOIN exercise_sets st ON st.sessionExerciseId = se.id " +
            "WHERE s.status = 'COMPLETED' GROUP BY se.id",
    )
    fun observeExerciseTotals(): Flow<List<ExerciseTotalsRow>>

    // ---- exercises + sets of a session ----------------------------------------------------

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY orderIndex")
    fun observeSessionExercises(sessionId: String): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun getSessionExercises(sessionId: String): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: String): SessionExerciseEntity?

    @Query("SELECT * FROM session_exercises ORDER BY sessionId, orderIndex")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT id FROM session_exercises")
    suspend fun sessionExerciseIds(): List<String>

    @Query(
        "SELECT st.* FROM exercise_sets st INNER JOIN session_exercises se ON se.id = st.sessionExerciseId " +
            "WHERE se.sessionId = :sessionId ORDER BY se.orderIndex, st.orderIndex",
    )
    fun observeSetsOfSession(sessionId: String): Flow<List<ExerciseSetEntity>>

    @Query(
        "SELECT st.* FROM exercise_sets st INNER JOIN session_exercises se ON se.id = st.sessionExerciseId " +
            "WHERE se.sessionId = :sessionId ORDER BY se.orderIndex, st.orderIndex",
    )
    suspend fun getSetsOfSession(sessionId: String): List<ExerciseSetEntity>

    @Query("SELECT * FROM exercise_sets WHERE sessionExerciseId = :sessionExerciseId ORDER BY orderIndex")
    suspend fun getSets(sessionExerciseId: String): List<ExerciseSetEntity>

    @Query("SELECT * FROM exercise_sets WHERE id = :id")
    suspend fun getSet(id: String): ExerciseSetEntity?

    @Query("SELECT * FROM exercise_sets ORDER BY sessionExerciseId, orderIndex")
    suspend fun getAllSets(): List<ExerciseSetEntity>

    @Query("SELECT id FROM exercise_sets")
    suspend fun setIds(): List<String>

    // ---- comparisons --------------------------------------------------------------------------

    @Query(
        "SELECT se.id AS sessionExerciseId, s.localDate AS localDate, s.title AS title " +
            "FROM session_exercises se INNER JOIN workout_sessions s ON s.id = se.sessionId " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND s.id != :excludeSessionId " +
            "AND s.startedAt < :before " +
            "AND EXISTS (SELECT 1 FROM exercise_sets st WHERE st.sessionExerciseId = se.id " +
            "AND st.isCompleted = 1 AND st.setType != 'WARMUP') " +
            "ORDER BY CASE WHEN s.plannedWorkoutId = :plannedWorkoutId THEN 1 ELSE 0 END DESC, s.startedAt DESC LIMIT 1",
    )
    suspend fun findPrevious(
        exerciseId: String,
        plannedWorkoutId: String?,
        excludeSessionId: String,
        before: Long,
    ): PreviousRef?

    @Query(
        "SELECT * FROM workout_sessions WHERE status = 'COMPLETED' AND id != :id AND startedAt < :before " +
            "AND ((:plannedWorkoutId IS NOT NULL AND plannedWorkoutId = :plannedWorkoutId) " +
            "OR (:plannedWorkoutId IS NULL AND title = :title)) ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun findPreviousSameType(
        id: String,
        before: Long,
        plannedWorkoutId: String?,
        title: String,
    ): WorkoutSessionEntity?

    // ---- writes ----------------------------------------------------------------------------

    @Upsert
    suspend fun upsertSession(entity: WorkoutSessionEntity)

    @Upsert
    suspend fun upsertSessions(entities: List<WorkoutSessionEntity>)

    @Upsert
    suspend fun upsertSessionExercise(entity: SessionExerciseEntity)

    @Upsert
    suspend fun upsertSessionExercises(entities: List<SessionExerciseEntity>)

    @Upsert
    suspend fun upsertSets(entities: List<ExerciseSetEntity>)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: String)

    @Query("DELETE FROM exercise_sets WHERE id IN (:ids)")
    suspend fun deleteSets(ids: List<String>)

    @Query("DELETE FROM exercise_sets")
    suspend fun deleteAllSets()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteAllSessionExercises()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    // Field level updates keep concurrent edits (weight then reps) from overwriting each other.
    @Query("UPDATE exercise_sets SET weightKg = :value WHERE id = :id")
    suspend fun setWeight(id: String, value: Double?)

    @Query("UPDATE exercise_sets SET reps = :value WHERE id = :id")
    suspend fun setReps(id: String, value: Int?)

    @Query("UPDATE exercise_sets SET durationSec = :value WHERE id = :id")
    suspend fun setDuration(id: String, value: Int?)

    @Query("UPDATE exercise_sets SET rir = :value WHERE id = :id")
    suspend fun setRir(id: String, value: Int?)

    @Query("UPDATE exercise_sets SET rpe = :value WHERE id = :id")
    suspend fun setRpe(id: String, value: Double?)

    @Query("UPDATE exercise_sets SET notes = :value WHERE id = :id")
    suspend fun setSetNotes(id: String, value: String)

    @Query("UPDATE exercise_sets SET isFailure = :value WHERE id = :id")
    suspend fun setFailure(id: String, value: Boolean)

    @Query("UPDATE exercise_sets SET isCompleted = :done, completedAt = :at WHERE id = :id")
    suspend fun setCompleted(id: String, done: Boolean, at: Long?)

    @Query("UPDATE session_exercises SET notes = :value WHERE id = :id")
    suspend fun setExerciseNotes(id: String, value: String)

    @Query("UPDATE session_exercises SET skipped = :skipped, skipReason = :reason WHERE id = :id")
    suspend fun setSkipped(id: String, skipped: Boolean, reason: String)

    @Query("UPDATE session_exercises SET orderIndex = :index WHERE id = :id")
    suspend fun setSessionExerciseOrder(id: String, index: Int)

    @Query("UPDATE workout_sessions SET notes = :value, updatedAt = :now WHERE id = :id")
    suspend fun setSessionNotes(id: String, value: String, now: Long)

    @Query("UPDATE workout_sessions SET energyRating = :energy, moodRating = :mood, updatedAt = :now WHERE id = :id")
    suspend fun setRatings(id: String, energy: Int?, mood: Int?, now: Long)

    @Query(
        "UPDATE workout_sessions SET difficultyRating = :difficulty, qualityRating = :quality, sorenessRating = :soreness, " +
            "fatigueRating = :fatigue, sleepHours = :sleep, updatedAt = :now WHERE id = :id",
    )
    suspend fun setRecovery(id: String, difficulty: Int?, quality: Int?, soreness: Int?, fatigue: Int?, sleep: Double?, now: Long)

    @Query("UPDATE workout_sessions SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun setTitle(id: String, title: String, now: Long)

    @Query(
        "UPDATE workout_sessions SET startedAt = :startedAt, endedAt = :endedAt, localDate = :localDate, zoneId = :zoneId, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun setTimes(id: String, startedAt: Long, endedAt: Long?, localDate: String, zoneId: String, now: Long)

    @Query(
        "UPDATE workout_sessions SET status = 'COMPLETED', endedAt = :endedAt, restTotalMs = NULL, restEndsAt = NULL, " +
            "restPausedRemainingMs = NULL, restLabel = '', updatedAt = :now WHERE id = :id",
    )
    suspend fun markCompleted(id: String, endedAt: Long, now: Long)

    @Query(
        "UPDATE workout_sessions SET restTotalMs = :total, restEndsAt = :endsAt, restPausedRemainingMs = :paused, " +
            "restLabel = :label, updatedAt = :now WHERE id = :id",
    )
    suspend fun setRest(id: String, total: Long?, endsAt: Long?, paused: Long?, label: String, now: Long)

    /** Clears the rest timer only if it is still the one that ends at [endsAt]; returns rows changed (0 or 1). */
    @Query(
        "UPDATE workout_sessions SET restTotalMs = NULL, restEndsAt = NULL, restPausedRemainingMs = NULL, restLabel = '' " +
            "WHERE id = :id AND restEndsAt = :endsAt",
    )
    suspend fun clearRestIfEndsAt(id: String, endsAt: Long): Int

    @Query(
        "UPDATE workout_sessions SET restTotalMs = NULL, restEndsAt = NULL, restPausedRemainingMs = NULL, restLabel = '' " +
            "WHERE id = :id",
    )
    suspend fun clearRest(id: String)
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM body_measurements ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY measuredAt DESC")
    suspend fun getAll(): List<BodyMeasurementEntity>

    @Query("SELECT * FROM body_measurements WHERE id = :id")
    suspend fun get(id: String): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements WHERE weightKg IS NOT NULL ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatestWeight(): Flow<BodyMeasurementEntity?>

    @Query("SELECT id FROM body_measurements")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(entity: BodyMeasurementEntity)

    @Upsert
    suspend fun upsertAll(entities: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAll()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_events ORDER BY startsAt DESC")
    fun observeAll(): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_events ORDER BY startsAt DESC")
    suspend fun getAll(): List<ActivityEventEntity>

    @Query("SELECT id FROM activity_events")
    suspend fun ids(): List<String>

    @Query("SELECT * FROM activity_events WHERE id = :id")
    suspend fun get(id: String): ActivityEventEntity?

    @Upsert
    suspend fun upsert(entity: ActivityEventEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ActivityEventEntity>)

    @Query("DELETE FROM activity_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM activity_events")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: AppSettingsEntity)

    @Upsert
    suspend fun upsert(entity: AppSettingsEntity)

    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()
}


@Dao
interface GoalDao {
    @Query(
        "SELECT * FROM goals ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 WHEN 'ACHIEVED' THEN 2 ELSE 3 END, createdAt DESC",
    )
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY createdAt")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE id = :id")
    fun observe(id: String): Flow<GoalEntity?>

    @Query("SELECT id FROM goals")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(entity: GoalEntity)

    @Upsert
    suspend fun upsertAll(entities: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Query("SELECT * FROM goal_changes WHERE goalId = :goalId ORDER BY changedAt DESC")
    fun observeChanges(goalId: String): Flow<List<GoalChangeEntity>>

    @Query("SELECT * FROM goal_changes ORDER BY changedAt")
    suspend fun getAllChanges(): List<GoalChangeEntity>

    @Query("SELECT id FROM goal_changes")
    suspend fun changeIds(): List<String>

    @Upsert
    suspend fun upsertChange(entity: GoalChangeEntity)

    @Upsert
    suspend fun upsertChanges(entities: List<GoalChangeEntity>)

    @Query("DELETE FROM goal_changes")
    suspend fun deleteAllChanges()
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM progress_photos ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photos ORDER BY takenAt DESC")
    suspend fun getAll(): List<ProgressPhotoEntity>

    @Query("SELECT * FROM progress_photos WHERE id = :id")
    suspend fun get(id: String): ProgressPhotoEntity?

    @Query("SELECT id FROM progress_photos")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(entity: ProgressPhotoEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ProgressPhotoEntity>)

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM progress_photos")
    suspend fun deleteAll()
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM planned_overrides")
    fun observeOverrides(): Flow<List<PlannedOverrideEntity>>

    @Query("SELECT * FROM planned_overrides")
    suspend fun getOverrides(): List<PlannedOverrideEntity>

    @Query("SELECT * FROM planned_overrides WHERE plannedWorkoutId = :workoutId AND originalDate = :date")
    suspend fun findOverride(workoutId: String, date: String): PlannedOverrideEntity?

    @Query("SELECT id FROM planned_overrides")
    suspend fun overrideIds(): List<String>

    @Upsert
    suspend fun upsertOverride(entity: PlannedOverrideEntity)

    @Upsert
    suspend fun upsertOverrides(entities: List<PlannedOverrideEntity>)

    @Query("DELETE FROM planned_overrides WHERE plannedWorkoutId = :workoutId AND originalDate = :date")
    suspend fun deleteOverride(workoutId: String, date: String)

    @Query("DELETE FROM planned_overrides")
    suspend fun deleteAllOverrides()
}

@Dao
interface ProgressionDao {
    @Query("SELECT * FROM progression_decisions ORDER BY decidedAt")
    fun observeDecisions(): Flow<List<ProgressionDecisionEntity>>

    @Query("SELECT * FROM progression_decisions ORDER BY decidedAt")
    suspend fun getDecisions(): List<ProgressionDecisionEntity>

    @Query("SELECT id FROM progression_decisions")
    suspend fun decisionIds(): List<String>

    @Upsert
    suspend fun upsertDecision(entity: ProgressionDecisionEntity)

    @Upsert
    suspend fun upsertDecisions(entities: List<ProgressionDecisionEntity>)

    @Query("DELETE FROM progression_decisions")
    suspend fun deleteAllDecisions()

    @Query("SELECT * FROM exercise_prefs")
    fun observePrefs(): Flow<List<ExercisePrefsEntity>>

    @Query("SELECT * FROM exercise_prefs")
    suspend fun getPrefs(): List<ExercisePrefsEntity>

    @Query("SELECT * FROM exercise_prefs WHERE exerciseKey = :key")
    suspend fun getPref(key: String): ExercisePrefsEntity?

    @Query("SELECT exerciseKey FROM exercise_prefs")
    suspend fun prefKeys(): List<String>

    @Upsert
    suspend fun upsertPref(entity: ExercisePrefsEntity)

    @Upsert
    suspend fun upsertPrefs(entities: List<ExercisePrefsEntity>)

    @Query("DELETE FROM exercise_prefs WHERE exerciseKey = :key")
    suspend fun deletePref(key: String)

    @Query("DELETE FROM exercise_prefs")
    suspend fun deleteAllPrefs()
}

@Dao
interface CustomMetricDao {
    @Query("SELECT * FROM custom_metrics ORDER BY isArchived, sortOrder, createdAt")
    fun observeAll(): Flow<List<CustomMetricEntity>>

    @Query("SELECT * FROM custom_metrics ORDER BY sortOrder, createdAt")
    suspend fun getAll(): List<CustomMetricEntity>

    @Query("SELECT * FROM custom_metrics WHERE id = :id")
    suspend fun get(id: String): CustomMetricEntity?

    @Query("SELECT id FROM custom_metrics")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(entity: CustomMetricEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CustomMetricEntity>)

    @Query("DELETE FROM custom_metrics WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM custom_metrics")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM measurement_custom_values WHERE metricId = :metricId")
    suspend fun usage(metricId: String): Int

    @Query("SELECT * FROM measurement_custom_values")
    fun observeValues(): Flow<List<MeasurementCustomValueEntity>>

    @Query("SELECT * FROM measurement_custom_values")
    suspend fun getValues(): List<MeasurementCustomValueEntity>

    @Query("SELECT * FROM measurement_custom_values WHERE measurementId = :measurementId")
    suspend fun valuesOf(measurementId: String): List<MeasurementCustomValueEntity>

    @Upsert
    suspend fun upsertValues(entities: List<MeasurementCustomValueEntity>)

    @Query("DELETE FROM measurement_custom_values WHERE measurementId = :measurementId")
    suspend fun deleteValuesOf(measurementId: String)

    @Query("DELETE FROM measurement_custom_values")
    suspend fun deleteAllValues()
}
