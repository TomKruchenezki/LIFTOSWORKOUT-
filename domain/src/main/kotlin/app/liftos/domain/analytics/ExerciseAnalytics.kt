package app.liftos.domain.analytics

import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.TrackingType
import java.time.LocalDate
import kotlin.math.abs

/** A recorded best: the set that achieved it, and where/when. */
data class PerformanceMark(
    val value: Double,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val date: LocalDate,
    val sessionId: String,
    val sessionTitle: String,
)

/** Everything one exercise did in one workout (working sets only). */
data class ExerciseSessionStat(
    val sessionId: String,
    val sessionTitle: String,
    val date: LocalDate,
    val startedAt: Long,
    /** Effective working sets (a left+right pair counts once). */
    val workingSets: Int,
    val totalReps: Int,
    val volumeKg: Double,
    val topWeightKg: Double?,
    val repsAtTopWeight: Int?,
    val bestE1rmKg: Double?,
    /** Weight averaged over reps (volume / reps) for loaded sets; null when nothing was loaded. */
    val avgWorkingWeightKg: Double?,
    val avgReps: Double?,
    val maxReps: Int?,
    val maxDurationSec: Int?,
    val sets: List<SetFact>,
)

enum class TrendMetric(val labelHe: String, val unit: String) {
    E1RM("1RM משוער", "ק״ג"),
    TOP_WEIGHT("משקל מקסימלי", "ק״ג"),
    REPS("חזרות מקסימליות", "חזרות"),
    DURATION("זמן מקסימלי", "שניות"),
}

/** Change of the exercise's main metric inside a time window. Needs at least two workouts in it. */
data class TrendResult(
    val windowDays: Int,
    val metric: TrendMetric,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val fromValue: Double,
    val toValue: Double,
    val sessions: Int,
) {
    val delta: Double get() = toValue - fromValue
    val percent: Double? get() = if (fromValue > 0.0) delta / fromValue * 100.0 else null
}

data class WeightRepsRow(
    val weightKg: Double,
    val sets: Int,
    val bestReps: Int,
    val totalReps: Int,
    val sessions: Int,
    val lastDate: LocalDate,
)

enum class ChangeVerdict { NEW, UP, SAME, DOWN }

/** Last workout versus the one before it, for the same exercise. */
data class SessionDelta(
    val previousDate: LocalDate,
    val volumeDeltaKg: Double,
    val topWeightDeltaKg: Double?,
    val e1rmDeltaKg: Double?,
    val repsDelta: Int,
    val verdict: ChangeVerdict,
)

data class ExerciseOverview(
    val key: String,
    val name: String,
    val nameEn: String,
    val primaryMuscle: MuscleGroup,
    val trackingType: TrackingType,
    /** Oldest first. */
    val sessions: List<ExerciseSessionStat>,
    val maxWeight: PerformanceMark?,
    val bestE1rm: PerformanceMark?,
    /** Heaviest weight where exactly one rep was completed. This is a logged single, not a tested max. */
    val actualOneRm: PerformanceMark?,
    /** Heaviest weight lifted for at least 5/8/10 reps. */
    val repMax: Map<Int, PerformanceMark>,
    val maxRepsUnloaded: PerformanceMark?,
    val maxDuration: PerformanceMark?,
    val totalVolumeKg: Double,
    val totalWorkingSets: Int,
    val totalReps: Int,
    val metric: TrendMetric,
    val trends: List<TrendResult?>,
    val lastVsPrevious: SessionDelta?,
    val weightTable: List<WeightRepsRow>,
) {
    val sessionCount: Int get() = sessions.size
    val lastSession: ExerciseSessionStat? get() = sessions.lastOrNull()
}

/** One row of the exercise list on the analytics screen. */
data class ExerciseListRow(
    val key: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val sessions: Int,
    val lastDate: LocalDate,
    val lastTop: String?,
)

object ExerciseAnalytics {
    private const val EPS = 1e-9
    val TREND_WINDOWS = listOf(30, 90, 365)
    val REP_MAX_TARGETS = listOf(5, 8, 10)

    fun factsFor(facts: List<SetFact>, key: String): List<SetFact> = facts.filter { it.key == key }

    fun list(facts: List<SetFact>): List<ExerciseListRow> =
        facts.groupBy { it.key }.mapNotNull { (key, rows) ->
            val working = rows.filter { it.isWorking }
            if (working.isEmpty()) return@mapNotNull null
            val stats = sessionStats(rows)
            val last = stats.lastOrNull() ?: return@mapNotNull null
            val first = rows.first()
            ExerciseListRow(
                key = key,
                name = first.exerciseName,
                primaryMuscle = first.primaryMuscle,
                sessions = stats.size,
                lastDate = last.date,
                lastTop = describeTop(last, first.trackingType),
            )
        }.sortedWith(compareByDescending<ExerciseListRow> { it.lastDate }.thenBy { it.name })

