package app.liftos.data.repo

import androidx.room.withTransaction
import app.liftos.data.TimeSource
import app.liftos.data.ZoneSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.analytics.SetCounting
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.logic.PreviousPerformance
import app.liftos.domain.logic.RestTimer
import app.liftos.domain.logic.SessionFactory
import app.liftos.domain.logic.SetEditing
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.SessionSource
import app.liftos.domain.model.SessionStatus
import app.liftos.domain.model.SetType
import app.liftos.domain.model.WorkoutDetail
import app.liftos.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** One row of the history list. */
data class HistoryItem(
    val session: WorkoutSession,
    val volumeKg: Double,
    val workingSets: Int,
    val exercises: Int,
)

sealed interface ToggleResult {
    /** The set cannot be completed yet (e.g. no reps). */
    data object Rejected : ToggleResult

    data class Completed(val restStarted: Boolean) : ToggleResult

    data object Uncompleted : ToggleResult
}

data class FinishSummary(val sessionId: String, val removedSets: Int, val removedExercises: Int)

class WorkoutRepository(
    private val db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val zones: ZoneSource = ZoneSource.Device,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val sessions = db.sessionDao()
    private val plans = db.planDao()
    private val exercises = db.exerciseDao()
    private val settings = db.settingsDao()

    // ---- observing -------------------------------------------------------------------------

    fun observeActive(): Flow<WorkoutSession?> = sessions.observeActiveSession().map { it?.toDomain() }

    fun observeLastCompleted(): Flow<WorkoutSession?> = sessions.observeLastCompleted().map { it?.toDomain() }

    fun observeCountBetween(from: String, to: String): Flow<Int> = sessions.observeCountBetween(from, to)

    fun observeCompletedRefs(since: String): Flow<List<app.liftos.data.db.CompletedRefRow>> = sessions.observeCompletedRefs(since)

    /** Full workout (header, exercises, sets) kept live in sync with the database. */
    fun observeDetail(sessionId: String): Flow<WorkoutDetail?> =
        combine(
            sessions.observeSession(sessionId),
            sessions.observeSessionExercises(sessionId),
            sessions.observeSetsOfSession(sessionId),
        ) { s, ex, sets ->
            if (s == null) null else assemble(s.toDomain(), ex.map { it.toDomain() }, sets.map { it.toDomain() })
        }

    suspend fun getDetail(sessionId: String): WorkoutDetail? {
        val s = sessions.getSession(sessionId)?.toDomain() ?: return null
        return assemble(
            s,
            sessions.getSessionExercises(sessionId).map { it.toDomain() },
            sessions.getSetsOfSession(sessionId).map { it.toDomain() },
        )
    }

    private fun assemble(
        s: WorkoutSession,
        ex: List<app.liftos.domain.model.SessionExercise>,
        sets: List<ExerciseSet>,
    ): WorkoutDetail {
        val bySe = sets.groupBy { it.sessionExerciseId }
        return WorkoutDetail(
            s,
            ex.sortedBy { it.orderIndex }.map { e -> ExerciseWithSets(e, (bySe[e.id] ?: emptyList()).sortedBy { it.orderIndex }) },
        )
    }

    fun observeHistory(): Flow<List<HistoryItem>> =
        combine(sessions.observeCompleted(), sessions.observeExerciseTotals()) { list, totals ->
            val bySession = totals.groupBy { it.sessionId }
            list.map { e ->
                val rows = bySession[e.id].orEmpty()
                val perExercise = rows.map { SetCounting.effective(it.isUnilateral, it.rightRows, it.leftRows, it.plainRows) }
                HistoryItem(e.toDomain(), rows.sumOf { it.volume }, perExercise.sum(), perExercise.count { it > 0 })
            }
        }

    // ---- previous data -----------------------------------------------------------------------

    private suspend fun previousFor(
        exerciseId: String,
        plannedWorkoutId: String?,
        excludeSessionId: String,
        before: Long,
    ): PreviousPerformance? {
        val ref = sessions.findPrevious(exerciseId, plannedWorkoutId, excludeSessionId, before) ?: return null
        return PreviousPerformance(exerciseId, ref.localDate, ref.title, sessions.getSets(ref.sessionExerciseId).map { it.toDomain() })
    }

    /** What the user did last time, per exercise of this workout (used for the "previous" hint). */
    suspend fun previousPerformance(sessionId: String): Map<String, PreviousPerformance> {
        val s = sessions.getSession(sessionId) ?: return emptyMap()
        val out = HashMap<String, PreviousPerformance>()
        for (se in sessions.getSessionExercises(sessionId)) {
            val exId = se.exerciseId ?: continue
            if (exId in out) continue
            previousFor(exId, s.plannedWorkoutId, sessionId, s.startedAt)?.let { out[exId] = it }
        }
        return out
    }

    suspend fun previousSameType(sessionId: String): WorkoutDetail? {
        val s = sessions.getSession(sessionId) ?: return null
        val prev = sessions.findPreviousSameType(s.id, s.startedAt, s.plannedWorkoutId, s.title) ?: return null
        return getDetail(prev.id)
    }

    // ---- starting and finishing ----------------------------------------------------------------

    /** Starts a workout from a plan day. If one is already active, that one is returned instead. */
    suspend fun startFromPlan(plannedWorkoutId: String): String = db.withTransaction {
        sessions.getActiveSession()?.let { return@withTransaction it.id }
        val workout = plans.getWorkout(plannedWorkoutId) ?: error("Unknown workout $plannedWorkoutId")
        val plan = plans.getPlan(workout.planId)?.toDomain()
        val planned = plans.getExercises(plannedWorkoutId).map { it.toDomain() }
        val library = exercises.getMany(planned.map { it.exerciseId }).map { it.toDomain() }.associateBy { it.id }
        val previous = HashMap<String, PreviousPerformance>()
        for (p in planned) {
            if (p.exerciseId in previous) continue
            previousFor(p.exerciseId, plannedWorkoutId, "", Long.MAX_VALUE)?.let { previous[p.exerciseId] = it }
        }
        val detail = SessionFactory.fromPlan(plan, workout.toDomain(), planned, library, previous, time.nowMs(), zones.zone(), ids)
        persist(detail)
        detail.session.id
    }

    suspend fun startBlank(title: String = "אימון חופשי"): String = db.withTransaction {
        sessions.getActiveSession()?.let { return@withTransaction it.id }
        val detail = SessionFactory.blank(title, time.nowMs(), zones.zone(), ids)
        persist(detail)
        detail.session.id
    }

    /**
     * Logs a workout that already happened. It is created as a finished workout with the plan's
     * structure pre-filled (sets not yet marked as done) and opened for editing.
     */
    suspend fun createPastWorkout(
        plannedWorkoutId: String?,
        title: String,
        startedAt: Long,
        durationMin: Int,
    ): String = db.withTransaction {
        val zone = zones.zone()
        val detail = if (plannedWorkoutId != null) {
            val workout = plans.getWorkout(plannedWorkoutId) ?: error("Unknown workout $plannedWorkoutId")
            val plan = plans.getPlan(workout.planId)?.toDomain()
            val planned = plans.getExercises(plannedWorkoutId).map { it.toDomain() }
            val library = exercises.getMany(planned.map { it.exerciseId }).map { it.toDomain() }.associateBy { it.id }
            val previous = HashMap<String, PreviousPerformance>()
            for (p in planned) {
                if (p.exerciseId in previous) continue
                previousFor(p.exerciseId, plannedWorkoutId, "", startedAt)?.let { previous[p.exerciseId] = it }
            }
            SessionFactory.fromPlan(plan, workout.toDomain(), planned, library, previous, startedAt, zone, ids)
        } else {
            SessionFactory.blank(title.ifBlank { "אימון" }, startedAt, zone, ids)
        }
        val now = time.nowMs()
        val s = detail.session.copy(
            title = title.ifBlank { detail.session.title },
            status = SessionStatus.COMPLETED,
            endedAt = startedAt + durationMin.coerceAtLeast(1) * 60_000L,
            source = SessionSource.MANUAL,
            createdAt = now, updatedAt = now,
        )
        persist(detail.copy(session = s))
        s.id
    }

    private suspend fun persist(detail: WorkoutDetail) {
        sessions.upsertSession(detail.session.toEntity())
        sessions.upsertSessionExercises(detail.exercises.map { it.exercise.toEntity() })
        sessions.upsertSets(detail.exercises.flatMap { it.sets }.map { it.toEntity() })
    }

    /**
     * Finishes the active workout. Sets that were never completed are dropped (and exercises
     * left without any set are removed unless they were deliberately skipped).
     */
    suspend fun finishWorkout(sessionId: String, dropIncompleteSets: Boolean = true): FinishSummary = db.withTransaction {
        var removedSets = 0
        var removedExercises = 0
        if (dropIncompleteSets) {
            for (se in sessions.getSessionExercises(sessionId)) {
                val sets = sessions.getSets(se.id)
                val incomplete = sets.filter { !it.isCompleted }
                if (incomplete.isNotEmpty()) {
                    sessions.deleteSets(incomplete.map { it.id })
                    removedSets += incomplete.size
                }
                val remaining = sets.size - incomplete.size
                if (remaining == 0 && !se.skipped) {
                    sessions.deleteSessionExercise(se.id)
                    removedExercises++
                } else if (incomplete.isNotEmpty()) {
                    sessions.upsertSets(SetEditing.renumber(sets.filter { it.isCompleted }.map { it.toDomain() }).map { it.toEntity() })
                }
            }
            sessions.getSessionExercises(sessionId).forEachIndexed { i, se -> sessions.setSessionExerciseOrder(se.id, i) }
        }
        sessions.markCompleted(sessionId, time.nowMs(), time.nowMs())
        FinishSummary(sessionId, removedSets, removedExercises)
    }

    suspend fun discardWorkout(sessionId: String) = sessions.deleteSession(sessionId)

    suspend fun deleteSession(sessionId: String) = sessions.deleteSession(sessionId)

    // ---- session level edits -------------------------------------------------------------------------

    suspend fun setSessionNotes(sessionId: String, notes: String) = sessions.setSessionNotes(sessionId, notes, time.nowMs())

    suspend fun setRatings(sessionId: String, energy: Int?, mood: Int?) =
        sessions.setRatings(sessionId, energy?.coerceIn(1, 5), mood?.coerceIn(1, 5), time.nowMs())

    /** Optional recovery data for a workout; any value may be null. Ratings are clamped to 1..5. */
    suspend fun setRecovery(
        sessionId: String,
        difficulty: Int?,
        quality: Int?,
        soreness: Int?,
        fatigue: Int?,
        sleepHours: Double?,
    ) = sessions.setRecovery(
        sessionId, difficulty?.coerceIn(1, 5), quality?.coerceIn(1, 5), soreness?.coerceIn(1, 5),
        fatigue?.coerceIn(1, 5), sleepHours?.takeIf { it in 0.0..24.0 }, time.nowMs(),
    )

    suspend fun setTitle(sessionId: String, title: String) {
        if (title.isNotBlank()) sessions.setTitle(sessionId, title.trim(), time.nowMs())
    }

    /** Changes start/end of a workout (history editing); the local date is recomputed in the current zone. */
    suspend fun setTimes(sessionId: String, startedAt: Long, endedAt: Long?) {
        val zone = zones.zone()
        val end = endedAt?.coerceAtLeast(startedAt)
        sessions.setTimes(sessionId, startedAt, end, TimeUtils.localDateString(startedAt, zone), zone.id, time.nowMs())
    }

    // ---- sets --------------------------------------------------------------------------------------------

    suspend fun setWeight(setId: String, value: Double?) = sessions.setWeight(setId, value?.takeIf { it >= 0 })

    suspend fun setReps(setId: String, value: Int?) = sessions.setReps(setId, value?.takeIf { it >= 0 })

    suspend fun setDuration(setId: String, value: Int?) = sessions.setDuration(setId, value?.takeIf { it >= 0 })

    suspend fun setRir(setId: String, value: Int?) = sessions.setRir(setId, value?.takeIf { it in 0..10 })

    suspend fun setRpe(setId: String, value: Double?) = sessions.setRpe(setId, value?.takeIf { it in 0.0..10.0 })

    suspend fun setSetNotes(setId: String, notes: String) = sessions.setSetNotes(setId, notes)

    suspend fun setFailure(setId: String, value: Boolean) = sessions.setFailure(setId, value)

    /**
     * Puts [weightKg] on every working set of the exercise that is not done yet (used when the user
     * accepts a progression suggestion or types a target weight). Completed sets are never touched.
     * Returns how many sets were changed.
     */
    suspend fun applyWeightToOpenSets(sessionExerciseId: String, weightKg: Double): Int = db.withTransaction {
        val open = sessions.getSets(sessionExerciseId).filter { !it.isCompleted && it.setType != SetType.WARMUP }
        open.forEach { sessions.setWeight(it.id, weightKg) }
        open.size
    }

    suspend fun addSet(sessionExerciseId: String, type: SetType = SetType.WORKING) {
        db.withTransaction {
            val se = sessions.getSessionExercise(sessionExerciseId) ?: return@withTransaction
            val current = sessions.getSets(sessionExerciseId).map { it.toDomain() }
            val updated = SetEditing.addSet(se.toDomain(), current, type, ids)
            sessions.upsertSets(updated.map { it.toEntity() })
        }
    }

    suspend fun deleteSet(setId: String) {
        db.withTransaction {
            val set = sessions.getSet(setId) ?: return@withTransaction
            val se = sessions.getSessionExercise(set.sessionExerciseId) ?: return@withTransaction
            val current = sessions.getSets(se.id).map { it.toDomain() }
            val updated = SetEditing.deleteSet(se.toDomain(), current, setId)
            val removed = current.map { it.id } - updated.map { it.id }.toSet()
            if (removed.isNotEmpty()) sessions.deleteSets(removed)
            sessions.upsertSets(updated.map { it.toEntity() })
        }
    }

    /** Changes a row between warm-up / working / drop; warm-ups are always kept first. */
    suspend fun changeSetType(setId: String, type: SetType) {
        db.withTransaction {
            val set = sessions.getSet(setId) ?: return@withTransaction
            val rows = sessions.getSets(set.sessionExerciseId).map { it.toDomain() }
                .map { if (it.id == setId) it.copy(setType = type) else it }
            val sorted = rows.sortedWith(compareBy({ if (it.setType == SetType.WARMUP) 0 else 1 }, { it.orderIndex }))
            sessions.upsertSets(SetEditing.renumber(sorted).map { it.toEntity() })
        }
    }

    /**
     * Marks a set done / not done. Completing a set can start the rest timer (when enabled in
     * settings). Returns [ToggleResult.Rejected] if the set has no reps/seconds yet.
     */
    suspend fun toggleSetCompleted(setId: String): ToggleResult = db.withTransaction {
        val setEntity = sessions.getSet(setId) ?: return@withTransaction ToggleResult.Rejected
        val se = sessions.getSessionExercise(setEntity.sessionExerciseId)?.toDomain() ?: return@withTransaction ToggleResult.Rejected
        val now = time.nowMs()
        if (setEntity.isCompleted) {
            sessions.setCompleted(setId, false, null)
            return@withTransaction ToggleResult.Uncompleted
        }
        val set = setEntity.toDomain()
        if (!SetEditing.canComplete(se, set)) return@withTransaction ToggleResult.Rejected
        sessions.setCompleted(setId, true, now)
        val autoRest = settings.get()?.autoStartRest ?: true
        val session = sessions.getSession(se.sessionId)
        val shouldRest = autoRest && session?.status == SessionStatus.ACTIVE &&
            set.setType != SetType.WARMUP && SetEditing.shouldStartRestAfter(se, set) && se.restSeconds > 0
        if (shouldRest) {
            val rest = RestTimer.start(now, se.restSeconds, se.nameHe)
            sessions.setRest(se.sessionId, rest.totalMs, rest.endsAt, null, rest.label, now)
        }
        ToggleResult.Completed(shouldRest)
    }

    // ---- exercises in a session --------------------------------------------------------------------

    suspend fun addExercise(sessionId: String, exercise: Exercise): String = db.withTransaction {
        val s = sessions.getSession(sessionId) ?: error("Unknown session")
        val order = sessions.getSessionExercises(sessionId).size
        val prefs = settings.get()?.toDomain() ?: app.liftos.domain.model.AppSettings()
        val previous = previousFor(exercise.id, s.plannedWorkoutId, sessionId, s.startedAt)
        val built = SessionFactory.adHocExercise(
            sessionId = sessionId, orderIndex = order, exercise = exercise, targetSets = 3,
            restSeconds = prefs.defaultRestFor(exercise.type), previous = previous, idGen = ids,
        )
        sessions.upsertSessionExercise(built.exercise.toEntity())
        sessions.upsertSets(built.sets.map { it.toEntity() })
        built.exercise.id
    }

    /**
     * Replaces the exercise in a slot. Sets already completed stay; the remaining sets are rebuilt
     * (and pre-filled) for the new exercise.
     */
    suspend fun replaceExercise(sessionExerciseId: String, newExercise: Exercise) {
        db.withTransaction {
            val old = sessions.getSessionExercise(sessionExerciseId)?.toDomain() ?: return@withTransaction
            val session = sessions.getSession(old.sessionId) ?: return@withTransaction
            val oldSets = sessions.getSets(sessionExerciseId).map { it.toDomain() }
            val completed = oldSets.filter { it.isCompleted }
            val incomplete = oldSets.filterNot { it.isCompleted }
            if (incomplete.isNotEmpty()) sessions.deleteSets(incomplete.map { it.id })

            val prefs = settings.get()?.toDomain() ?: app.liftos.domain.model.AppSettings()
            val previous = previousFor(newExercise.id, session.plannedWorkoutId, session.id, session.startedAt)
            val completedWorking = completed.count { it.setType != SetType.WARMUP }
            val targetSets = (old.plannedSets - (if (newExercise.isUnilateral) completedWorking / 2 else completedWorking)).coerceAtLeast(0)
            val (min, max) = SessionFactory.defaultRepRange(newExercise)
            val renamed = old.copy(
                exerciseId = newExercise.id, nameHe = newExercise.nameHe, nameEn = newExercise.nameEn,
                primaryMuscle = newExercise.primaryMuscle, trackingType = newExercise.trackingType,
                isUnilateral = newExercise.isUnilateral, repsMin = min, repsMax = max,
                restSeconds = prefs.defaultRestFor(newExercise.type),
            )
            sessions.upsertSessionExercise(renamed.toEntity())
            val fresh = SessionFactory.buildSets(
                renamed,
                app.liftos.domain.model.PlannedExercise(
                    id = "", plannedWorkoutId = "", exerciseId = newExercise.id, targetSets = targetSets,
                    repsMin = min, repsMax = max, restSeconds = renamed.restSeconds,
                ),
                previous, ids,
            )
            val all = SetEditing.renumber(completed + fresh)
            sessions.upsertSets(all.map { it.toEntity() })
        }
    }

    suspend fun removeExercise(sessionExerciseId: String) {
        db.withTransaction {
            val se = sessions.getSessionExercise(sessionExerciseId) ?: return@withTransaction
            sessions.deleteSessionExercise(sessionExerciseId)
            sessions.getSessionExercises(se.sessionId).forEachIndexed { i, e -> sessions.setSessionExerciseOrder(e.id, i) }
        }
    }

    suspend fun reorderExercises(sessionId: String, orderedIds: List<String>) {
        db.withTransaction { orderedIds.forEachIndexed { i, id -> sessions.setSessionExerciseOrder(id, i) } }
    }

    suspend fun setExerciseNotes(sessionExerciseId: String, notes: String) = sessions.setExerciseNotes(sessionExerciseId, notes)

    suspend fun setSkipped(sessionExerciseId: String, skipped: Boolean, reason: String = "") =
        sessions.setSkipped(sessionExerciseId, skipped, if (skipped) reason.trim() else "")

    // ---- rest timer ------------------------------------------------------------------------------------

    suspend fun startRest(sessionId: String, seconds: Int, label: String = "") {
        val now = time.nowMs()
        val rest = RestTimer.start(now, seconds, label)
        sessions.setRest(sessionId, rest.totalMs, rest.endsAt, null, rest.label, now)
    }

    suspend fun pauseRest(sessionId: String) = mutateRest(sessionId) { rest, now -> RestTimer.pause(rest, now) }

    suspend fun resumeRest(sessionId: String) = mutateRest(sessionId) { rest, now -> RestTimer.resume(rest, now) }

    suspend fun addRestSeconds(sessionId: String, seconds: Int) =
        mutateRest(sessionId) { rest, now -> RestTimer.addSeconds(rest, now, seconds) }

    suspend fun skipRest(sessionId: String) = sessions.clearRest(sessionId)

    /**
     * Atomically "claims" the end of a rest period so that exactly one of several competing
     * finishers (foreground service, backup alarm, UI) alerts the user. Returns true for the winner.
     */
    suspend fun claimRestFinish(sessionId: String, endsAt: Long): Boolean = sessions.clearRestIfEndsAt(sessionId, endsAt) == 1

    private suspend fun mutateRest(
        sessionId: String,
        change: (app.liftos.domain.logic.RestTimerState, Long) -> app.liftos.domain.logic.RestTimerState,
    ) {
        db.withTransaction {
            val s = sessions.getSession(sessionId)?.toDomain() ?: return@withTransaction
            val rest = s.rest ?: return@withTransaction
            val now = time.nowMs()
            val updated = change(rest, now)
            sessions.setRest(sessionId, updated.totalMs, updated.endsAt, updated.pausedRemainingMs, updated.label, now)
        }
    }

}
