package app.liftos.domain.export

import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.OneRepMax
import app.liftos.domain.logic.SessionComparator
import app.liftos.domain.logic.SetEditing
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.logic.Verdict
import app.liftos.domain.logic.VolumeCalculator
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.SetType
import app.liftos.domain.model.TrackingType
import app.liftos.domain.model.WorkoutDetail

/** Body data the user explicitly chose to attach to an AI report. */
data class BodySnapshot(
    val latest: BodyMeasurement?,
    val change7Days: BodyTrends.WeightChange?,
    val change30Days: BodyTrends.WeightChange?,
)

/**
 * Plain-text workout reports. The same text is used for "copy summary" and as the base of the
 * report the user pastes into ChatGPT / Claude. Nothing here is sent anywhere by the app.
 */
object TextReport {

    const val AI_INSTRUCTION =
        "נתח את האימון שלי מבחינת נפח, עצימות, התקדמות, התאוששות ובחירת התרגילים. " +
            "השווה לנתונים הקודמים כשיש בסיס לכך. הסבר מה עבד, מה כדאי לשפר והצע יעדים ריאליים לאימון הבא. " +
            "אל תמציא מידע שלא מופיע בדוח."

    /** Readable summary of one workout, optionally followed by a short comparison to [previous]. */
    fun workout(detail: WorkoutDetail, previous: WorkoutDetail? = null): String {
        val sb = StringBuilder()
        appendHeader(sb, detail)
        appendTotals(sb, detail)
        sb.appendLine()
        sb.appendLine("פירוט תרגילים")
        appendExercises(sb, detail)
        if (previous != null) {
            sb.appendLine()
            appendComparison(sb, detail, previous)
        }
        return sb.toString().trimEnd()
    }

    /** Full report for pasting into an AI chat: current workout, previous workout, optional body data, request. */
    fun aiReport(detail: WorkoutDetail, previous: WorkoutDetail?, body: BodySnapshot?): String {
        val sb = StringBuilder()
        sb.appendLine("דוח אימון מתוך LiftOS")
        sb.appendLine("(הנתונים נרשמו ידנית על ידי המתאמן; אין בדוח נתונים שלא הוזנו.)")
        sb.appendLine()
        sb.appendLine("## האימון הנוכחי")
        appendHeader(sb, detail)
        appendTotals(sb, detail)
        sb.appendLine()
        sb.appendLine("### פירוט תרגילים")
        appendExercises(sb, detail)

        sb.appendLine()
        if (previous != null) {
            sb.appendLine("## האימון הקודם מאותו סוג")
            appendHeader(sb, previous)
            appendTotals(sb, previous)
            sb.appendLine()
            sb.appendLine("### פירוט תרגילים")
            appendExercises(sb, previous)
            sb.appendLine()
            appendComparison(sb, detail, previous)
        } else {
            sb.appendLine("## האימון הקודם מאותו סוג")
            sb.appendLine("אין אימון קודם מאותו סוג במערכת, ולכן אין בסיס להשוואה.")
        }

        if (body != null) {
            sb.appendLine()
            sb.appendLine("## מדדי גוף (צורפו לפי בחירת המתאמן)")
            appendBody(sb, body)
        }

        sb.appendLine()
        sb.appendLine("## בקשה")
        sb.appendLine(AI_INSTRUCTION)
        return sb.toString().trimEnd()
    }

    // ---- sections -----------------------------------------------------------------------------

    private fun appendHeader(sb: StringBuilder, d: WorkoutDetail) {
        val s = d.session
        val zone = TimeUtils.zone(s.zoneId)
        val date = TimeUtils.parseDate(s.localDate)
        sb.appendLine("אימון: ${s.title}")
        if (s.planName.isNotBlank()) sb.appendLine("תוכנית: ${s.planName}")
        sb.append("תאריך: ").appendLine(date?.let { Formatters.dateWithWeekday(it) } ?: s.localDate)
        val end = s.endedAt?.let { " · סיום ${Formatters.time(it, zone)}" }.orEmpty()
        sb.appendLine("התחלה ${Formatters.time(s.startedAt, zone)}$end")
        s.durationMs?.let { sb.appendLine("משך האימון: ${Formatters.duration(it)}") }
        s.energyRating?.let { sb.appendLine("דירוג אנרגיה: $it/5") }
        s.moodRating?.let { sb.appendLine("הרגשה כללית: $it/5") }
        if (s.notes.isNotBlank()) sb.appendLine("הערות לאימון: ${s.notes}")
    }

    private fun appendTotals(sb: StringBuilder, d: WorkoutDetail) {
        val t = VolumeCalculator.totals(d)
        sb.appendLine(
            "סיכום: נפח ${Formatters.thousands(t.volumeKg)} ק״ג-חזרות (משקל × חזרות, ללא חימום) · " +
                "${t.workingSets} סטי עבודה · ${t.warmupSets} סטי חימום · ${t.totalReps} חזרות · ${t.exercisesPerformed} תרגילים",
        )
    }