    private fun describeTop(s: ExerciseSessionStat, tracking: TrackingType): String? = when {
        tracking == TrackingType.DURATION -> s.maxDurationSec?.let { "$it שניות" }
        s.topWeightKg != null && s.topWeightKg > 0 && s.repsAtTopWeight != null ->
            "${app.liftos.domain.logic.Formatters.number(s.topWeightKg)} ק״ג × ${s.repsAtTopWeight}"
        s.maxReps != null -> "${s.maxReps} חזרות"
        else -> null
    }

    /** Per-workout stats for the (already filtered to one exercise) facts. Oldest first. */
    fun sessionStats(exerciseFacts: List<SetFact>): List<ExerciseSessionStat> =
        exerciseFacts.filter { it.isWorking }
            .groupBy { it.sessionId }
            .values
            .map { rows -> statFor(rows.sortedWith(compareBy({ it.exerciseOrder }, { it.setOrder }))) }
            .sortedWith(compareBy({ it.date }, { it.startedAt }))

    private fun statFor(rows: List<SetFact>): ExerciseSessionStat {
        val first = rows.first()
        val loaded = rows.filter { it.isLoaded && (it.reps ?: 0) > 0 }
        val top = loaded.maxWithOrNull(compareBy<SetFact>({ it.weightKg }, { it.reps }))
        val repsSum = rows.sumOf { it.reps ?: 0 }
        val loadedReps = loaded.sumOf { it.reps ?: 0 }
        val volume = rows.sumOf { it.volumeKg }
        val repSets = rows.filter { (it.reps ?: 0) > 0 }
        return ExerciseSessionStat(
            sessionId = first.sessionId,
            sessionTitle = first.sessionTitle,
            date = first.date,
            startedAt = first.startedAt,
            workingSets = SetCounting.effectiveSets(rows),
            totalReps = repsSum,
            volumeKg = volume,
            topWeightKg = top?.weightKg,
            repsAtTopWeight = top?.reps,
            bestE1rmKg = rows.mapNotNull { it.estimatedOneRm }.maxOrNull(),
            avgWorkingWeightKg = if (loadedReps > 0) volume / loadedReps else null,
            avgReps = if (repSets.isNotEmpty()) repSets.sumOf { it.reps ?: 0 }.toDouble() / repSets.size else null,
            maxReps = repSets.maxOfOrNull { it.reps ?: 0 },
            maxDurationSec = rows.mapNotNull { it.durationSec }.filter { it > 0 }.maxOrNull(),
            sets = rows,
        )
    }

    fun metricValue(stat: ExerciseSessionStat, metric: TrendMetric): Double? = when (metric) {
        TrendMetric.E1RM -> stat.bestE1rmKg
        TrendMetric.TOP_WEIGHT -> stat.topWeightKg
        TrendMetric.REPS -> stat.maxReps?.toDouble()
        TrendMetric.DURATION -> stat.maxDurationSec?.toDouble()
    }

    fun primaryMetric(tracking: TrackingType, stats: List<ExerciseSessionStat>): TrendMetric = when {
        tracking == TrackingType.DURATION -> TrendMetric.DURATION
        stats.any { it.bestE1rmKg != null } -> TrendMetric.E1RM
        stats.any { it.topWeightKg != null } -> TrendMetric.TOP_WEIGHT
        else -> TrendMetric.REPS
    }

    fun overview(facts: List<SetFact>, key: String, today: LocalDate): ExerciseOverview? {
        val rows = factsFor(facts, key)
        val working = rows.filter { it.isWorking }
        if (working.isEmpty()) return null
        val sample = rows.first()
        val stats = sessionStats(rows)
        val metric = primaryMetric(sample.trackingType, stats)

        val loaded = working.filter { it.isLoaded && (it.reps ?: 0) > 0 }
        val maxWeight = loaded.maxWithOrNull(compareBy<SetFact>({ it.weightKg }, { it.reps }, { it.startedAt }))
            ?.let { mark(it, it.weightKg!!) }
        val bestE1rm = loaded.filter { it.estimatedOneRm != null }
            .maxWithOrNull(compareBy<SetFact>({ it.estimatedOneRm }, { it.startedAt }))
            ?.let { mark(it, it.estimatedOneRm!!) }
        val actual = loaded.filter { it.reps == 1 }
            .maxWithOrNull(compareBy<SetFact>({ it.weightKg }, { it.startedAt }))
            ?.let { mark(it, it.weightKg!!) }
        val repMax = REP_MAX_TARGETS.mapNotNull { n ->
            loaded.filter { (it.reps ?: 0) >= n }
                .maxWithOrNull(compareBy<SetFact>({ it.weightKg }, { it.reps }, { it.startedAt }))
                ?.let { n to mark(it, it.weightKg!!) }
        }.toMap()
        val unloaded = working.filter { !it.isLoaded && (it.reps ?: 0) > 0 }
        val maxRepsUnloaded = unloaded.maxWithOrNull(compareBy<SetFact>({ it.reps }, { it.startedAt }))
            ?.let { mark(it, it.reps!!.toDouble()) }
        val maxDuration = working.filter { (it.durationSec ?: 0) > 0 }
            .maxWithOrNull(compareBy<SetFact>({ it.durationSec }, { it.startedAt }))
            ?.let { mark(it, it.durationSec!!.toDouble()) }

        return ExerciseOverview(
            key = key,
            name = sample.exerciseName,
            nameEn = sample.exerciseNameEn,
            primaryMuscle = sample.primaryMuscle,
            trackingType = sample.trackingType,
            sessions = stats,
            maxWeight = maxWeight,
            bestE1rm = bestE1rm,
            actualOneRm = actual,
            repMax = repMax,
            maxRepsUnloaded = maxRepsUnloaded,
            maxDuration = maxDuration,
            totalVolumeKg = stats.sumOf { it.volumeKg },
            totalWorkingSets = stats.sumOf { it.workingSets },
            totalReps = stats.sumOf { it.totalReps },
            metric = metric,
            trends = TREND_WINDOWS.map { trend(stats, metric, it, today) },
            lastVsPrevious = lastVsPrevious(stats),
            weightTable = weightTable(working),
        )
    }

