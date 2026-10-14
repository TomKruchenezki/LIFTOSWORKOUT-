package app.liftos.domain.export

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.ExerciseAnalytics
import app.liftos.domain.analytics.MuscleModel
import app.liftos.domain.analytics.RecordsResult
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetCounting
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.analytics.VolumeAnalytics
import app.liftos.domain.goals.BodyField
import app.liftos.domain.goals.GoalProgress
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.OneRepMax
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.Goal
import app.liftos.domain.model.SetType
import app.liftos.domain.model.TrackingType
import app.liftos.domain.progression.ProgressionSuggestion
import app.liftos.domain.recovery.RecoveryReport
import java.time.LocalDate

/** Which part of the history a report covers. */
enum class ReportScope(val labelHe: String) {
    SINGLE("אימון בודד"),
    THIS_WEEK("כל האימונים השבוע"),
    LAST_MONTH("חודש אחרון"),
    WEEKS_12("12 שבועות אחרונים"),
    CUSTOM("תקופה מותאמת"),
    ALL("כל ההיסטוריה"),
    ;

    fun range(today: LocalDate, earliest: LocalDate?, custom: DateRange?): DateRange = when (this) {
        SINGLE -> DateRange(today, today)
        THIS_WEEK -> DateRange.weekOf(today)
        LAST_MONTH -> DateRange.lastDays(30, today)
        WEEKS_12 -> DateRange.lastDays(84, today)
        CUSTOM -> custom ?: DateRange.lastDays(30, today)
        ALL -> DateRange(minOf(earliest ?: today, today), today)
    }
}

/** What the user chose to include; sensitive blocks (body, notes) are opt-in per export. */
data class ReportOptions(
    val includeBody: Boolean = false,
    val includeNotes: Boolean = true,
    val includeRecovery: Boolean = true,
    val includeGoals: Boolean = false,
    val includeRecords: Boolean = true,
    val includeProgressionHints: Boolean = false,
)

data class ReportInput(
    val range: DateRange,
    val scopeLabel: String,
    val today: LocalDate,
    val facts: List<SetFact>,
    val sessions: List<SessionFact>,
    val records: RecordsResult,
    val measurements: List<BodyMeasurement> = emptyList(),
    val goals: List<Pair<Goal, GoalProgress>> = emptyList(),
    val recovery: RecoveryReport? = null,
    val targetPerWeek: Int = 0,
    val suggestions: List<ProgressionSuggestion> = emptyList(),
)

/**
 * Plain-text report for a period (a week, a month, 12 weeks, everything), to be pasted into ChatGPT or
 * Claude. The app never sends it anywhere: it is copied, shared or exported by the user.
 */
object PeriodReport {
    /** Above this many workouts only the latest ones are listed set by set. */
    const val FULL_DETAIL_LIMIT = 12
    const val RECENT_DETAIL = 4
    private const val MAX_EXERCISES = 25

    fun build(input: ReportInput, options: ReportOptions): String {
        val range = input.range
        val sessions = input.sessions.filter { it.date in range }.sortedBy { it.startedAt }
        val facts = input.facts.filter { it.date in range }
        val sb = StringBuilder()
        sb.appendLine("דוח אימונים מתוך LiftOS – ${input.scopeLabel}")
        sb.appendLine("טווח תאריכים: ${Formatters.date(range.start)} – ${Formatters.date(range.end)} (${range.days} ימים)")
        sb.appendLine("(הנתונים נרשמו ידנית על ידי המתאמן; אין בדוח נתונים שלא הוזנו.)")

        if (sessions.isEmpty()) {
            sb.appendLine()
            sb.appendLine("לא תועדו אימונים בטווח הזה, ולכן אין נתונים לניתוח.")
            appendRequest(sb)
            return sb.toString().trimEnd()
        }

        appendSummary(sb, input, sessions, facts)
        appendMuscles(sb, facts, range)
        appendExercises(sb, facts)
        if (options.includeRecords) appendRecords(sb, input.records, range)
        appendWorkouts(sb, sessions, facts, options)
        if (options.includeRecovery) appendRecovery(sb, sessions, input.recovery)
        if (options.includeProgressionHints && input.suggestions.isNotEmpty()) appendHints(sb, input.suggestions)
        if (options.includeBody) appendBody(sb, input.measurements, range, input.today)
        if (options.includeGoals && input.goals.isNotEmpty()) appendGoals(sb, input.goals)
        appendRequest(sb)
        return sb.toString().trimEnd()
    }

