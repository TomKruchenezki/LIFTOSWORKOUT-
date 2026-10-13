package app.liftos.domain.analytics

import app.liftos.domain.logic.Formatters
import java.time.LocalDate
import kotlin.math.abs

/**
 * One tappable point of a chart: the exact value, its date, and the workout it came from
 * (so a tap can show "62.5 ק״ג · Upper A · 13/10/2026" and open that workout).
 */
data class ChartPoint(
    val date: LocalDate,
    val value: Double,
    val title: String,
    val detail: String = "",
    val sessionId: String? = null,
)

data class DistributionSlice(val label: String, val value: Double, val fraction: Double)

object ChartSeries {

    private fun n(v: Double, d: Int = 1) = Formatters.number(v, d)

    fun exerciseMetric(stats: List<ExerciseSessionStat>, metric: TrendMetric, range: DateRange): List<ChartPoint> =
        stats.filter { it.date in range }.mapNotNull { s ->
            val v = ExerciseAnalytics.metricValue(s, metric) ?: return@mapNotNull null
            val setText = when (metric) {
                TrendMetric.E1RM -> s.sets.filter { it.estimatedOneRm != null }.maxByOrNull { it.estimatedOneRm!! }
                    ?.let { "מתוך ${n(it.weightKg!!, 2)} × ${it.reps}" }
                TrendMetric.TOP_WEIGHT -> s.repsAtTopWeight?.let { "× $it חזרות" }
                else -> null
            }
            ChartPoint(
                date = s.date,
                value = v,
                title = "${n(v, 2)} ${metric.unit}",
                detail = listOfNotNull(setText, s.sessionTitle, Formatters.date(s.date)).joinToString(" · "),
                sessionId = s.sessionId,
            )
        }

    fun exerciseVolume(stats: List<ExerciseSessionStat>, range: DateRange): List<ChartPoint> =
        stats.filter { it.date in range && it.volumeKg > 0 }.map { s ->
            ChartPoint(
                date = s.date,
                value = s.volumeKg,
                title = "${Formatters.thousands(s.volumeKg)} ק״ג-חזרות",
                detail = "${s.workingSets} סטים · ${s.sessionTitle} · ${Formatters.date(s.date)}",
                sessionId = s.sessionId,
            )
        }

    /** Best reps at one fixed weight, per workout. */
    fun repsAtWeight(facts: List<SetFact>, key: String, weightKg: Double, range: DateRange): List<ChartPoint> =
        facts.filter { it.key == key && it.isWorking && it.date in range && (it.reps ?: 0) > 0 }
            .filter { abs((it.weightKg ?: 0.0) - weightKg) < 0.01 }
            .groupBy { it.sessionId }
            .values.map { rows ->
                val best = rows.maxByOrNull { it.reps!! }!!
                ChartPoint(
                    date = best.date,
                    value = best.reps!!.toDouble(),
                    title = "${best.reps} חזרות ב-${n(weightKg, 2)} ק״ג",
                    detail = "${best.sessionTitle} · ${Formatters.date(best.date)}",
                    sessionId = best.sessionId,
                )
            }.sortedBy { it.date }

    /** Total volume of every workout in the range. */
    fun workoutVolume(facts: List<SetFact>, sessions: List<SessionFact>, range: DateRange): List<ChartPoint> {
        val volumes = VolumeAnalytics.volumeBySession(facts)
        return sessions.filter { it.date in range }.sortedBy { it.startedAt }.mapNotNull { s ->
            val v = volumes[s.id] ?: return@mapNotNull null
            if (v <= 0.0) return@mapNotNull null
            ChartPoint(s.date, v, "${Formatters.thousands(v)} ק״ג-חזרות", "${s.title} · ${Formatters.date(s.date)}", s.id)
        }
    }

    fun workoutDurations(sessions: List<SessionFact>, range: DateRange): List<ChartPoint> =
        sessions.filter { it.date in range }.sortedBy { it.startedAt }.mapNotNull { s ->
            val ms = s.durationMs ?: return@mapNotNull null
            ChartPoint(s.date, ms / 60_000.0, "${ms / 60_000} דקות", "${s.title} · ${Formatters.date(s.date)}", s.id)
        }

    enum class BucketMetric(val labelHe: String) {
        VOLUME("נפח (ק״ג-חזרות)"),
        SETS("סטי עבודה"),
        WORKOUTS("אימונים"),
        DURATION("משך ממוצע (דקות)"),
    }

    fun bucketSeries(buckets: List<Bucket>, metric: BucketMetric, granularity: Granularity): List<ChartPoint> =
        buckets.mapNotNull { b ->
            val v = when (metric) {
                BucketMetric.VOLUME -> b.volumeKg
                BucketMetric.SETS -> b.workingSets.toDouble()
                BucketMetric.WORKOUTS -> b.workouts.toDouble()
                BucketMetric.DURATION -> (b.avgDurationMs ?: return@mapNotNull null) / 60_000.0
            }
            val span = when (granularity) {
                Granularity.DAY -> Formatters.date(b.start)
                else -> "${Formatters.date(b.start)}–${Formatters.date(b.end)}"
            }
            val title = when (metric) {
                BucketMetric.VOLUME -> "${Formatters.thousands(v)} ק״ג-חזרות"
                BucketMetric.SETS -> "${v.toInt()} סטים"
                BucketMetric.WORKOUTS -> "${v.toInt()} אימונים"
                BucketMetric.DURATION -> "${v.toInt()} דקות"
            }
            ChartPoint(b.start, v, title, "${b.workouts} אימונים · $span")
        }

    fun muscleDistribution(loads: List<MuscleLoad>, includeIndirect: Boolean): List<DistributionSlice> {
        val values = loads.map { it.muscle.labelHe to it.sets(includeIndirect) }.filter { it.second > 0.0 }
        val total = values.sumOf { it.second }
        if (total <= 0.0) return emptyList()
        return values.sortedByDescending { it.second }.map { (label, v) -> DistributionSlice(label, v, v / total) }
    }
}
