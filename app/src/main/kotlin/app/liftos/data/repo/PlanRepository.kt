package app.liftos.data.repo

import androidx.room.withTransaction
import app.liftos.data.TimeSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.logic.SessionFactory
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.PlanDetail
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.PlannedWorkoutDetail
import app.liftos.domain.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlanRepository(
    private val db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.planDao()
    private val settings = db.settingsDao()

    // ---- reading ---------------------------------------------------------------------------

    fun observePlans(): Flow<List<WorkoutPlan>> = dao.observePlans().map { l -> l.map { it.toDomain() } }

    fun observePlanStats(): Flow<List<app.liftos.data.db.PlanStatsRow>> = dao.observePlanStats()

    fun observePlanDetail(planId: String): Flow<PlanDetail?> =
        combine(dao.observePlan(planId), dao.observeWorkouts(planId), dao.observeAllExercisesOfPlan(planId)) { plan, workouts, exercises ->
            plan?.let { assemble(it.toDomain(), workouts.map { w -> w.toDomain() }, exercises.map { e -> e.toDomain() }) }
        }

    fun observeWorkoutDetail(workoutId: String): Flow<PlannedWorkoutDetail?> =
        combine(dao.observeWorkout(workoutId), dao.observeExercises(workoutId)) { w, e ->
            w?.let { PlannedWorkoutDetail(it.toDomain(), e.map { x -> x.toDomain() }) }
        }

    suspend fun getPlanDetail(planId: String): PlanDetail? {
        val plan = dao.getPlan(planId)?.toDomain() ?: return null
        val workouts = dao.getWorkouts(planId).map { it.toDomain() }
        val exercises = workouts.flatMap { w -> dao.getExercises(w.id).map { it.toDomain() } }
        return assemble(plan, workouts, exercises)
    }

    private fun assemble(plan: WorkoutPlan, workouts: List<PlannedWorkout>, exercises: List<PlannedExercise>) = PlanDetail(
        plan = plan,
        workouts = workouts.sortedBy { it.orderIndex }.map { w ->
            PlannedWorkoutDetail(w, exercises.filter { it.plannedWorkoutId == w.id }.sortedBy { it.orderIndex })
        },
    )

    // ---- plans -------------------------------------------------------------------------------

    suspend fun createPlan(name: String, description: String = "", goal: String = "", workoutsPerWeek: Int = 0): String {
        val now = time.nowMs()
        val id = ids.newId()
        dao.upsertPlan(
            WorkoutPlan(
                id = id, name = name.trim().ifBlank { "תוכנית חדשה" }, description = description, goal = goal,
                workoutsPerWeek = workoutsPerWeek, sortOrder = dao.planCount(), createdAt = now, updatedAt = now,
            ).toEntity(),
        )
        return id
    }

    suspend fun updatePlan(plan: WorkoutPlan) {
        val existing = dao.getPlan(plan.id) ?: return
        dao.upsertPlan(plan.copy(name = plan.name.trim().ifBlank { existing.name }, createdAt = existing.createdAt, updatedAt = time.nowMs()).toEntity())
    }

    /** Deleting a plan never touches workout history (sessions only keep a soft reference). */
    suspend fun deletePlan(planId: String) {
        db.withTransaction {
            dao.deletePlan(planId)
            val s = settings.get()
            if (s != null && s.activePlanId == planId) settings.upsert(s.copy(activePlanId = null))
        }
    }

    /** Deep copy of a plan (new ids everywhere). Used for "duplicate", "save as template" and "use template". */
    suspend fun duplicatePlan(planId: String, newName: String? = null, asTemplate: Boolean = false): String? = db.withTransaction {
        val src = getPlanDetail(planId) ?: return@withTransaction null
        val now = time.nowMs()
        val newPlanId = ids.newId()
        dao.upsertPlan(
            src.plan.copy(
                id = newPlanId,
                name = newName?.trim()?.takeIf { it.isNotEmpty() } ?: (src.plan.name + " (עותק)"),
                isTemplate = asTemplate,
                sortOrder = dao.planCount(),
                createdAt = now, updatedAt = now,
            ).toEntity(),
        )
        src.workouts.forEach { w ->
            val newWorkoutId = ids.newId()
            dao.upsertWorkout(w.workout.copy(id = newWorkoutId, planId = newPlanId).toEntity())
            dao.upsertPlannedExercises(w.exercises.map { it.copy(id = ids.newId(), plannedWorkoutId = newWorkoutId).toEntity() })
        }
        newPlanId
    }

    suspend fun saveAsTemplate(planId: String): String? {
        val name = dao.getPlan(planId)?.name ?: return null
        return duplicatePlan(planId, "$name (תבנית)", asTemplate = true)
    }

    suspend fun createFromTemplate(templateId: String): String? {
        val name = dao.getPlan(templateId)?.name?.removeSuffix(" (תבנית)") ?: return null
        return duplicatePlan(templateId, name, asTemplate = false)
    }

    suspend fun setActivePlan(planId: String?) {
        db.withTransaction {
            val s = settings.get() ?: app.liftos.domain.model.AppSettings().toEntity()
            settings.upsert(s.copy(activePlanId = planId))
        }
    }

    // ---- workouts (training days) ---------------------------------------------------------

    suspend fun addWorkout(planId: String, name: String, weekday: Int? = null): String {
        val id = ids.newId()
        dao.upsertWorkout(
            PlannedWorkout(
                id = id, planId = planId, name = name.trim().ifBlank { "אימון חדש" },
                orderIndex = dao.getWorkouts(planId).size, weekday = weekday,
            ).toEntity(),
        )
        touchPlan(planId)
        return id
    }

    suspend fun updateWorkout(workout: PlannedWorkout) {
        dao.upsertWorkout(workout.copy(name = workout.name.trim().ifBlank { "אימון" }).toEntity())
        touchPlan(workout.planId)
    }

    suspend fun deleteWorkout(workoutId: String) {
        val planId = dao.getWorkout(workoutId)?.planId
        dao.deleteWorkout(workoutId)
        planId?.let { reindexWorkouts(it) }
    }

    suspend fun duplicateWorkout(workoutId: String): String? = db.withTransaction {
        val w = dao.getWorkout(workoutId)?.toDomain() ?: return@withTransaction null
        val newId = ids.newId()
        dao.upsertWorkout(w.copy(id = newId, name = w.name + " (עותק)", orderIndex = dao.getWorkouts(w.planId).size, weekday = null).toEntity())
        dao.upsertPlannedExercises(dao.getExercises(workoutId).map { it.toDomain().copy(id = ids.newId(), plannedWorkoutId = newId).toEntity() })
        touchPlan(w.planId)
        newId
    }

    suspend fun reorderWorkouts(planId: String, orderedIds: List<String>) {
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setWorkoutOrder(id, i) } }
        touchPlan(planId)
    }

    private suspend fun reindexWorkouts(planId: String) {
        db.withTransaction { dao.getWorkouts(planId).forEachIndexed { i, w -> dao.setWorkoutOrder(w.id, i) } }
    }

    // ---- planned exercises --------------------------------------------------------------------

    suspend fun addExercise(workoutId: String, exercise: Exercise, defaultRestSec: Int): String = db.withTransaction {
        val id = ids.newId()
        val (min, max) = SessionFactory.defaultRepRange(exercise)
        dao.upsertPlannedExercise(
            PlannedExercise(
                id = id, plannedWorkoutId = workoutId, exerciseId = exercise.id,
                orderIndex = dao.getExercises(workoutId).size, targetSets = 3,
                repsMin = min, repsMax = max, restSeconds = defaultRestSec,
            ).toEntity(),
        )
        dao.getWorkout(workoutId)?.planId?.let { touchPlan(it) }
        id
    }

    suspend fun updatePlannedExercise(p: PlannedExercise) {
        val fixed = p.copy(
            targetSets = p.targetSets.coerceIn(1, 30),
            repsMin = p.repsMin.coerceAtLeast(1),
            repsMax = p.repsMax.coerceAtLeast(p.repsMin.coerceAtLeast(1)),
            restSeconds = p.restSeconds.coerceIn(0, 3600),
            warmupSets = p.warmupSets.coerceIn(0, 10),
        )
        dao.upsertPlannedExercise(fixed.toEntity())
        dao.getWorkout(p.plannedWorkoutId)?.planId?.let { touchPlan(it) }
    }

    suspend fun deletePlannedExercise(id: String) {
        val p = dao.getPlannedExercise(id) ?: return
        db.withTransaction {
            dao.deletePlannedExercise(id)
            dao.getExercises(p.plannedWorkoutId).forEachIndexed { i, e -> dao.setExerciseOrder(e.id, i) }
        }
        dao.getWorkout(p.plannedWorkoutId)?.planId?.let { touchPlan(it) }
    }

    /** Duplicates an exercise row right below the original. */
    suspend fun duplicatePlannedExercise(id: String): String? = db.withTransaction {
        val src = dao.getPlannedExercise(id)?.toDomain() ?: return@withTransaction null
        val list = dao.getExercises(src.plannedWorkoutId).map { it.toDomain() }.toMutableList()
        val copy = src.copy(id = ids.newId())
        list.add(list.indexOfFirst { it.id == id } + 1, copy)
        dao.upsertPlannedExercises(list.mapIndexed { i, e -> e.copy(orderIndex = i).toEntity() })
        dao.getWorkout(src.plannedWorkoutId)?.planId?.let { touchPlan(it) }
        copy.id
    }

    /** Swaps the exercise but keeps sets/reps/rest/notes of the row. */
    suspend fun swapExercise(plannedExerciseId: String, newExercise: Exercise) {
        val p = dao.getPlannedExercise(plannedExerciseId)?.toDomain() ?: return
        dao.upsertPlannedExercise(p.copy(exerciseId = newExercise.id).toEntity())
        dao.getWorkout(p.plannedWorkoutId)?.planId?.let { touchPlan(it) }
    }

    suspend fun reorderPlannedExercises(workoutId: String, orderedIds: List<String>) {
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setExerciseOrder(id, i) } }
        dao.getWorkout(workoutId)?.planId?.let { touchPlan(it) }
    }

    private suspend fun touchPlan(planId: String) {
        val p = dao.getPlan(planId) ?: return
        dao.upsertPlan(p.copy(updatedAt = time.nowMs()))
    }
}