    // ---- sections -----------------------------------------------------------------------------

    private fun appendSummary(sb: StringBuilder, input: ReportInput, sessions: List<SessionFact>, facts: List<SetFact>) {
        val cmp = VolumeAnalytics.compare(input.facts, input.sessions, input.range)
        val cur = cmp.current
        sb.appendLine()
        sb.appendLine("## סיכום התקופה")
        sb.appendLine("אימונים: ${cur.workouts} · ימי אימון: ${cur.trainedDays} · סטי עבודה: ${cur.workingSets} · חזרות: ${cur.totalReps}")
        sb.appendLine("נפח כולל: ${Formatters.thousands(cur.volumeKg)} ק״ג-חזרות (משקל × חזרות, סטי עבודה בלבד, ללא חימום)")
        cur.avgDurationMs?.let { sb.appendLine("משך אימון ממוצע: ${it / 60_000} דקות") }
        if (input.targetPerWeek > 0) {
            val expected = Math.round(input.targetPerWeek * input.range.weeks()).toInt()
            sb.appendLine("עמידה בתוכנית: ${cur.workouts} אימונים מתוך כ-$expected מתוכננים (יעד ${input.targetPerWeek} בשבוע)")
        }
        val prev = cmp.previous
        if (prev.workouts > 0) {
            sb.appendLine("בהשוואה לתקופה הקודמת באותו אורך (${Formatters.date(prev.range.start)} – ${Formatters.date(prev.range.end)}):")
            sb.appendLine("  אימונים ${delta(cmp.workouts.delta, 0)}, סטים ${delta(cmp.workingSets.delta, 0)}, נפח ${delta(cmp.volumeKg.delta, 0)} ק״ג-חזרות" +
                (cmp.volumeKg.percent?.let { " (${delta(it, 1)}%)" } ?: ""))
        } else {
            sb.appendLine("אין נתונים בתקופה הקודמת באותו אורך, ולכן אין השוואה.")
        }
    }

    private fun appendMuscles(sb: StringBuilder, facts: List<SetFact>, range: DateRange) {
        val loads = VolumeAnalytics.muscleLoads(facts, range)
        if (loads.isEmpty()) return
        sb.appendLine()
        sb.appendLine("## סטים לפי קבוצת שרירים")
        val weeks = range.weeks().coerceAtLeast(1.0)
        loads.filter { it.directSets > 0 }.forEach {
            sb.appendLine("- ${it.muscle.labelHe}: ${it.directSets} סטים ישירים (כ-${Formatters.number(it.directSets / weeks, 1)} בשבוע) · ${it.workouts} אימונים")
        }
        val indirect = loads.filter { it.indirectSets > 0 }
        if (indirect.isNotEmpty()) {
            sb.appendLine("הערכה לסטים עקיפים (שריר משני = חצי סט): " +
                indirect.joinToString(", ") { "${it.muscle.labelHe} ${Formatters.number(it.indirectSets, 1)}" })
        }
        sb.appendLine("(${MuscleModel.ASSUMPTIONS})")
    }

    private fun appendExercises(sb: StringBuilder, facts: List<SetFact>) {
        val rows = facts.filter { it.isWorking }.groupBy { it.key }.values
            .map { r -> r to ExerciseAnalytics.sessionStats(r) }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { (_, stats) -> stats.sumOf { it.volumeKg } }
            .take(MAX_EXERCISES)
        if (rows.isEmpty()) return
        sb.appendLine()
        sb.appendLine("## התקדמות לפי תרגיל")
        for ((r, stats) in rows) {
            val first = stats.first()
            val last = stats.last()
            val name = r.first().exerciseName
            val volume = stats.sumOf { it.volumeKg }
            val sets = stats.sumOf { it.workingSets }
            val line = StringBuilder("- $name: ${stats.size} אימונים · $sets סטים · נפח ${Formatters.thousands(volume)}")
            if (stats.size >= 2) {
                line.append(" · סט מוביל ${top(first, r.first().trackingType)} ← ${top(last, r.first().trackingType)}")
                if (first.bestE1rmKg != null && last.bestE1rmKg != null) {
                    line.append(" · 1RM משוער ${Formatters.number(first.bestE1rmKg, 1)} ← ${Formatters.number(last.bestE1rmKg, 1)} ק״ג (הערכה)")
                }
            } else {
                line.append(" · סט מוביל ${top(last, r.first().trackingType)} (אימון אחד בלבד – אין בסיס למגמה)")
            }
            sb.appendLine(line)
        }
        sb.appendLine("(${OneRepMax.EXPLANATION})")
    }

