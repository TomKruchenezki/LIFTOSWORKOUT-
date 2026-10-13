package app.liftos.domain.insights

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.ExerciseAnalytics
import app.liftos.domain.analytics.RecordKind
import app.liftos.domain.analytics.RecordsResult
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetCounting
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.logic.Formatters
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.progression.ProgressionEngine
import app.liftos.domain.progression.SuggestionKind
import app.liftos.domain.recovery.RecoveryReport
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class InsightTone { POSITIVE, INFO, ATTENTION }

data class Insight(
    val id: String,
    val tone: InsightTone,
    val text: String,
    val sessionId: String? = null,
    val exerciseKey: String? = null,
)

/**
 * Rule based observations computed from the user's own data. This is arithmetic and comparisons –
 * there is no machine learning here, and the UI says so.
 */
object InsightEngine {
    const val NOT_ENOUGH = "נדרשים אימונים נוספים כדי לזהות מגמה."

    private val groups = listOf(
        "רגליים" to setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES),
        "חזה" to setOf(MuscleGroup.CHEST),
        "גב" to setOf(MuscleGroup.BACK, MuscleGroup.TRAPS, MuscleGroup.LOWER_BACK),
        "כתפיים" to setOf(MuscleGroup.SHOULDERS),
        "ידיים" to setOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS),
        "בטן" to setOf(MuscleGroup.ABS),
    )

    fun generate(
        facts: List<SetFact>,
        sessions: List<SessionFact>,
        records: RecordsResult,
        today: LocalDate,
        targetPerWeek: Int,
        recovery: RecoveryReport? = null,
        max: Int = 8,
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val week = DateRange.weekOf(today)
        val doneThisWeek = sessions.count { it.date in week }

        if (doneThisWeek > 0) {
            val target = if (targetPerWeek > 0) " מתוך יעד של $targetPerWeek" else ""
            out += Insight("week", InsightTone.POSITIVE, "ביצעת $doneThisWeek ${if (doneThisWeek == 1) "אימון" else "אימונים"} השבוע$target.")
        }
        if (sessions.size < 3) {
            out += Insight("not-enough", InsightTone.INFO, NOT_ENOUGH)
            return out.take(max)
        }

        // Heavier working weight than last time.
        val byKey = facts.groupBy { it.key }
        val increases = byKey.mapNotNull { (key, rows) ->
            val stats = ExerciseAnalytics.sessionStats(rows)
            val delta = ExerciseAnalytics.lastVsPrevious(stats) ?: return@mapNotNull null
            val last = stats.last()
            val tw = delta.topWeightDeltaKg ?: return@mapNotNull null
            if (tw > 0.0 && ChronoUnit.DAYS.between(last.date, today) in 0..10) Triple(key, rows.first().exerciseName, tw) else null
        }.sortedByDescending { it.third }.take(3)
        increases.forEach { (key, name, kg) ->
            out += Insight("up-$key", InsightTone.POSITIVE, "העלית את משקל העבודה ב$name ב-${Formatters.number(kg)} ק״ג.", exerciseKey = key)
        }

        // Fresh records.
        records.events.filter { it.date in DateRange.lastDays(7, today) && it.kind != RecordKind.REPS_AT_WEIGHT && it.kind != RecordKind.BEST_VOLUME }
            .groupBy { it.exerciseKey }.values.take(3).forEach { evs ->
                val e = evs.minBy { it.kind.priority }
                val what = when (e.kind) {
                    RecordKind.MAX_REPS -> "שיא חדש בחזרות ב${e.exerciseName} (${Formatters.number(e.newValue, 0)} חזרות)"
                    RecordKind.MAX_DURATION -> "שיא חדש בזמן ב${e.exerciseName} (${Formatters.number(e.newValue, 0)} שניות)"
                    else -> "שיא אישי חדש ב${e.exerciseName}: ${e.kind.labelHe} ${Formatters.number(e.newValue)} ${e.kind.unit}"
                }
                out += Insight("pr-${e.exerciseKey}", InsightTone.POSITIVE, "$what.", sessionId = e.sessionId, exerciseKey = e.exerciseKey)
            }

        // Volume of the same kind of workout versus last time.
        sessions.maxByOrNull { it.startedAt }?.let { last ->
            val previous = sessions.filter { it.title == last.title && it.startedAt < last.startedAt }.maxByOrNull { it.startedAt }
            if (previous != null) {
                val vLast = facts.filter { it.sessionId == last.id && it.isWorking }.sumOf { it.volumeKg }
                val vPrev = facts.filter { it.sessionId == previous.id && it.isWorking }.sumOf { it.volumeKg }
                if (vPrev > 0.0 && vLast > 0.0) {
                    val pct = (vLast - vPrev) / vPrev * 100
                    if (abs(pct) >= 3.0) {
                        val dir = if (pct > 0) "גדל" else "קטן"
                        out += Insight(
                            "vol-${last.id}",
                            if (pct > 0) InsightTone.POSITIVE else InsightTone.INFO,
                            "נפח אימון ${last.title} $dir ב-${Formatters.number(abs(pct), 0)}% בהשוואה לפעם הקודמת.",
                            sessionId = last.id,
                        )
                    }
                }
            }
        }

        // A muscle group that was not trained for a while (only groups that were trained before).
        val neglected = groups.mapNotNull { (label, muscles) ->
            val last = facts.filter { it.isWorking && it.primaryMuscle in muscles }.maxOfOrNull { it.date } ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(last, today)
            if (days >= 7) Triple(label, days, last) else null
        }.sortedByDescending { it.second }.take(2)
        neglected.forEach { (label, days, _) ->
            out += Insight("neglect-$label", InsightTone.ATTENTION, "לא בוצע אימון $label במשך $days ימים.")
        }

        // A multi-workout decline in one exercise (same rule as the progression engine).
        val recentKeys = byKey.filter { (_, rows) -> rows.any { ChronoUnit.DAYS.between(it.date, today) in 0..21 } }.keys
        recentKeys.mapNotNull { key ->
            val name = byKey[key]!!.first().exerciseName
            val s = ProgressionEngine.suggest(key, name, facts, sessions, today)
            if (s.kind == SuggestionKind.REVIEW) key to name else null
        }.take(2).forEach { (key, name) ->
            out += Insight("dip-$key", InsightTone.ATTENTION, "נרשמה ירידה בביצועים ב$name במשך מספר אימונים.", exerciseKey = key)
        }

        // Weekly target streak.
        if (targetPerWeek > 0) {
            var streak = 0
            var start = week.start.minusWeeks(1)
            while (true) {
                val r = DateRange(start, start.plusDays(6))
                if (sessions.count { it.date in r } >= targetPerWeek) {
                    streak++
                    start = start.minusWeeks(1)
                } else break
            }
            if (streak >= 2) out += Insight("streak", InsightTone.POSITIVE, "עמדת ביעד האימונים השבועי $streak שבועות ברצף.")
        }

        recovery?.signals?.take(2)?.forEach { out += Insight("rec-${it.kind}", InsightTone.ATTENTION, it.text) }

        return out.take(max)
    }

    /** Sets per muscle group the last 7 days - used by the dashboard line "כמה סטים". */
    fun weeklySets(facts: List<SetFact>, today: LocalDate): Int =
        SetCounting.effectiveWorkingSets(facts.filter { it.date in DateRange.lastDays(7, today) })
}
