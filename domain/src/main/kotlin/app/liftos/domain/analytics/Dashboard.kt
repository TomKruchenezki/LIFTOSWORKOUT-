package app.liftos.domain.analytics

import app.liftos.domain.model.MuscleGroup
import java.time.LocalDate

/** How many tracked exercises got stronger, stayed level or dropped between two equal windows. */
data class StrengthTrend(
    val windowDays: Int,
    val tracked: Int,
    val improved: Int,
    val same: Int,
    val declined: Int,
    val medianPercent: Double?,
) {
    /** A trend needs at least one exercise with data in both windows; otherwise we say nothing. */
    val hasEnoughData: Boolean get() = tracked >= 1
}

data class Adherence(val completed: Int, val expected: Int, val windowDays: Int) {
    /** 0..100; extra workouts beyond the plan do not push it above 100. */
    val percent: Int get() = if (expected <= 0) 0 else (minOf(completed, expected) * 100 / expected)
}

data class DashboardCards(
    val today: LocalDate,
    val week: PeriodStats,
    val weekComparison: PeriodComparison,
    val newPrsThisWeek: Int,
    val musclesTrainedThisWeek: List<MuscleGroup>,
    val adherence: Adherence?,
    val strength: StrengthTrend,
    val avgDurationMs: Long?,
    val hasAnyWorkout: Boolean,
)

object DashboardAnalytics {
    private const val SAME_THRESHOLD_PERCENT = 2.0
    const val STRENGTH_WINDOW_DAYS = 28

    fun cards(
        facts: List<SetFact>,
        sessions: List<SessionFact>,
        records: RecordsResult,
        today: LocalDate,
        targetPerWeek: Int,
    ): DashboardCards {
        val week = DateRange.weekOf(today)
        val comparison = VolumeAnalytics.compare(facts, sessions, week)
        val prsThisWeek = records.events.count { it.date in week && it.kind != RecordKind.REPS_AT_WEIGHT && it.kind != RecordKind.BEST_VOLUME }
        val muscles = VolumeAnalytics.muscleLoads(facts, week).filter { it.directSets > 0 }.map { it.muscle }
        val last30 = VolumeAnalytics.periodStats(facts, sessions, DateRange.lastDays(30, today))
        return DashboardCards(
            today = today,
            week = comparison.current,
            weekComparison = comparison,
            newPrsThisWeek = prsThisWeek,
            musclesTrainedThisWeek = muscles,
            adherence = adherence(sessions, targetPerWeek, today),
            strength = strengthTrend(facts, today),
            avgDurationMs = last30.avgDurationMs,
            hasAnyWorkout = sessions.isNotEmpty(),
        )
    }

    /** Workouts done in the last [days] days against what the plan asks for. Null when no target is set. */
    fun adherence(sessions: List<SessionFact>, targetPerWeek: Int, today: LocalDate, days: Int = 28): Adherence? {
        if (targetPerWeek <= 0) return null
        val range = DateRange.lastDays(days, today)
        val expected = Math.round(targetPerWeek * days / 7.0).toInt().coerceAtLeast(1)
        val done = sessions.count { it.date in range }
        return Adherence(done, expected, days)
    }

    fun strengthTrend(facts: List<SetFact>, today: LocalDate, windowDays: Int = STRENGTH_WINDOW_DAYS): StrengthTrend {
        val recent = DateRange.lastDays(windowDays, today)
        val prior = recent.previous()
        val percents = mutableListOf<Double>()
        for ((_, rows) in facts.groupBy { it.key }) {
            val stats = ExerciseAnalytics.sessionStats(rows)
            if (stats.isEmpty()) continue
            val metric = ExerciseAnalytics.primaryMetric(rows.first().trackingType, stats)
            val cur = stats.filter { it.date in recent }.mapNotNull { ExerciseAnalytics.metricValue(it, metric) }.maxOrNull()
            val old = stats.filter { it.date in prior }.mapNotNull { ExerciseAnalytics.metricValue(it, metric) }.maxOrNull()
            if (cur != null && old != null && old > 0.0) percents += (cur - old) / old * 100.0
        }
        val improved = percents.count { it > SAME_THRESHOLD_PERCENT }
        val declined = percents.count { it < -SAME_THRESHOLD_PERCENT }
        return StrengthTrend(
            windowDays = windowDays,
            tracked = percents.size,
            improved = improved,
            same = percents.size - improved - declined,
            declined = declined,
            medianPercent = median(percents),
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }
}
