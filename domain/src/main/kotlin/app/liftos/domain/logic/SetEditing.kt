package app.liftos.domain.logic

import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType

/** Display label for a set row, e.g. number "2" with side RIGHT, or "ח1" for a warm-up. */
data class SetLabel(val number: String, val side: Side?)

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { java.util.UUID.randomUUID().toString() }
    }
}

/**
 * Pure helpers for manipulating the ordered set rows of one exercise. Repositories call these
 * and persist the returned lists, so the rules live in one tested place.
 */
object SetEditing {

    /** Rows are ordered: warm-ups first (in order), then working/drop rows. */
    private fun sortedRows(sets: List<ExerciseSet>): List<ExerciseSet> =
        sets.sortedBy { it.orderIndex }

    fun renumber(sets: List<ExerciseSet>): List<ExerciseSet> =
        sets.mapIndexed { i, s -> if (s.orderIndex == i) s else s.copy(orderIndex = i) }

    /**
     * Adds one set (two rows - right and left - for unilateral exercises).
     * New working rows copy weight/reps/effort from the last row of the same kind, so adding a
     * set during a workout is a single tap. Warm-ups are inserted after the last warm-up.
     */
    fun addSet(
        exercise: SessionExercise,
        existing: List<ExerciseSet>,
        type: SetType = SetType.WORKING,
        idGen: IdGenerator = IdGenerator.Random,
    ): List<ExerciseSet> {
        val rows = sortedRows(existing)
        val template = rows.lastOrNull { (it.setType == SetType.WARMUP) == (type == SetType.WARMUP) }
        val sides: List<Side?> = if (exercise.isUnilateral) listOf(Side.RIGHT, Side.LEFT) else listOf(null)
        val newRows = sides.map { side ->
            val sideTemplate = rows.lastOrNull {
                (it.setType == SetType.WARMUP) == (type == SetType.WARMUP) && it.side == side
            } ?: template
            ExerciseSet(
                id = idGen.newId(),
                sessionExerciseId = exercise.id,
                setType = type,
                side = side,
                weightKg = sideTemplate?.weightKg,
                reps = sideTemplate?.reps,
                durationSec = sideTemplate?.durationSec,
                rir = sideTemplate?.rir,
                rpe = sideTemplate?.rpe,
            )
        }
        val insertAt = if (type == SetType.WARMUP) {
            val lastWarmup = rows.indexOfLast { it.setType == SetType.WARMUP }
            lastWarmup + 1
        } else {
            rows.size
        }
        val result = rows.toMutableList().apply { addAll(insertAt, newRows) }
        return renumber(result)
    }

    /**
     * Removes a set. For unilateral exercises the matching row on the other side (same
     * number) is removed too, so the right/left rows always stay in pairs.
     */
    fun deleteSet(exercise: SessionExercise, existing: List<ExerciseSet>, setId: String): List<ExerciseSet> {
        val rows = sortedRows(existing)
        val target = rows.firstOrNull { it.id == setId } ?: return rows
        val toRemove = mutableSetOf(target.id)
        if (exercise.isUnilateral && target.side != null) {
            val labels = labels(exercise, rows)
            val number = labels[target.id]?.number
            rows.filter { labels[it.id]?.number == number && it.setType == target.setType }
                .forEach { toRemove += it.id }
        }
        return renumber(rows.filterNot { it.id in toRemove })
    }

    /** Maps each set id to its display label. */
    fun labels(exercise: SessionExercise, sets: List<ExerciseSet>): Map<String, SetLabel> {
        val result = LinkedHashMap<String, SetLabel>()
        var warmupNo = 0
        var workNo = 0
        for (s in sortedRows(sets)) {
            if (s.setType == SetType.WARMUP) {
                if (!exercise.isUnilateral || s.side != Side.LEFT) warmupNo++
                result[s.id] = SetLabel("ח$warmupNo", s.side)
            } else {
                if (!exercise.isUnilateral || s.side != Side.LEFT) workNo++
                result[s.id] = SetLabel(workNo.toString(), s.side)
            }
        }
        return result
    }

    /** A set can be marked done once it has the value that defines it (reps or seconds). */
    fun canComplete(exercise: SessionExercise, set: ExerciseSet): Boolean = when (exercise.trackingType) {
        TrackingType.DURATION -> (set.durationSec ?: 0) > 0
        else -> (set.reps ?: 0) > 0
    }

    /**
     * Whether finishing a set should start the rest timer. For unilateral exercises we rest after
     * the left side (the second row of the pair), not between the two sides.
     */
    fun shouldStartRestAfter(exercise: SessionExercise, set: ExerciseSet): Boolean =
        !(exercise.isUnilateral && set.side == Side.RIGHT)

    fun withCompletion(set: ExerciseSet, completed: Boolean, nowMs: Long): ExerciseSet =
        set.copy(isCompleted = completed, completedAt = if (completed) nowMs else null)
}
