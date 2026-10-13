package app.liftos.domain.logic

import app.liftos.domain.analytics.SetCounting
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.WorkoutDetail

/**
 * Volume load = weight x reps, summed over **completed, non-warm-up** sets. A left+right pair of a
 * unilateral exercise counts as one *set* but both sides count toward volume and reps.
 *
 * Bodyweight-only sets (no added weight) contribute 0 kg-reps; they are still counted in the
 * set and rep totals. Duration sets (plank) contribute no volume.
 */
object VolumeCalculator {

    /** A set counts toward training totals when it was completed and is not a warm-up. */
    fun counts(set: ExerciseSet): Boolean = set.isCompleted && set.setType != SetType.WARMUP

    fun setVolume(set: ExerciseSet): Double {
        if (!counts(set)) return 0.0
        val w = set.weightKg ?: return 0.0
        val r = set.reps ?: return 0.0
        return w * r
    }

    fun exerciseVolume(sets: List<ExerciseSet>): Double = sets.sumOf(::setVolume)

    fun sessionVolume(detail: WorkoutDetail): Double = detail.exercises.sumOf { exerciseVolume(it.sets) }

    fun totals(detail: WorkoutDetail): SessionTotals {
        var volume = 0.0
        var working = 0
        var warmups = 0
        var reps = 0
        var exercisesDone = 0
        for (ex in detail.exercises) {
            var right = 0
            var left = 0
            var plain = 0
            for (s in ex.sets) {
                if (!s.isCompleted) continue
                if (s.setType == SetType.WARMUP) {
                    warmups++
                    continue
                }
                when (s.side) {
                    Side.RIGHT -> right++
                    Side.LEFT -> left++
                    null -> plain++
                }
                reps += s.reps ?: 0
                volume += setVolume(s)
            }
            // A right+left pair is one set; volume and reps still count both sides.
            val sets = SetCounting.effective(ex.exercise.isUnilateral, right, left, plain)
            working += sets
            if (sets > 0) exercisesDone++
        }
        return SessionTotals(volume, working, warmups, reps, exercisesDone)
    }

    /** Best working set by weight (ties broken by reps), or null if none was completed. */
    fun topSet(sets: List<ExerciseSet>): ExerciseSet? =
        sets.filter(::counts)
            .filter { it.weightKg != null && it.reps != null }
            .maxWithOrNull(compareBy<ExerciseSet>({ it.weightKg }, { it.reps }))

    fun workingSets(ex: ExerciseWithSets): List<ExerciseSet> = ex.sets.filter(::counts)
}

data class SessionTotals(
    val volumeKg: Double,
    val workingSets: Int,
    val warmupSets: Int,
    val totalReps: Int,
    val exercisesPerformed: Int,
)