    private fun top(s: app.liftos.domain.analytics.ExerciseSessionStat, tracking: TrackingType): String = when {
        tracking == TrackingType.DURATION -> "${s.maxDurationSec ?: 0} שנ׳"
        s.topWeightKg != null && s.topWeightKg > 0 -> "${Formatters.number(s.topWeightKg)}×${s.repsAtTopWeight ?: 0}"
        else -> "${s.maxReps ?: 0} חזרות"
    }

    private fun appendRecords(sb: StringBuilder, records: RecordsResult, range: DateRange) {
        val events = records.events.filter { it.date in range }
        sb.appendLine()
        sb.appendLine("## שיאים אישיים חדשים בתקופה")
        if (events.isEmpty()) {
            sb.appendLine("לא נשברו שיאים בתקופה.")
            return
        }
        events.sortedWith(compareBy({ it.date }, { it.exerciseName }, { it.kind.priority })).take(40).forEach {
            sb.appendLine("- ${Formatters.date(it.date)} · ${RecordLines.line(it)}")
        }
    }

    private fun setText(f: SetFact): String {
        val core = when {
            f.trackingType == TrackingType.DURATION -> "${f.durationSec ?: 0}ש׳"
            f.weightKg != null && f.weightKg > 0 -> "${Formatters.number(f.weightKg)}×${f.reps ?: 0}"
            else -> "גוף×${f.reps ?: 0}"
        }
        val extra = buildList {
            f.rir?.let { add("RIR $it") }
            f.rpe?.let { add("RPE ${Formatters.number(it, 1)}") }
            if (f.isFailure) add("כשל")
            if (f.setType == SetType.DROP) add("דרופ")
            f.side?.let { add(it.shortHe) }
        }
        return if (extra.isEmpty()) core else "$core (${extra.joinToString(", ")})"
    }

    private fun appendWorkouts(sb: StringBuilder, sessions: List<SessionFact>, facts: List<SetFact>, options: ReportOptions) {
        sb.appendLine()
        sb.appendLine("## האימונים")
        val detailFrom = if (sessions.size <= FULL_DETAIL_LIMIT) 0 else sessions.size - RECENT_DETAIL
        if (detailFrom > 0) sb.appendLine("(עד האימון ה-$detailFrom מוצגת שורת סיכום בלבד; ${RECENT_DETAIL} האחרונים מפורטים סט אחר סט.)")
        sessions.forEachIndexed { index, s ->
            val rows = facts.filter { it.sessionId == s.id }
            val working = rows.filter { it.isWorking }
            val head = "### ${Formatters.date(s.date)} · ${s.title}" + (s.durationMs?.let { " · ${it / 60_000} דקות" } ?: "")
            val ratings = buildList {
                s.energyRating?.let { add("אנרגיה $it/5") }
                s.moodRating?.let { add("הרגשה $it/5") }
                s.difficultyRating?.let { add("קושי $it/5") }
                s.qualityRating?.let { add("איכות $it/5") }
                s.fatigueRating?.let { add("עייפות $it/5") }
                s.sorenessRating?.let { add("כאבי שרירים $it/5") }
                s.sleepHours?.let { add("שינה ${Formatters.number(it, 1)} שעות") }
            }
            if (index < detailFrom) {
                sb.appendLine(
                    "- ${Formatters.date(s.date)} ${s.title}: ${SetCounting.effectiveWorkingSets(working)} סטים · נפח ${Formatters.thousands(working.sumOf { it.volumeKg })}" +
                        (if (ratings.isNotEmpty()) " · ${ratings.joinToString(", ")}" else ""),
                )
                return@forEachIndexed
            }
            sb.appendLine(head)
            sb.appendLine("סטי עבודה: ${SetCounting.effectiveWorkingSets(working)} · נפח: ${Formatters.thousands(working.sumOf { it.volumeKg })} ק״ג-חזרות")
            if (ratings.isNotEmpty()) sb.appendLine("דירוגים: ${ratings.joinToString(" · ")}")
            if (options.includeNotes && s.notes.isNotBlank()) sb.appendLine("הערות: ${s.notes}")
            rows.groupBy { it.sessionExerciseId }.values.sortedBy { it.first().exerciseOrder }.forEach { ex ->
                val warm = ex.filter { !it.isWorking }.sortedBy { it.setOrder }
                val work = ex.filter { it.isWorking }.sortedBy { it.setOrder }
                val name = ex.first().exerciseName
                val parts = StringBuilder("- $name: ${work.joinToString(" · ") { setText(it) }}")
                if (warm.isNotEmpty()) parts.append(" | חימום: ${warm.joinToString(" · ") { setText(it) }}")
                sb.appendLine(parts)
            }
        }
    }

