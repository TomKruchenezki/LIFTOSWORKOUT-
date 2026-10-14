package app.liftos.domain.export

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.ExerciseAnalytics
import app.liftos.domain.analytics.ExerciseKey
import app.liftos.domain.analytics.PrEvent
import app.liftos.domain.analytics.RecordKind
import app.liftos.domain.analytics.RecordsResult
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.WorkoutDetail

/** One line describing a personal record, shared by the single-workout and the period reports. */
object RecordLines {
    fun line(e: PrEvent): String {
        val est = if (e.kind.isEstimate) " (הערכה)" else ""
        val value = Formatters.number(e.newValue)
        val previous = Formatters.number(e.previousValue)
        return if (e.kind == RecordKind.REPS_AT_WEIGHT) {
            "${e.exerciseName}: ${e.kind.labelHe}${e.weightKg?.let { " ${Formatters.number(it)} ק״ג" }.orEmpty()} - $value ${e.kind.unit} (קודם $previous)"
        } else {
            "${e.exerciseName}: ${e.kind.labelHe}$est $value ${e.kind.unit} (קודם $previous)"
        }
    }
}

/**
 * What the single-workout AI report needs besides the workout itself: the records this workout set and the
 * trend of each exercise, computed from the facts as they were up to the workout's date.
 */
data class WorkoutReportExtras(
    val newRecords: List<PrEvent>,
    val trendLines: List<String>,
    val workoutsInLast28Days: Int,
)

object WorkoutReportContext {
    const val TREND_WINDOW_DAYS = 90
    const val NOT_ENOUGH_FOR_TREND = "אין מספיק אימונים בתקופה כדי לזהות מגמה (נדרשים לפחות שניים)"

    fun build(detail: WorkoutDetail, facts: List<SetFact>, sessions: List<SessionFact>, records: RecordsResult): WorkoutReportExtras {
        val date = TimeUtils.parseDate(detail.session.localDate) ?: return WorkoutReportExtras(emptyList(), emptyList(), 0)
        val upToThen = facts.filter { !it.date.isAfter(date) }
        val keys = detail.exercises.filter { !it.exercise.skipped }
            .map { ExerciseKey.of(it.exercise.exerciseId, it.exercise.nameEn, it.exercise.nameHe) }
            .distinct()
        val lines = keys.mapNotNull { key ->
            val o = ExerciseAnalytics.overview(upToThen, key, date) ?: return@mapNotNull null
            val t = o.trends.getOrNull(ExerciseAnalytics.TREND_WINDOWS.indexOf(TREND_WINDOW_DAYS))
            if (t == null) {
                "${o.name}: $NOT_ENOUGH_FOR_TREND"
            } else {
                val pct = t.percent?.let { " (${if (it > 0) "+" else ""}${Formatters.number(it, 1)}%)" }.orEmpty()
                "${o.name}: ${t.metric.labelHe} ${Formatters.number(t.fromValue, 1)} ← ${Formatters.number(t.toValue, 1)} ${t.metric.unit}$pct " +
                    "ב-${t.windowDays} הימים עד האימון הזה (${t.sessions} אימונים)"
            }
        }
        val window = DateRange(date.minusDays(27), date)
        return WorkoutReportExtras(
            newRecords = records.eventsFor(detail.session.id).sortedWith(compareBy({ it.exerciseName }, { it.kind.priority })),
            trendLines = lines,
            workoutsInLast28Days = sessions.count { it.date in window },
        )
    }
}
