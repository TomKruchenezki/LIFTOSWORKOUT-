package app.liftos.domain.recovery

import app.liftos.domain.analytics.DashboardAnalytics
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetCounting
import app.liftos.domain.analytics.SetFact
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SignalKind {
    ENERGY_FALLING,
    ENERGY_LOW,
    FATIGUE_HIGH,
    SORENESS_HIGH,
    SHORT_SLEEP,
    PERFORMANCE_DIP,
    LOAD_SPIKE,
    NO_REST,
}

data class RecoverySignal(val kind: SignalKind, val text: String)

data class WeeklyLoad(val range: DateRange, val workouts: Int, val workingSets: Int, val volumeKg: Double)

data class RecoveryReport(
    val today: LocalDate,
    val workoutsLast7Days: Int,
    val restDaysLast7Days: Int,
    val workoutsLast28Days: Int,
    /** Newest last. Rolling 7-day windows ending today, 4 of them. */
    val weeklyLoads: List<WeeklyLoad>,
    val ratedEnergySessions: Int,
    val avgEnergyRecent: Double?,
    val avgEnergyBefore: Double?,
    val avgFatigueRecent: Double?,
    val avgSleepRecent: Double?,
    val signals: List<RecoverySignal>,
    val deloadHint: String?,
    val enoughData: Boolean,
) {
    /** What to tell the user when there is nothing to report. */
    val summaryLine: String
        get() = when {
            !enoughData -> "נדרשים אימונים נוספים כדי לזהות מגמה."
            signals.isEmpty() -> "לא זוהו אותות חריגים בנתונים שהזנת."
            else -> "זוהו ${signals.size} אותות שכדאי לשים לב אליהם."
        }
}

/**
 * Simple, transparent rules over what the user logged (energy, fatigue, sleep, frequency, load, strength
 * trend). They produce *signals to look at*, never a diagnosis: no medical claims, no "overtraining".
 */
object RecoveryAnalytics {
    const val DISCLAIMER =
        "אלה חישובים פשוטים על הנתונים שהזנת – לא אבחנה רפואית ולא קביעה שיש אימון יתר. " +
            "אם יש כאב חריג, סחרחורת או תסמינים מדאיגים, פנה לאיש מקצוע."

