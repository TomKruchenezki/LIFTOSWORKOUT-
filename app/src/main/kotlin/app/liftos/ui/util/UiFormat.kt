package app.liftos.ui.util

import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.NextReason
import app.liftos.domain.logic.NextWorkout
import java.time.LocalDate

/** U+200E LEFT-TO-RIGHT MARK: keeps "-0.6" from rendering as "0.6-" inside right-to-left text. */
const val LRM = "‎"

fun ltr(text: String): String = LRM + text + LRM

fun kg(v: Double?): String = if (v == null) "-" else ltr(Formatters.number(v)) + " ק״ג"

fun signedKg(v: Double): String {
    val n = Formatters.number(v, 1)
    val s = if (v > 0) "+$n" else if (v < 0) n.replace("-", "−") else n
    return ltr(s) + " ק״ג"
}

fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "היום"
    date == today.plusDays(1) -> "מחר"
    date == today.minusDays(1) -> "אתמול"
    else -> "יום ${app.liftos.domain.logic.TimeUtils.weekdayHe(date)}"
}

fun nextLabel(next: NextWorkout, today: LocalDate): String = when (next.reason) {
    NextReason.TODAY -> "היום"
    NextReason.UPCOMING -> next.date?.let { dayLabel(it, today) } ?: ""
    NextReason.ROTATION -> "הבא בתור"
    NextReason.NONE -> ""
}

/** "13/10/2026" with a weekday: "יום ג׳ · 13/10/2026". */
fun dateLine(date: LocalDate): String = "יום ${app.liftos.domain.logic.TimeUtils.weekdayShortHe(date.dayOfWeek.value)} · ${Formatters.date(date)}"

private val MONTHS_HE = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר",
)

fun monthYear(date: LocalDate): String = "${MONTHS_HE[date.monthValue - 1]} ${date.year}"
