package app.liftos.domain.logic

import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.SessionSource
import app.liftos.domain.model.SessionStatus
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType
import app.liftos.domain.model.WorkoutDetail
import app.liftos.domain.model.WorkoutPlan
import app.liftos.domain.model.WorkoutSession
import java.time.ZoneId

/** What the user did last time for one exercise (working sets only, in order). */
data class PreviousPerformance(
    val exerciseId: String,
    /** ISO local date of the session the data comes from. */
    val localDate: String,
    val sessionTitle: String,
    val sets: List<ExerciseSet>,
)

/**
 * Builds a new workout from a plan day. The session receives *snapshots* of the plan and exercise
 * data, so later plan edits can never rewrite history. Sets are pre-filled from the previous
 * performance but are never marked as completed.
 */
object SessionFactory {

    fun fromPlan(
        plan: WorkoutPlan?,
        workout: PlannedWorkout,
        planned: List<PlannedExercise>,
        library: Map<String, Exercise>,
        previous: Map<String, PreviousPerformance>,
        nowMs: Long,
        zone: ZoneId,
        idGen: IdGenerator = IdGenerator.Random,
    ): WorkoutDetail {
        val sessionId = idGen.newId()
        val session = WorkoutSession(
            id = sessionId,
            planId = plan?.id,
            plannedWorkoutId = workout.id,
            planName = plan?.name.orEmpty(),
            title = workout.name,
            status = SessionStatus.ACTIVE,
            startedAt = nowMs,
            localDate = TimeUtils.localDateString(nowMs, zone),
            zoneId = zone.id,
            source = SessionSource.LIVE,
            createdAt = nowMs,
            updatedAt = nowMs,
        )
        val exercises = planned.sortedBy { it.orderIndex }.mapIndexedNotNull { index, p ->
            val ex = library[p.exerciseId] ?: return@mapIndexedNotNull null
            buildExercise(sessionId, index, p, ex, previous[p.exerciseId], idGen)
        }
        return WorkoutDetail(session, exercises)
    }

    /** Starts an empty ad-hoc workout (no plan). */
    fun blank(title: String, nowMs: Long, zone: ZoneId, idGen: IdGenerator = IdGenerator.Random): WorkoutDetail {
        val session = WorkoutSession(
            id = idGen.newId(),
            title = title,
            startedAt = nowMs,
            localDate = TimeUtils.localDateString(nowMs, zone),
            zoneId = zone.id,
            createdAt = nowMs,
            updatedAt = nowMs,
        )
        return WorkoutDetail(session, emptyList())
    }

    private fun buildExercise(
        sessionId: String,
        index: Int,
        p: PlannedExercise,
        ex: Exercise,
        prev: PreviousPerformance?,
        idGen: IdGenerator,
    ): ExerciseWithSets {
        val seId = idGen.newId()
        val se = SessionExercise(
            id = seId,
            sessionId = sessionId,
            exerciseId = ex.id,
            plannedExerciseId = p.id,
            nameHe = ex.nameHe,
            nameEn = ex.nameEn,
            primaryMuscle = ex.primaryMuscle,
            trackingType = ex.trackingType,
            isUnilateral = ex.isUnilateral,
            orderIndex = index,
            plannedSets = p.targetSets,
            repsMin = p.repsMin,
            repsMax = p.repsMax,
            targetWeightKg = p.targetWeightKg,
            targetRir = p.targetRir,
            targetRpe = p.targetRpe,
            restSeconds = p.restSeconds,
            notes = p.notes,
            supersetGroup = p.supersetGroup,
        )
        return ExerciseWithSets(se, buildSets(se, p, prev, idGen))
    }

    internal fun buildSets(
        se: SessionExercise,
        p: PlannedExercise,
        prev: PreviousPerformance?,
        idGen: IdGenerator,
    ): List<ExerciseSet> {
        val rows = mutableListOf<ExerciseSet>()
        val sides: List<Side?> = if (se.isUnilateral) listOf(Side.RIGHT, Side.LEFT) else listOf(null)
        val prevWorking = prev?.sets.orEmpty()
            .filter { it.setType != SetType.WARMUP }
            .sortedBy { it.orderIndex }

        repeat(p.warmupSets.coerceAtLeast(0)) {
            sides.forEach { side ->
                rows += ExerciseSet(
                    id = idGen.newId(),
                    sessionExerciseId = se.id,
                    setType = SetType.WARMUP,
                    side = side,
                    orderIndex = rows.size,
                )
            }
        }

        for (k in 0 until p.targetSets.coerceAtLeast(0)) {
            for (side in sides) {
                val source = pickPrevious(prevWorking, k, side)
                rows += ExerciseSet(
                    id = idGen.newId(),
                    sessionExerciseId = se.id,
                    orderIndex = rows.size,
                    setType = SetType.WORKING,
                    side = side,
                    weightKg = if (se.trackingType == TrackingType.DURATION) null else source?.weightKg ?: p.targetWeightKg,
                    reps = if (se.trackingType == TrackingType.DURATION) null else source?.reps,
                    durationSec = if (se.trackingType == TrackingType.DURATION) source?.durationSec else null,
                    rir = source?.rir ?: p.targetRir,
                    rpe = source?.rpe ?: p.targetRpe,
                )
            }
        }
        return rows
    }

    /**
     * Picks the previous row to copy values from: the same set index (and side) if it existed,
     * otherwise the last previous row for that side, otherwise null.
     */
    private fun pickPrevious(prevWorking: List<ExerciseSet>, k: Int, side: Side?): ExerciseSet? {
        if (prevWorking.isEmpty()) return null
        val sameSide = if (side != null) prevWorking.filter { it.side == side } else prevWorking
        val pool = sameSide.ifEmpty { prevWorking }
        return pool.getOrNull(k) ?: pool.last()
    }
}