    private fun appendRecovery(sb: StringBuilder, sessions: List<SessionFact>, recovery: RecoveryReport?) {
        val energy = sessions.mapNotNull { it.energyRating }
        sb.appendLine()
        sb.appendLine("## התאוששות ועומס")
        if (energy.isNotEmpty()) {
            sb.appendLine("דירוג אנרגיה ממוצע בתקופה: ${Formatters.number(energy.average(), 1)}/5 (${energy.size} אימונים מדורגים)")
        } else {
            sb.appendLine("לא הוזנו דירוגי אנרגיה בתקופה.")
        }
        if (recovery != null) {
            sb.appendLine("אימונים ב-7 הימים האחרונים: ${recovery.workoutsLast7Days} · ימי מנוחה: ${recovery.restDaysLast7Days}")
            if (recovery.signals.isEmpty()) {
                sb.appendLine(recovery.summaryLine)
            } else {
                recovery.signals.forEach { sb.appendLine("- ${it.text}") }
                recovery.deloadHint?.let { sb.appendLine(it) }
            }
            sb.appendLine("(${app.liftos.domain.recovery.RecoveryAnalytics.DISCLAIMER})")
        }
    }

    private fun appendHints(sb: StringBuilder, hints: List<ProgressionSuggestion>) {
        sb.appendLine()
        sb.appendLine("## הצעות התקדמות מהחישוב המקומי של האפליקציה (חוקים, לא AI)")
        hints.take(20).forEach {
            val w = it.suggestedWeightKg?.let { w -> " → ${Formatters.number(w)} ק״ג" }.orEmpty()
            sb.appendLine("- ${it.exerciseName}: ${it.kind.labelHe}$w (${it.confidence.labelHe}) – ${it.explanation}")
        }
    }

    private fun appendBody(sb: StringBuilder, all: List<BodyMeasurement>, range: DateRange, today: LocalDate) {
        sb.appendLine()
        sb.appendLine("## מדדי גוף (צורפו לפי בחירת המתאמן)")
        val inRange = all.filter { m -> app.liftos.domain.logic.TimeUtils.parseDate(m.localDate)?.let { it in range } == true }
        if (inRange.isEmpty() && all.isEmpty()) {
            sb.appendLine("לא קיימות מדידות.")
            return
        }
        for (field in BodyField.entries) {
            val pts = app.liftos.domain.body.BodyAnalytics.series(all, field, range)
            if (pts.isEmpty()) continue
            val first = pts.first()
            val last = pts.last()
            val change = if (pts.size >= 2) " · שינוי ${delta(last.value - first.value, 1)} ${field.unit}" else ""
            sb.appendLine("- ${field.labelHe}: ${Formatters.number(last.value)} ${field.unit} (${pts.size} מדידות${change})")
        }
        BodyTrends.weightChange(all, 7)?.let { sb.appendLine("שינוי משקל ב-7 ימים: ${delta(it.deltaKg, 1)} ק״ג") }
        BodyTrends.weightChange(all, 30)?.let { sb.appendLine("שינוי משקל ב-30 ימים: ${delta(it.deltaKg, 1)} ק״ג") }
        sb.appendLine("(${app.liftos.domain.body.BodyAnalytics.CAVEAT})")
    }

    private fun appendGoals(sb: StringBuilder, goals: List<Pair<Goal, GoalProgress>>) {
        sb.appendLine()
        sb.appendLine("## יעדים")
        goals.forEach { (g, p) ->
            val cur = p.current?.let { Formatters.number(it, 1) } ?: "אין נתונים"
            val pct = p.percent?.let { " (${Formatters.number(it, 0)}%)" }.orEmpty()
            sb.appendLine("- ${g.title}: מ-${Formatters.number(g.startValue, 1)} ליעד ${Formatters.number(g.targetValue, 1)} ${g.unit} · כעת $cur$pct")
        }
    }

    private fun appendRequest(sb: StringBuilder) {
        sb.appendLine()
        sb.appendLine("## בקשה")
        sb.appendLine(TextReport.AI_INSTRUCTION)
    }

    private fun delta(v: Double, decimals: Int): String {
        val n = Formatters.number(v, decimals)
        return if (v > 0) "+$n" else n
    }
}
