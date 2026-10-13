package app.liftos.domain.progression

import app.liftos.domain.analytics.ExerciseAnalytics
import app.liftos.domain.analytics.ExerciseSessionStat
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetCounting
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.logic.Formatters
import app.liftos.domain.model.TrackingType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Double progression: stay at a weight until every set reaches the top of the rep range, then add
 * the smallest weight jump and start again from the bottom of the range.
 *
 * The engine is deliberately cautious:
 *  - one weak workout never makes the user "weaker" (it only produces "repeat the weight");
 *  - a decline needs two consecutive workouts to be flagged, and is phrased as "check recovery";
 *  - thin history lowers the confidence and says so;
 *  - nothing is ever applied automatically.
 */
object ProgressionEngine {
    const val LONG_GAP_DAYS = 28
    private const val DECLINE_TOLERANCE = 0.01
    private const val DURATION_STEP_SEC = 5

    /** The last workout seen through the lens of the rep range. */
    private data class Eval(
        val stat: ExerciseSessionStat,
        val weightKg: Double?,
        val values: List<Int>,
        val setCount: Int,
        val allAtTop: Boolean,
        val belowRange: Boolean,
        val anyFailure: Boolean,
        val avgRir: Double?,
        /** Different working weights in one workout (pyramid / ramp): double progression does not apply. */
        val varied: Boolean,
    )

    private class Ctx(
        val key: String,
        val name: String,
        val evidence: List<String>,
        val baseCautions: List<String>,
        val baseConfidence: Confidence,
        val currentWeight: Double?,
        val basedOn: String,
        val sessions: Int,
    ) {
        fun make(
            kind: SuggestionKind,
            headline: String,
            explanation: String,
            suggestedWeight: Double? = null,
            targetMin: Int? = null,
            targetMax: Int? = null,
            confidence: Confidence = baseConfidence,
            extraCautions: List<String> = emptyList(),
        ) = ProgressionSuggestion(
            exerciseKey = key, exerciseName = name, kind = kind, headline = headline, explanation = explanation,
            evidence = evidence, cautions = baseCautions + extraCautions, confidence = confidence,
            currentWeightKg = currentWeight, suggestedWeightKg = suggestedWeight,
            targetRepsMin = targetMin, targetRepsMax = targetMax,
            basedOnSessionId = basedOn, sessionsConsidered = sessions,
        )
    }

    private fun valueOf(set: SetFact, tracking: TrackingType): Int =
        if (tracking == TrackingType.DURATION) set.durationSec ?: 0 else set.reps ?: 0