    fun report(
        facts: List<SetFact>,
        sessions: List<SessionFact>,
        today: LocalDate,
    ): RecoveryReport {
        val last7 = DateRange.lastDays(7, today)
        val last28 = DateRange.lastDays(28, today)
        val inLast28 = sessions.filter { it.date in last28 }
        val trainedDates = sessions.filter { it.date in last7 }.map { it.date }.toSet()
        val restDays = (0 until 7).map { today.minusDays(it.toLong()) }.count { it !in trainedDates }

        val loads = (3 downTo 0).map { i ->
            val end = today.minusDays(7L * i)
            val range = DateRange(end.minusDays(6), end)
            val f = facts.filter { it.isWorking && it.date in range }
            WeeklyLoad(range, sessions.count { it.date in range }, SetCounting.effectiveWorkingSets(f), f.sumOf { it.volumeKg })
        }

        val rated = sessions.filter { it.energyRating != null }.sortedBy { it.startedAt }
        val recentEnergy = rated.takeLast(3).map { it.energyRating!!.toDouble() }
        val beforeEnergy = rated.dropLast(3).takeLast(3).map { it.energyRating!!.toDouble() }
        val avgRecent = recentEnergy.takeIf { it.isNotEmpty() }?.average()
        val avgBefore = beforeEnergy.takeIf { it.size >= 3 }?.average()

        val fatigue = sessions.filter { it.fatigueRating != null }.sortedBy { it.startedAt }.takeLast(3).map { it.fatigueRating!!.toDouble() }
        val soreness = sessions.filter { it.sorenessRating != null }.sortedBy { it.startedAt }.takeLast(3).map { it.sorenessRating!!.toDouble() }
        val sleep = sessions.filter { it.sleepHours != null }.sortedBy { it.startedAt }.takeLast(5).map { it.sleepHours!! }

        val signals = mutableListOf<RecoverySignal>()
        if (rated.size >= 6 && avgRecent != null && avgBefore != null && avgRecent <= avgBefore - 1.0) {
            signals += RecoverySignal(
                SignalKind.ENERGY_FALLING,
                "דירוגי האנרגיה ירדו: ממוצע ${one(avgBefore)} ב-3 האימונים שלפני כן ← ${one(avgRecent)} ב-3 האחרונים.",
            )
        }
        if (recentEnergy.size >= 3 && recentEnergy.all { it <= 2.0 }) {
            signals += RecoverySignal(SignalKind.ENERGY_LOW, "שלושת האימונים האחרונים דורגו באנרגיה נמוכה (2 או פחות).")
        }
        if (fatigue.size >= 2 && fatigue.average() >= 4.0) {
            signals += RecoverySignal(SignalKind.FATIGUE_HIGH, "דווחה עייפות גבוהה באימונים האחרונים (ממוצע ${one(fatigue.average())} מתוך 5).")
        }
        if (soreness.size >= 2 && soreness.average() >= 4.0) {
            signals += RecoverySignal(SignalKind.SORENESS_HIGH, "דווחו כאבי שרירים גבוהים באימונים האחרונים (ממוצע ${one(soreness.average())} מתוך 5).")
        }
        if (sleep.size >= 3 && sleep.average() < 6.0) {
            signals += RecoverySignal(SignalKind.SHORT_SLEEP, "משך השינה שהוזן בימים האחרונים קצר: ממוצע ${one(sleep.average())} שעות.")
        }
        val trend = DashboardAnalytics.strengthTrend(facts, today)
        if (trend.tracked >= 3 && trend.declined >= 2 && trend.improved == 0) {
            signals += RecoverySignal(
                SignalKind.PERFORMANCE_DIP,
                "ב-${trend.declined} מתוך ${trend.tracked} תרגילים הביצועים נמוכים מ-4 השבועות שלפני כן, ובאף תרגיל לא נרשם שיפור.",
            )
        }
        val prevAvg = loads.take(3).map { it.volumeKg }.average()
        val prevAllTrained = loads.take(3).all { it.workouts > 0 }
        val current = loads.last()
        if (prevAllTrained && prevAvg > 0.0 && current.volumeKg >= prevAvg * 1.5) {
            signals += RecoverySignal(
                SignalKind.LOAD_SPIKE,
                "נפח 7 הימים האחרונים (${app.liftos.domain.logic.Formatters.thousands(current.volumeKg)} ק״ג-חזרות) גבוה ב-" +
                    "${Math.round((current.volumeKg / prevAvg - 1) * 100)}% מהממוצע של 3 השבועות שלפניו.",
            )
        }
        if (consecutiveTrainingDays(sessions.map { it.date }.toSet(), today) >= 5 || trainedDates.size >= 6) {
            signals += RecoverySignal(SignalKind.NO_REST, "אימנת ${maxOf(trainedDates.size, consecutiveTrainingDays(sessions.map { it.date }.toSet(), today))} ימים כמעט ברצף – מעט ימי מנוחה.")
        }

        val deload = if (signals.map { it.kind }.distinct().size >= 2) {
            "כמה אותות מצביעים באותו כיוון. שווה לשקול שבוע עם נפח מופחת (Deload) – למשל כ-60%-70% מהסטים – ולראות איך הגוף מגיב. זו הצעה כללית ולא אבחנה."
        } else null

        return RecoveryReport(
            today = today,
            workoutsLast7Days = sessions.count { it.date in last7 },
            restDaysLast7Days = restDays,
            workoutsLast28Days = inLast28.size,
            weeklyLoads = loads,
            ratedEnergySessions = rated.size,
            avgEnergyRecent = avgRecent,
            avgEnergyBefore = avgBefore,
            avgFatigueRecent = fatigue.takeIf { it.isNotEmpty() }?.average(),
            avgSleepRecent = sleep.takeIf { it.isNotEmpty() }?.average(),
            signals = signals,
            deloadHint = deload,
            enoughData = inLast28.size >= 4,
        )
    }

    private fun consecutiveTrainingDays(trained: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in trained) today else today.minusDays(1)
        var n = 0
        while (day in trained) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    private fun one(v: Double) = app.liftos.domain.logic.Formatters.number(v, 1)

    /** Days since the last workout, or null if there never was one. */
    fun daysSinceLastWorkout(sessions: List<SessionFact>, today: LocalDate): Long? =
        sessions.maxOfOrNull { it.date }?.let { ChronoUnit.DAYS.between(it, today) }
}
