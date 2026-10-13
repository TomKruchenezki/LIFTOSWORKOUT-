package app.liftos.domain.analytics

import app.liftos.domain.model.MuscleGroup
import java.time.LocalDate

/**
 * The assumptions behind per-muscle set counts. They are shown to the user next to the numbers.
 *
 *  - A set is credited in full ("direct") to the exercise's PRIMARY muscle only.
 *  - If the user turns indirect sets on, each SECONDARY muscle of the exercise receives
 *    [INDIRECT_FACTOR] of a set. This is a rough convention, not a measurement.
 *  - Warm-up sets are never counted. A left+right unilateral pair is one set.
 */
object MuscleModel {
    const val INDIRECT_FACTOR = 0.5

    const val ASSUMPTIONS =
        "סטים ישירים נספרים לשריר הראשי של התרגיל בלבד. כשמפעילים \"כולל עקיפים\", כל שריר משני מקבל חצי סט לכל סט – " +
            "זו הנחה מקובלת ולא מדידה. סטי חימום לא נספרים, וזוג ימין+שמאל נחשב סט אחד."
}

/** Totals for a date range. */
data class PeriodStats(
    val range: DateRange,
    val workouts: Int,
    val workingSets: Int,
    val totalReps: Int,
    val volumeKg: Double,
    val trainedDays: Int,
    val avgDurationMs: Long?,
)

data class MuscleLoad(
    val muscle: MuscleGroup,
    /** Sets where this muscle is the primary one. */
    val directSets: Int,
    /** Sets where it is a secondary muscle, already multiplied by [MuscleModel.INDIRECT_FACTOR]. */
    val indirectSets: Double,
    /** Workouts that trained the muscle directly. */
    val workouts: Int,
    /** Volume of the exercises that target it directly. */
    val volumeKg: Double,
) {
    fun sets(includeIndirect: Boolean): Double = directSets + if (includeIndirect) indirectSets else 0.0
}

data class Bucket(
    val start: LocalDate,
    val end: LocalDate,
    val workouts: Int,
    val workingSets: Int,
    val volumeKg: Double,
    val avgDurationMs: Long?,
)

/** A metric in two periods. [percent] is null when the earlier value was zero (no meaningful ratio). */
data class MetricChange(val current: Double, val previous: Double) {
    val delta: Double get() = current - previous
    val percent: Double? get() = if (previous > 0.0) delta / previous * 100.0 else null
}

data class PeriodComparison(
    val current: PeriodStats,
    val previous: PeriodStats,
    val workouts: MetricChange,
    val workingSets: MetricChange,
    val volumeKg: MetricChange,
    val avgDurationMin: MetricChange?,
)

object VolumeAnalytics {

    fun inRange(facts: List<SetFact>, range: DateRange): List<SetFact> = facts.filter { it.date in range }

    fun sessionsInRange(sessions: List<SessionFact>, range: DateRange): List<SessionFact> =
        sessions.filter { it.date in range }

    fun periodStats(facts: List<SetFact>, sessions: List<SessionFact>, range: DateRange): PeriodStats {
        val sess = sessionsInRange(sessions, range)
        val working = inRange(facts, range).filter { it.isWorking }
        val durations = sess.mapNotNull { it.durationMs }
        return PeriodStats(
            range = range,
            workouts = sess.size,
            workingSets = SetCounting.effectiveWorkingSets(working),
            totalReps = working.sumOf { it.reps ?: 0 },
            volumeKg = working.sumOf { it.volumeKg },
            trainedDays = sess.map { it.localDate }.distinct().size,
            avgDurationMs = if (durations.isEmpty()) null else durations.average().toLong(),
        )
    }

    fun compare(facts: List<SetFact>, sessions: List<SessionFact>, range: DateRange): PeriodComparison {
        val cur = periodStats(facts, sessions, range)
        val prev = periodStats(facts, sessions, range.previous())
        return PeriodComparison(
            current = cur,
            previous = prev,
            workouts = MetricChange(cur.workouts.toDouble(), prev.workouts.toDouble()),
            workingSets = MetricChange(cur.workingSets.toDouble(), prev.workingSets.toDouble()),
            volumeKg = MetricChange(cur.volumeKg, prev.volumeKg),
            avgDurationMin = if (cur.avgDurationMs != null && prev.avgDurationMs != null) {
                MetricChange(cur.avgDurationMs / 60_000.0, prev.avgDurationMs / 60_000.0)
            } else null,
        )
    }

    /** Totals per day/week/month bucket; empty buckets are kept so charts show real gaps. */
    fun buckets(
        facts: List<SetFact>,
        sessions: List<SessionFact>,
        range: DateRange,
        granularity: Granularity,
    ): List<Bucket> {
        val factsByBucket = inRange(facts, range).filter { it.isWorking }.groupBy { Buckets.startOf(it.date, granularity) }
        val sessionsByBucket = sessionsInRange(sessions, range).groupBy { Buckets.startOf(it.date, granularity) }
        return Buckets.starts(range, granularity).map { start ->
            val f = factsByBucket[start].orEmpty()
            val s = sessionsByBucket[start].orEmpty()
            val durations = s.mapNotNull { it.durationMs }
            Bucket(
                start = start,
                end = Buckets.endOf(start, granularity),
                workouts = s.size,
                workingSets = SetCounting.effectiveWorkingSets(f),
                volumeKg = f.sumOf { it.volumeKg },
                avgDurationMs = if (durations.isEmpty()) null else durations.average().toLong(),
            )
        }
    }

    /** Per-muscle loads for the range, most trained first. Muscles with no sets are omitted. */
    fun muscleLoads(facts: List<SetFact>, range: DateRange): List<MuscleLoad> {
        val groups = inRange(facts, range).filter { it.isWorking }.groupBy { it.sessionExerciseId }.values
        val direct = HashMap<MuscleGroup, Int>()
        val indirect = HashMap<MuscleGroup, Double>()
        val volume = HashMap<MuscleGroup, Double>()
        val sessionsPerMuscle = HashMap<MuscleGroup, MutableSet<String>>()
        for (rows in groups) {
            val first = rows.first()
            val sets = SetCounting.effectiveSets(rows)
            if (sets == 0) continue
            direct.merge(first.primaryMuscle, sets, Int::plus)
            volume.merge(first.primaryMuscle, rows.sumOf { it.volumeKg }, Double::plus)
            sessionsPerMuscle.getOrPut(first.primaryMuscle) { mutableSetOf() }.add(first.sessionId)
            for (m in first.secondaryMuscles.filter { it != first.primaryMuscle }.distinct()) {
                indirect.merge(m, sets * MuscleModel.INDIRECT_FACTOR, Double::plus)
            }
        }
        val muscles = direct.keys + indirect.keys
        return muscles.map { m ->
            MuscleLoad(
                muscle = m,
                directSets = direct[m] ?: 0,
                indirectSets = indirect[m] ?: 0.0,
                workouts = sessionsPerMuscle[m]?.size ?: 0,
                volumeKg = volume[m] ?: 0.0,
            )
        }.sortedWith(compareByDescending<MuscleLoad> { it.directSets }.thenByDescending { it.indirectSets })
    }

    /** Volume of each workout, keyed by session id. */
    fun volumeBySession(facts: List<SetFact>): Map<String, Double> =
        facts.filter { it.isWorking }.groupBy { it.sessionId }.mapValues { (_, rows) -> rows.sumOf { it.volumeKg } }

    fun earliestDate(sessions: List<SessionFact>, facts: List<SetFact>): LocalDate? =
        (sessions.map { it.date } + facts.map { it.date }).minOrNull()
}