    private fun appendExercises(sb: StringBuilder, d: WorkoutDetail) {
        d.exercises.sortedBy { it.exercise.orderIndex }.forEachIndexed { i, ex ->
            val e = ex.exercise
            val en = if (e.nameEn.isNotBlank()) " (${e.nameEn})" else ""
            sb.appendLine("${i + 1}. ${e.nameHe}$en - ${e.primaryMuscle.labelHe}")
            if (e.skipped) {
                val reason = if (e.skipReason.isNotBlank()) ": ${e.skipReason}" else ""
                sb.appendLine("   דולג$reason")
            }
            val unit = if (e.trackingType == TrackingType.DURATION) "שניות" else "חזרות"
            if (e.plannedSets > 0 && e.repsMin != null && e.repsMax != null) {
                val side = if (e.isUnilateral) " לכל צד" else ""
                val range = if (e.repsMin == e.repsMax) "${e.repsMin}" else "${e.repsMin}-${e.repsMax}"
                sb.appendLine("   יעד: ${e.plannedSets} סטים × $range $unit$side · מנוחה מתוכננת ${e.restSeconds} שניות")
            }
            val labels = SetEditing.labels(e, ex.sets)
            ex.sets.sortedBy { it.orderIndex }.forEach { set ->
                val label = labels[set.id]
                val kind = if (set.setType == SetType.WARMUP) "חימום" else "סט"
                val number = label?.number?.removePrefix("ח").orEmpty()
                val side = label?.side?.let { " (${it.labelHe})" }.orEmpty()
                sb.appendLine("   $kind $number$side: ${setText(ex, set)}")
            }
            if (e.notes.isNotBlank()) sb.appendLine("   הערות לתרגיל: ${e.notes}")
        }
    }

    internal fun setText(ex: ExerciseWithSets, set: ExerciseSet): String {
        val parts = mutableListOf<String>()
        when (ex.exercise.trackingType) {
            TrackingType.DURATION -> parts += "${set.durationSec ?: 0} שניות"
            TrackingType.BODYWEIGHT_REPS -> {
                val w = set.weightKg
                val load = if (w == null || w == 0.0) "משקל גוף" else "+${Formatters.number(w)} ק״ג"
                parts += "$load × ${set.reps ?: 0}"
            }
            TrackingType.WEIGHT_REPS -> {
                val w = set.weightKg
                parts += if (w == null) "ללא משקל × ${set.reps ?: 0}" else "${Formatters.number(w)} ק״ג × ${set.reps ?: 0}"
            }
        }
        set.rir?.let { parts += "RIR $it" }
        set.rpe?.let { parts += "RPE ${Formatters.number(it, 1)}" }
        if (set.isFailure) parts += "עד כשל"
        if (set.setType == SetType.DROP) parts += "דרופ סט"
        if (set.notes.isNotBlank()) parts += "הערה: ${set.notes}"
        if (!set.isCompleted) parts += "לא הושלם"
        return parts.joinToString(" · ")
    }

    private fun appendComparison(sb: StringBuilder, current: WorkoutDetail, previous: WorkoutDetail) {
        val c = SessionComparator.compare(current, previous)
        sb.appendLine("## השוואה לאימון הקודם מאותו סוג (${c.previousDate})")
        val pct = c.volumePercent?.let { " (${signed(it, 1)}%)" }.orEmpty()
        sb.appendLine("נפח: ${signed(c.volumeDeltaKg, 0)} ק״ג-חזרות$pct")
        sb.appendLine("סטי עבודה: ${signedInt(c.workingSetsDelta)}")
        c.durationDeltaMs?.let { sb.appendLine("משך: ${signedInt((it / 60_000).toInt())} דקות") }
        c.exercises.forEach { ec ->
            val verdict = when (ec.verdict) {
                Verdict.NEW -> "חדש (אין נתוני עבר)"
                Verdict.UP -> "שיפור"
                Verdict.SAME -> "ללא שינוי"
                Verdict.DOWN -> "ירידה"
            }
            val cur = ec.current?.let { topText(it) } ?: "-"
            val prev = ec.previous?.let { topText(it) } ?: "-"
            sb.appendLine("- ${ec.nameHe}: הסט הטוב ביותר $prev ← $cur · $verdict")
        }
        sb.appendLine("(הערכת שיפור לפי ${OneRepMax.EXPLANATION})")
    }

    private fun topText(s: ExerciseSet): String =
        "${Formatters.number(s.weightKg)}×${s.reps ?: 0}"

    private fun appendBody(sb: StringBuilder, body: BodySnapshot) {
        val m = body.latest
        if (m == null) {
            sb.appendLine("לא קיימות מדידות.")
            return
        }
        sb.appendLine("מדידה אחרונה: ${m.localDate}")
        m.weightKg?.let { sb.appendLine("משקל: ${Formatters.number(it)} ק״ג") }
        listOf(
            "היקף מותניים" to m.waistCm, "היקף חזה" to m.chestCm, "היקף זרוע" to m.armCm,
            "היקף ירך" to m.thighCm, "היקף אגן" to m.hipsCm, "היקף צוואר" to m.neckCm,
        ).forEach { (label, v) -> v?.let { sb.appendLine("$label: ${Formatters.number(it)} ס״מ") } }
        m.bodyFatPct?.let { sb.appendLine("אחוז שומן: ${Formatters.number(it)}%") }
        body.change7Days?.let { sb.appendLine("שינוי משקל ב-7 ימים: ${signed(it.deltaKg, 1)} ק״ג") }
        body.change30Days?.let { sb.appendLine("שינוי משקל ב-30 ימים: ${signed(it.deltaKg, 1)} ק״ג") }
        sb.appendLine("(שינויי משקל קצרי טווח עשויים לנבוע מנוזלים, מזון או תוספים ואינם מעידים בהכרח על שומן או שריר.)")
        if (m.notes.isNotBlank()) sb.appendLine("הערות: ${m.notes}")
    }

    private fun signed(v: Double, decimals: Int): String {
        val n = Formatters.number(v, decimals)
        return if (v > 0) "+$n" else n
    }

    private fun signedInt(v: Int): String = if (v > 0) "+$v" else v.toString()
}
