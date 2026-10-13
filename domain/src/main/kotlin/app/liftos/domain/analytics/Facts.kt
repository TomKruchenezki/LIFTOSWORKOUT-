package app.liftos.domain.analytics

import app.liftos.domain.logic.OneRepMax
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType
import java.time.LocalDate

/** Identity of an exercise across sessions: the library id, or a name based key for ad-hoc exercises. */
object ExerciseKey {
    fun of(exerciseId: String?, nameEn: String, nameHe: String): String =
        exerciseId ?: ("name:" + nameEn.trim().lowercase().ifBlank { nameHe.trim() })
}

/**
 * One **completed** set of a **completed** session, flattened with everything analytics needs.
 * Analytics never reads the database directly: the app hands over a list of these, which keeps
 * every calculation pure and testable. Warm-up rows are included (so they can be shown) but are
 * excluded from every training metric via [isWorking].
 */
data class SetFact(
    val sessionId: String,
    val sessionTitle: String,
    val startedAt: Long,
    /** Local calendar date (ISO yyyy-MM-dd) of the workout. */
    val localDate: String,
    val sessionExerciseId: String,
    val exerciseId: String?,
    val exerciseName: String,
    val exerciseNameEn: String = "",
    val primaryMuscle: MuscleGroup = MuscleGroup.OTHER,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val isUnilateral: Boolean = false,
    val exerciseOrder: Int = 0,
    val setId: String,
    val setOrder: Int = 0,
    val setType: SetType = SetType.WORKING,
    val side: Side? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val rir: Int? = null,
    val rpe: Double? = null,
    val isFailure: Boolean = false,
    val completedAt: Long? = null,
    /** Plan targets snapshotted into the workout (used by the progression engine). */
    val plannedSets: Int = 0,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    /** From the exercise library; null when the exercise was deleted from it. */
    val equipment: Equipment? = null,
    val exerciseType: ExerciseType? = null,
) {
    val key: String get() = ExerciseKey.of(exerciseId, exerciseNameEn, exerciseName)
    val date: LocalDate get() = TimeUtils.parseDate(localDate) ?: LocalDate.EPOCH
    val isWorking: Boolean get() = setType != SetType.WARMUP

    /** Working sets that follow the planned weight; drop sets intentionally use lighter loads. */
    val isStraightSet: Boolean get() = setType == SetType.WORKING

    /** Weight x reps for working sets with both values; duration exercises have no volume. */
    val volumeKg: Double
        get() {
            if (!isWorking || trackingType == TrackingType.DURATION) return 0.0
            val w = weightKg ?: return 0.0
            val r = reps ?: return 0.0
            return w * r
        }

    /** Estimated 1RM (Epley), only for loaded sets of 1..12 reps. Null otherwise. */
    val estimatedOneRm: Double?
        get() {
            if (!isWorking || trackingType == TrackingType.DURATION) return null
            val w = weightKg ?: return null
            val r = reps ?: return null
            return OneRepMax.epley(w, r)
        }

    /** True when the set carries an external load (weight above zero). */
    val isLoaded: Boolean get() = (weightKg ?: 0.0) > 0.0
}

/** A completed workout reduced to what analytics needs. */
data class SessionFact(
    val id: String,
    val title: String,
    val planName: String = "",
    val plannedWorkoutId: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val localDate: String,
    val energyRating: Int? = null,
    val moodRating: Int? = null,
    val difficultyRating: Int? = null,
    val qualityRating: Int? = null,
    val sorenessRating: Int? = null,
    val fatigueRating: Int? = null,
    val sleepHours: Double? = null,
    val notes: String = "",
) {
    val date: LocalDate get() = TimeUtils.parseDate(localDate) ?: LocalDate.EPOCH
    val durationMs: Long? get() = endedAt?.let { (it - startedAt).coerceAtLeast(0) }
}

/**
 * How many "sets" a group of rows represents. A unilateral set is stored as a right row and a
 * left row; it counts as ONE set (each side got that set), exactly like a bilateral exercise.
 * Volume (kg x reps) still sums both sides, because that is the real tonnage lifted.
 */
object SetCounting {
    /** Sets for already counted rows: a right+left pair is one set, so a unilateral exercise counts max(right, left). */
    fun effective(unilateral: Boolean, right: Int, left: Int, plain: Int): Int =
        if (unilateral) maxOf(right, left) + plain else right + left + plain

    fun effectiveSets(rows: List<SetFact>): Int {
        if (rows.isEmpty()) return 0
        val unilateral = rows.first().isUnilateral
        if (!unilateral) return rows.size
        val right = rows.count { it.side == Side.RIGHT }
        val left = rows.count { it.side == Side.LEFT }
        val noSide = rows.count { it.side == null }
        return maxOf(right, left) + noSide
    }

    /** Effective working sets (warm-ups excluded) over any mix of exercises and sessions. */
    fun effectiveWorkingSets(facts: List<SetFact>): Int =
        facts.filter { it.isWorking }
            .groupBy { it.sessionExerciseId }
            .values
            .sumOf { effectiveSets(it) }
}
