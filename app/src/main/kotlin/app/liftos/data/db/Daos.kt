package app.liftos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Result row of "find the previous performance of an exercise". */
data class PreviousRef(
    val sessionExerciseId: String,
    val localDate: String,
    val title: String,
)

/** Per-workout totals for the history list. */
data class SessionTotalsRow(
    val sessionId: String,
    val volume: Double,
    val workingSets: Int,
    val exercises: Int,
)

data class CompletedRefRow(
    val plannedWorkoutId: String?,
    val localDate: String,
)

data class SetValueRow(
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
)

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
        "SELECT s.id AS sessionId, " +
            "COALESCE(SUM(CASE WHEN st.isCompleted = 1 AND st.setType != 'WARMUP' THEN st.weightKg * st.reps ELSE 0 END), 0) AS volume, " +
            "COUNT(CASE WHEN st.isCompleted = 1 AND st.setType != 'WARMUP' THEN 1 END) AS workingSets, " +
            "COUNT(DISTINCT CASE WHEN st.isCompleted = 1 AND st.setType != 'WARMUP' THEN se.id END) AS exercises " +
            "FROM workout_sessions s " +
            "LEFT JOIN session_exercises se ON se.sessionId = s.id " +
            "LEFT JOIN exercise_sets st ON st.sessionExerciseId = se.id " +
            "WHERE s.status = 'COMPLETED' GROUP BY s.id",
    )
    fun observeTotals(): Flow<List<SessionTotalsRow>>

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

    @Query(
        "SELECT se.exerciseId AS exerciseId, st.weightKg AS weightKg, st.reps AS reps " +
            "FROM exercise_sets st INNER JOIN session_exercises se ON se.id = st.sessionExerciseId " +
            "INNER JOIN workout_sessions s ON s.id = se.sessionId " +
            "WHERE s.status = 'COMPLETED' AND s.id != :excludeSessionId AND s.startedAt < :before " +
            "AND st.isCompleted = 1 AND st.setType != 'WARMUP' AND st.weightKg > 0 AND st.reps > 0 " +
            "AND se.exerciseId IN (:exerciseIds)",
    )
    suspend fun getHistoricalSets(exerciseIds: List<String>, excludeSessionId: String, before: Long): List<SetValueRow>

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