    private fun mark(f: SetFact, value: Double) = PerformanceMark(
        value = value,
        weightKg = f.weightKg,
        reps = f.reps,
        durationSec = f.durationSec,
        date = f.date,
        sessionId = f.sessionId,
        sessionTitle = f.sessionTitle,
    )

    /** First vs last workout inside the window, by the exercise's main metric. Null with fewer than two workouts. */
    fun trend(stats: List<ExerciseSessionStat>, metric: TrendMetric, windowDays: Int, today: LocalDate): TrendResult? {
        val range = DateRange.lastDays(windowDays, today)
        val inWindow = stats.filter { it.date in range }
            .mapNotNull { s -> metricValue(s, metric)?.let { s to it } }
            .sortedBy { it.first.date }
        if (inWindow.size < 2) return null
        val (first, fv) = inWindow.first()
        val (last, lv) = inWindow.last()
        return TrendResult(windowDays, metric, first.date, last.date, fv, lv, inWindow.size)
    }

    fun lastVsPrevious(stats: List<ExerciseSessionStat>): SessionDelta? {
        if (stats.size < 2) return null
        val cur = stats[stats.size - 1]
        val prev = stats[stats.size - 2]
        val e1 = if (cur.bestE1rmKg != null && prev.bestE1rmKg != null) cur.bestE1rmKg - prev.bestE1rmKg else null
        val tw = if (cur.topWeightKg != null && prev.topWeightKg != null) cur.topWeightKg - prev.topWeightKg else null
        val verdict = when {
            e1 != null -> sign(e1)
            cur.maxDurationSec != null && prev.maxDurationSec != null -> sign((cur.maxDurationSec - prev.maxDurationSec).toDouble())
            tw != null && abs(tw) > EPS -> sign(tw)
            else -> sign(cur.volumeKg - prev.volumeKg).let { v ->
                if (v == ChangeVerdict.SAME) sign((cur.totalReps - prev.totalReps).toDouble()) else v
            }
        }
        return SessionDelta(
            previousDate = prev.date,
            volumeDeltaKg = cur.volumeKg - prev.volumeKg,
            topWeightDeltaKg = tw,
            e1rmDeltaKg = e1,
            repsDelta = cur.totalReps - prev.totalReps,
            verdict = verdict,
        )
    }

    private fun sign(v: Double): ChangeVerdict = when {
        abs(v) < 0.01 -> ChangeVerdict.SAME
        v > 0 -> ChangeVerdict.UP
        else -> ChangeVerdict.DOWN
    }

    /** Reps performed at each distinct working weight (unloaded sets are grouped under 0). */
    fun weightTable(workingRows: List<SetFact>): List<WeightRepsRow> =
        workingRows.filter { (it.reps ?: 0) > 0 }
            .groupBy { Math.round((it.weightKg ?: 0.0) * 100) / 100.0 }
            .map { (w, rows) ->
                WeightRepsRow(
                    weightKg = w,
                    sets = rows.size,
                    bestReps = rows.maxOf { it.reps ?: 0 },
                    totalReps = rows.sumOf { it.reps ?: 0 },
                    sessions = rows.map { it.sessionId }.distinct().size,
                    lastDate = rows.maxOf { it.date },
                )
            }
            .sortedByDescending { it.weightKg }

    fun weightTable(facts: List<SetFact>, key: String): List<WeightRepsRow> =
        weightTable(factsFor(facts, key).filter { it.isWorking })
}
