package app.liftos.domain.logic

import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.WorkoutDetail
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Verdict { NEW, UP, SAME, DOWN }

data class ExerciseComparison(
    val nameHe: String,
    val current: ExerciseSet?,
    val previous: ExerciseSet?,
    val volumeDeltaKg: Double,
    val verdict: Verdict,
)

data class SessionComparison(
    val previousSessionId: String,
    val previousDate: String,
    val volumeDeltaKg: Double,
    /** Percent change vs previous volume; null when the previous volume was zero. */
    val volumePercent: Double?,
    val workingSetsDelta: Int,
    val durationDeltaMs: Long?,
    val exercises: List<ExerciseComparison>,
)

object SessionComparator {

    fun compare(current: WorkoutDetail, previous: WorkoutDetail): SessionComparison {
        val curTotals = VolumeCalculator.totals(current)
        val prevTotals = VolumeCalculator.totals(previous)
        val prevByKey = previous.exercises.associateBy { key(it) }
        val comparisons = current.exercises.map { ce ->
            val pe = prevByKey[key(ce)]
            val curTop = VolumeCalculator.topSet(ce.sets)
            val prevTop = pe?.let { VolumeCalculator.topSet(it.sets) }
            val curVol = VolumeCalculator.exerciseVolume(ce.sets)
            val prevVol = pe?.let { VolumeCalculator.exerciseVolume(it.sets) } ?: 0.0
            ExerciseComparison(
                nameHe = ce.exercise.nameHe,
                current = curTop,
                previous = prevTop,
                volumeDeltaKg = curVol - prevVol,
                verdict = verdict(curTop, prevTop, curVol, prevVol, pe != null),
            )
        }
        val durDelta = if (current.session.durationMs != null && previous.session.durationMs != null) {
            current.session.durationMs!! - previous.session.durationMs!!
        } else null
        return SessionComparison(
            previousSessionId = previous.session.id,
            previousDate = previous.session.localDate,
            volumeDeltaKg = curTotals.volumeKg - prevTotals.volumeKg,
            volumePercent = if (prevTotals.volumeKg > 0) (curTotals.volumeKg - prevTotals.volumeKg) / prevTotals.volumeKg * 100.0 else null,
            workingSetsDelta = curTotals.workingSets - prevTotals.workingSets,
            durationDeltaMs = durDelta,
            exercises = comparisons,
        )
    }

    private fun key(e: ExerciseWithSets): String = e.exercise.exerciseId ?: e.exercise.nameEn.lowercase().ifBlank { e.exercise.nameHe }

    internal fun verdict(cur: ExerciseSet?, prev: ExerciseSet?, curVol: Double, prevVol: Double, existedBefore: Boolean): Verdict {
        if (!existedBefore || (prev == null && prevVol == 0.0)) return Verdict.NEW
        if (cur == null) return Verdict.DOWN
        val cw = cur.weightKg
        val pw = prev?.weightKg
        val cr = cur.reps
        val pr = prev?.reps
        if (cw != null && pw != null && cr != null && pr != null) {
            val ce = OneRepMax.epley(cw, cr)
            val pe = OneRepMax.epley(pw, pr)
            if (ce != null && pe != null) {
                return when {
                    abs(ce - pe) < 0.01 -> Verdict.SAME
                    ce > pe -> Verdict.UP
                    else -> Verdict.DOWN
                }
            }
        }
        return when {
            abs(curVol - prevVol) < 0.01 -> Verdict.SAME
            curVol > prevVol -> Verdict.UP
            else -> Verdict.DOWN
        }
    }
}

enum class RecordType { MAX_WEIGHT, BEST_E1RM }

data class PersonalRecord(
    val nameHe: String,
    val type: RecordType,
    val newValue: Double,
    val oldValue: Double,
)

/** Best values recorded for an exercise *before* the session being checked. */
data class RecordBaseline(val maxWeightKg: Double, val bestE1rmKg: Double?)

object PersonalRecords {
    /** Detects records versus [baselines] (keyed by exerciseId). The very first time is not a record. */
    fun detect(current: WorkoutDetail, baselines: Map<String, RecordBaseline>): List<PersonalRecord> {
        val out = mutableListOf<PersonalRecord>()
        for (ex in current.exercises) {
            val id = ex.exercise.exerciseId ?: continue
            val base = baselines[id] ?: continue
            val counted = ex.sets.filter(VolumeCalculator::counts).filter { (it.weightKg ?: 0.0) > 0.0 && (it.reps ?: 0) > 0 }
            val maxW = counted.maxOfOrNull { it.weightKg!! } ?: continue
            if (maxW > base.maxWeightKg + 1e-9) {
                out += PersonalRecord(ex.exercise.nameHe, RecordType.MAX_WEIGHT, maxW, base.maxWeightKg)
            }
            val bestE = counted.mapNotNull { OneRepMax.epley(it.weightKg!!, it.reps!!) }.maxOrNull()
            val baseE = base.bestE1rmKg
            if (bestE != null && baseE != null && bestE > baseE + 1e-9 && maxW <= base.maxWeightKg + 1e-9) {
                out += PersonalRecord(ex.exercise.nameHe, RecordType.BEST_E1RM, bestE, baseE)
            }
        }
        return out
    }
}

object BodyTrends {
    data class WeightChange(val days: Int, val deltaKg: Double, val fromDate: LocalDate, val toDate: LocalDate)

    /**
     * Change in body weight between the latest weigh-in and the weigh-in closest to
     * `latest - days` (within a tolerance). Returns null if there is no suitable baseline.
     * This reports a number only; it never claims whether the change is fat, muscle or water.
     */
    fun weightChange(measurements: List<BodyMeasurement>, days: Int): WeightChange? {
        val weighed = measurements.filter { it.weightKg != null }
            .mapNotNull { m -> TimeUtils.parseDate(m.localDate)?.let { it to m.weightKg!! } }
            .sortedBy { it.first }
        if (weighed.size < 2) return null
        val (latestDate, latestWeight) = weighed.last()
        val target = latestDate.minusDays(days.toLong())
        val tolerance = maxOf(2, days / 4).toLong()
        val baseline = weighed.dropLast(1)
            .filter { abs(ChronoUnit.DAYS.between(it.first, target)) <= tolerance }
            .minByOrNull { abs(ChronoUnit.DAYS.between(it.first, target)) }
            ?: return null
        return WeightChange(days, latestWeight - baseline.second, baseline.first, latestDate)
    }

    data class Point(val date: LocalDate, val value: Double)

    /** Trailing moving average over [windowDays] calendar days, to separate trend from daily noise. */
    fun movingAverage(points: List<Point>, windowDays: Int = 7): List<Point> {
        val sorted = points.sortedBy { it.date }
        return sorted.map { p ->
            val window = sorted.filter { it.date <= p.date && ChronoUnit.DAYS.between(it.date, p.date) < windowDays }
            Point(p.date, window.map { it.value }.average())
        }
    }
}
