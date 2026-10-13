package app.liftos.ui.util

import app.liftos.domain.analytics.PrEvent
import app.liftos.domain.analytics.RecordEntry
import app.liftos.domain.analytics.RecordKind
import app.liftos.domain.logic.Formatters

/** Hebrew sentences for records, shared by the summary, the records screen and notifications. */
object RecordText {

    private fun n(v: Double) = Formatters.number(v, 1)

    /** "62.5 ק״ג × 5" - the set that made the record. */
    fun valueText(kind: RecordKind, value: Double, weightKg: Double?, reps: Int?): String = when (kind) {
        RecordKind.REPS_AT_WEIGHT -> "${n(value)} חזרות ב-${n(weightKg ?: 0.0)} ק״ג"
        RecordKind.MAX_REPS -> "${n(value)} חזרות"
        RecordKind.MAX_DURATION -> "${n(value)} שניות"
        RecordKind.BEST_VOLUME -> "${Formatters.thousands(value)} ק״ג-חזרות"
        RecordKind.REP_MAX_5, RecordKind.REP_MAX_10 -> "${n(value)} ק״ג" + (reps?.let { " × $it" } ?: "")
        RecordKind.MAX_WEIGHT, RecordKind.ACTUAL_1RM -> "${n(value)} ק״ג" + (reps?.let { " × $it" } ?: "")
        RecordKind.BEST_E1RM -> "${n(value)} ק״ג" + (if (weightKg != null && reps != null) " (מתוך ${n(weightKg)} × $reps)" else "")
    }

    fun describe(e: PrEvent): String {
        val est = if (e.kind.isEstimate) " (הערכה)" else ""
        val label = if (e.kind == RecordKind.REPS_AT_WEIGHT) "" else "${e.kind.labelHe}$est: "
        val prev = if (e.kind == RecordKind.REPS_AT_WEIGHT) "${n(e.previousValue)} חזרות" else previousText(e.kind, e.previousValue)
        return "${e.exerciseName} – $label${valueText(e.kind, e.newValue, e.weightKg, e.reps)} (קודם $prev)"
    }

    fun describe(r: RecordEntry): String = valueText(r.kind, r.value, r.weightKg, r.reps)

    private fun previousText(kind: RecordKind, v: Double) = when (kind) {
        RecordKind.MAX_REPS -> "${n(v)} חזרות"
        RecordKind.MAX_DURATION -> "${n(v)} שניות"
        RecordKind.BEST_VOLUME -> "${Formatters.thousands(v)}"
        else -> "${n(v)} ק״ג"
    }
}