    fun suggest(
        key: String,
        name: String,
        facts: List<SetFact>,
        sessions: List<SessionFact>,
        today: LocalDate,
        target: RepTarget? = null,
        incrementOverrideKg: Double? = null,
    ): ProgressionSuggestion {
        val exerciseFacts = facts.filter { it.key == key }
        val tracking = exerciseFacts.firstOrNull()?.trackingType ?: TrackingType.WEIGHT_REPS
        val stats = ExerciseAnalytics.sessionStats(exerciseFacts)
            .filter { s -> s.sets.any { it.isStraightSet && valueOf(it, tracking) > 0 } }
        if (stats.isEmpty()) {
            return ProgressionSuggestion(
                exerciseKey = key, exerciseName = name, kind = SuggestionKind.INSUFFICIENT_DATA,
                headline = "אין עדיין היסטוריה לתרגיל הזה.",
                explanation = "נדרש לפחות אימון אחד מתועד כדי להציע התקדמות. הזן משקל התחלתי נוח והתחל.",
            )
        }
        val sample = exerciseFacts.last()
        val rt = target
            ?: sample.takeIf { (it.targetRepsMin ?: 0) >= 1 && (it.targetRepsMax ?: 0) >= (it.targetRepsMin ?: 0) }
                ?.let { RepTarget(it.targetRepsMin!!, it.targetRepsMax!!, it.plannedSets.coerceAtLeast(1)) }
            ?: RepTarget.DEFAULT

        val lastEval = evaluate(stats.last(), rt, tracking)
        val last = lastEval.stat
        val gapDays = ChronoUnit.DAYS.between(last.date, today)

        val cautions = mutableListOf<String>()
        val rating = sessions.firstOrNull { it.id == last.sessionId }
        if (rating != null) {
            if ((rating.energyRating ?: 5) <= 2) cautions += "דירוג האנרגיה באימון האחרון היה נמוך – ייתכן שהביצועים הושפעו מכך."
            if ((rating.fatigueRating ?: 0) >= 4) cautions += "דווחה עייפות גבוהה באימון האחרון."
        }
        val tired = cautions.isNotEmpty()
        val recent90 = stats.count { ChronoUnit.DAYS.between(it.date, today) in 0..90 }
        val baseConfidence = when {
            stats.size >= 3 && recent90 >= 3 -> Confidence.HIGH
            stats.size >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        if (stats.size == 1) cautions += "ההמלצה מבוססת על אימון אחד בלבד."

        val ctx = Ctx(
            key = key, name = name, evidence = evidenceLines(stats, tracking), baseCautions = cautions,
            baseConfidence = baseConfidence, currentWeight = lastEval.weightKg, basedOn = last.sessionId, sessions = stats.size,
        )

        // A long break: do not push the weight up on the strength of an old workout.
        if (gapDays > LONG_GAP_DAYS) {
            return ctx.make(
                SuggestionKind.HOLD,
                "עבר זמן מאז הביצוע האחרון – כדאי להתחיל מהמשקל האחרון.",
                "האימון האחרון בתרגיל היה לפני $gapDays ימים. אחרי הפסקה ארוכה עדיף לחזור למשקל האחרון ולראות איך זה מרגיש לפני שמעלים.",
                suggestedWeight = lastEval.weightKg, targetMin = rt.repsMin, targetMax = rt.repsMax,
                confidence = Confidence.LOW,
            )
        }

        // Timed holds (plank): progress by seconds, never by weight.
        if (tracking == TrackingType.DURATION) return durationSuggestion(ctx, lastEval, rt)

        if (lastEval.varied) {
            return ctx.make(
                SuggestionKind.HOLD,
                "באימון האחרון נעשה שימוש במשקלים שונים בין הסטים.",
                "כשהמשקל משתנה בין הסטים (פירמידה או עלייה הדרגתית) שיטת Double Progression לא מתאימה להסקת מסקנה אוטומטית. " +
                    "התייחס לסט העבודה הכבד ביותר, ואם תרצה – קבע בעצמך משקל יעד.",
                suggestedWeight = lastEval.weightKg, targetMin = rt.repsMin, targetMax = rt.repsMax,
                confidence = Confidence.LOW,
            )
        }

        val step = IncrementPolicy.stepKg(sample.equipment, lastEval.weightKg, incrementOverrideKg)

        // Two workouts in a row that were worse than the one before: a pattern, not a bad day.
        declinePattern(stats, tracking)?.let { why ->
            return ctx.make(
                SuggestionKind.REVIEW,
                "בשני האימונים האחרונים נרשמה ירידה בביצועים. כדאי לבדוק התאוששות, עומס וטכניקה.",
                "$why אין זה אומר שהכוח ירד – אבל מומלץ לא להעלות משקל עכשיו, ולשקול שבוע קל יותר או יום מנוחה נוסף.",
                suggestedWeight = lastEval.weightKg, targetMin = rt.repsMin, targetMax = rt.repsMax,
                extraCautions = if (tired) emptyList() else listOf("בדוק גם שינה, לחץ וטכניקה – ההמלצה לא מאבחנת סיבה."),
            )
        }

        // Falling short of the bottom of the range twice at the same weight: lower the load a little.
        if (stats.size >= 2 && lastEval.weightKg != null && step != null) {
            val prev = evaluate(stats[stats.size - 2], rt, tracking)
            val sameWeight = prev.weightKg != null && abs(prev.weightKg - lastEval.weightKg) < 0.01
            if (sameWeight && lastEval.belowRange && prev.belowRange) {
                val lower = IncrementPolicy.round((lastEval.weightKg - step).coerceAtLeast(0.0))
                return ctx.make(
                    SuggestionKind.REDUCE_WEIGHT,
                    "ב-2 האימונים האחרונים לא הגעת לתחתית טווח החזרות במשקל הזה.",
                    "בשני האימונים האחרונים, ב-${Formatters.number(lastEval.weightKg)} ק״ג, חלק מהסטים נעצרו מתחת ל-${rt.repsMin} חזרות. " +
                        "הפחתה קטנה ל-${Formatters.number(lower)} ק״ג תאפשר לבנות חזרות מחדש בטכניקה טובה.",
                    suggestedWeight = lower, targetMin = rt.repsMin, targetMax = rt.repsMax,
                )
            }
        }

        // A single weak workout never lowers the target: repeat the weight.
        val missingSets = lastEval.setCount < rt.sets
        if (lastEval.belowRange || (missingSets && !lastEval.allAtTop)) {
            val reasons = buildList {
                if (lastEval.belowRange) add("חלק מהסטים היו מתחת ל-${rt.repsMin} חזרות")
                if (missingSets) add("הושלמו ${lastEval.setCount} מתוך ${rt.sets} סטים")
            }.joinToString(" ו")
            return ctx.make(
                SuggestionKind.HOLD,
                "כדאי לחזור על אותו משקל ולהשלים את הטווח.",
                "באימון האחרון $reasons. אימון בודד פחות מוצלח לא מעיד על ירידה בכוח – " +
                    "חזור על המשקל ונסה להגיע ל-${rt.repsMin}–${rt.repsMax} חזרות בכל ${rt.sets} הסטים.",
                suggestedWeight = lastEval.weightKg, targetMin = rt.repsMin, targetMax = rt.repsMax,
            )
        }

        if (lastEval.allAtTop) {
            val rir = lastEval.avgRir
            if ((rir != null && rir < 1.0) || lastEval.anyFailure) {
                return ctx.make(
                    SuggestionKind.HOLD,
                    "הגעת לטווח העליון, אך קרוב לכישלון – כדאי לחזור עוד פעם על המשקל.",
                    "כל הסטים הגיעו ל-${rt.repsMax} חזרות, אבל הסטים הסתיימו קרוב מאוד לכישלון" +
                        (rir?.let { " (RIR ${Formatters.number(it, 1)})" } ?: "") +
                        ". אימון נוסף על המשקל הזה עם 1–2 חזרות בשמורה יהפוך את העלאת המשקל לבטוחה יותר.",
                    suggestedWeight = lastEval.weightKg, targetMin = rt.repsMin, targetMax = rt.repsMax,
                )
            }
            val unloaded = lastEval.weightKg == null
            if (step == null) {
                return ctx.make(
                    SuggestionKind.HOLD,
                    "הגעת לטווח העליון בכל הסטים.",
                    "אי אפשר להציע קפיצת משקל לציוד הזה. אפשר להקשות באמצעות התנגדות חזקה יותר, טמפו איטי או וריאציה קשה יותר.",
                    targetMin = rt.repsMin, targetMax = rt.repsMax,
                )
            }
            val newWeight = IncrementPolicy.round((lastEval.weightKg ?: 0.0) + step)
            val explanation = if (unloaded) {
                "כל הסטים הגיעו ל-${rt.repsMax} חזרות עם משקל גוף. אפשר להוסיף ${Formatters.number(newWeight)} ק״ג (חגורה או וסט) " +
                    "או לעבור לוריאציה קשה יותר, ולחזור ל-${rt.repsMin} חזרות."
            } else {
                "כל ${lastEval.setCount} הסטים ב-${Formatters.number(lastEval.weightKg)} ק״ג הגיעו ל-${rt.repsMax} חזרות" +
                    (lastEval.avgRir?.let { " עם RIR ממוצע ${Formatters.number(it, 1)}" } ?: "") +
                    ". לפי שיטת Double Progression זה הזמן לקפוץ ל-${Formatters.number(newWeight)} ק״ג ולחזור לתחתית הטווח (${rt.repsMin} חזרות)."
            }
            return ctx.make(
                SuggestionKind.INCREASE_WEIGHT,
                "השלמת את כל הסטים בטווח העליון. אפשר לשקול העלאת משקל.",
                explanation,
                suggestedWeight = newWeight, targetMin = rt.repsMin, targetMax = rt.repsMax,
                confidence = if (tired && baseConfidence == Confidence.HIGH) Confidence.MEDIUM else baseConfidence,
                extraCautions = listOf("העלה רק אם הטכניקה נשארה נקייה בכל החזרות."),
            )
        }

        // Inside the range, not yet at the top everywhere: add repetitions.
        val minReps = lastEval.values.minOrNull() ?: rt.repsMin
        val nextMin = (minReps + 1).coerceAtMost(rt.repsMax)
        val load = lastEval.weightKg?.let { Formatters.number(it) + " ק״ג" } ?: "אותה התנגדות"
        return ctx.make(
            SuggestionKind.ADD_REPS,
            "עדיין לא הגעת ליעד החזרות. כדאי לשמור על המשקל ולנסות להוסיף חזרה.",
            "הסט החלש ביותר באימון האחרון היה $minReps חזרות והיעד הוא ${rt.repsMax}. " +
                "שמור על $load וכוון ל-$nextMin חזרות לפחות בכל סט; כשכל הסטים יגיעו ל-${rt.repsMax} אפשר יהיה להעלות משקל.",
            suggestedWeight = lastEval.weightKg, targetMin = nextMin, targetMax = rt.repsMax,
        )
    }

    private fun durationSuggestion(ctx: Ctx, e: Eval, rt: RepTarget): ProgressionSuggestion {
        val minSec = e.values.minOrNull() ?: rt.repsMin
        return if (e.allAtTop) {
            val next = rt.repsMax + DURATION_STEP_SEC
            ctx.make(
                SuggestionKind.ADD_REPS,
                "הגעת לזמן היעד בכל הסטים.",
                "כל הסטים הגיעו ל-${rt.repsMax} שניות לפחות. אפשר להאריך את היעד ל-$next שניות.",
                targetMin = rt.repsMax, targetMax = next,
            )
        } else {
            val next = (minSec + DURATION_STEP_SEC).coerceAtMost(rt.repsMax)
            ctx.make(
                SuggestionKind.ADD_REPS,
                "כדאי להאריך מעט את זמן ההחזקה.",
                "הסט הקצר ביותר באימון האחרון היה $minSec שניות והיעד הוא ${rt.repsMax}. נסה להגיע ל-$next שניות לפחות בכל סט.",
                targetMin = next, targetMax = rt.repsMax,
            )
        }
    }

    private fun evaluate(stat: ExerciseSessionStat, rt: RepTarget, tracking: TrackingType): Eval {
        val straight = stat.sets.filter { it.isStraightSet && valueOf(it, tracking) > 0 }
        val byWeight = straight.groupBy { Math.round((it.weightKg ?: 0.0) * 100) / 100.0 }
        // The working weight is the most common one; on a tie the heavier wins.
        val (w, rows) = byWeight.entries
            .sortedWith(compareByDescending<Map.Entry<Double, List<SetFact>>> { it.value.size }.thenByDescending { it.key })
            .first().toPair()
        val values = rows.map { valueOf(it, tracking) }
        val setCount = SetCounting.effectiveSets(rows)
        val rirs = rows.mapNotNull { it.rir?.toDouble() ?: it.rpe?.let { r -> 10.0 - r } }
        return Eval(
            stat = stat,
            weightKg = if (w > 0.0) w else null,
            values = values,
            setCount = setCount,
            allAtTop = values.isNotEmpty() && values.all { it >= rt.repsMax } && setCount >= rt.sets,
            belowRange = values.any { it < rt.repsMin },
            anyFailure = rows.any { it.isFailure },
            avgRir = if (rirs.isEmpty()) null else rirs.average(),
            varied = byWeight.size > 1 && rows.size < straight.size * 0.6,
        )
    }

    private fun sessionScore(s: ExerciseSessionStat, tracking: TrackingType): Double = when {
        s.bestE1rmKg != null -> s.bestE1rmKg
        tracking == TrackingType.DURATION -> (s.maxDurationSec ?: 0).toDouble()
        else -> s.totalReps.toDouble()
    }

    /** Text explaining a decline across the last three workouts, or null when there is none. */
    private fun declinePattern(stats: List<ExerciseSessionStat>, tracking: TrackingType): String? {
        if (stats.size < 3) return null
        val (a, b, c) = stats.takeLast(3)
        // A deliberate weight reduction last time is not a decline in performance.
        val wb = b.topWeightKg
        val wc = c.topWeightKg
        if (wb != null && wc != null && wc < wb - 0.01) return null
        val sa = sessionScore(a, tracking)
        val sb = sessionScore(b, tracking)
        val sc = sessionScore(c, tracking)
        if (sa <= 0.0 || sb <= 0.0) return null
        val declined = sb < sa * (1 - DECLINE_TOLERANCE) && sc < sb * (1 - DECLINE_TOLERANCE)
        if (!declined) return null
        val label = if (a.bestE1rmKg != null) "ה-1RM המשוער" else "סך החזרות"
        fun f(v: Double) = Formatters.number(v, 1)
        return "$label ירד ברצף: ${f(sa)} ← ${f(sb)} ← ${f(sc)}."
    }

    private fun evidenceLines(stats: List<ExerciseSessionStat>, tracking: TrackingType): List<String> =
        stats.takeLast(3).map { s ->
            val sets = s.sets.filter { it.isStraightSet && valueOf(it, tracking) > 0 }.joinToString(" · ") { set ->
                if (tracking == TrackingType.DURATION) {
                    "${set.durationSec} שנ׳"
                } else {
                    val w = set.weightKg?.takeIf { it > 0.0 }?.let { Formatters.number(it) + "×" } ?: ""
                    "$w${set.reps}"
                }
            }
            "${Formatters.date(s.date)}: $sets"
        }

    /** Result of comparing a suggestion with what the user already decided about it. */
    enum class Visibility { SHOW, APPLIED, IGNORED }

    /**
     * A decision hides the suggestion only for the workout it was based on: after the next workout of
     * the exercise the suggestion is recomputed and shown again.
     */
    fun visibility(suggestion: ProgressionSuggestion, decisions: List<ProgressionDecision>): Visibility {
        val based = suggestion.basedOnSessionId ?: return Visibility.SHOW
        val d = decisions.filter { it.exerciseKey == suggestion.exerciseKey && it.basedOnSessionId == based }
            .maxByOrNull { it.decidedAt } ?: return Visibility.SHOW
        return when (d.decision) {
            DecisionType.IGNORED -> Visibility.IGNORED
            DecisionType.APPLIED, DecisionType.MANUAL -> Visibility.APPLIED
        }
    }
}
