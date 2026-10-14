package app.liftos.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.calendar.Frequency
import app.liftos.domain.calendar.RecurrenceRule
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.PlanDetail
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.IntField
import app.liftos.ui.components.TimePickerSheet
import app.liftos.ui.util.ltr
import java.time.LocalDate
import java.time.ZoneId

/** Weekdays in the order the week is shown (Sunday first), as ISO numbers. */
private val WEEK_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

private enum class EndMode(val labelHe: String) {
    NEVER("ללא סוף"),
    UNTIL("עד תאריך"),
    COUNT("מספר פעמים"),
}

/**
 * Create or edit an event of any type. Repeating events keep ONE row plus a repeat rule; single
 * occurrences are moved or cancelled through the exceptions list (see ActivityRepository).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorSheet(
    initial: ActivityEvent?,
    initialDate: LocalDate,
    plan: PlanDetail?,
    sessions: List<SessionFact>,
    onSave: (ActivityEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val startZone = remember(initial) { initial?.let { TimeUtils.zone(it.zoneId) } ?: zone }
    val startDate = remember(initial) { initial?.let { TimeUtils.parseDate(it.localDate) } ?: initialDate }
    val startMinutes = remember(initial) {
        initial?.takeIf { !it.allDay }?.let { app.liftos.domain.calendar.CalendarBuilder.minutesOfDay(it.startsAt, startZone) } ?: (18 * 60)
    }
    val rule = remember(initial) { initial?.let { RecurrenceRule.parse(it.recurrence) } }

    var type by remember { mutableStateOf(initial?.type ?: ActivityType.STRENGTH) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var date by remember { mutableStateOf(startDate) }
    var hour by remember { mutableStateOf(startMinutes / 60) }
    var minute by remember { mutableStateOf(startMinutes % 60) }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }
    var duration by remember { mutableStateOf(initial?.durationMin ?: if (initial == null) 60 else null) }
    var status by remember { mutableStateOf(initial?.status ?: if (initialDate.isAfter(today)) EventStatus.PLANNED else EventStatus.DONE) }
    var plannedWorkoutId by remember { mutableStateOf(initial?.plannedWorkoutId) }
    var linkedSessionId by remember { mutableStateOf(initial?.linkedSessionId) }

    var frequency by remember { mutableStateOf(rule?.frequency) }
    var interval by remember { mutableStateOf<Int?>(rule?.interval ?: 1) }
    var weekdays by remember { mutableStateOf(rule?.weekdays?.toSet() ?: emptySet()) }
    var endMode by remember {
        mutableStateOf(
            when {
                rule?.until != null -> EndMode.UNTIL
                rule?.count != null -> EndMode.COUNT
                else -> EndMode.NEVER
            },
        )
    }
    var until by remember { mutableStateOf(rule?.until ?: startDate.plusMonths(3)) }
    var count by remember { mutableStateOf<Int?>(rule?.count ?: 10) }

    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }

    val nearSessions = remember(sessions, date) {
        sessions.filter { s -> kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(s.date, date)) <= 7 }
            .sortedBy { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(it.date, date)) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "אירוע חדש" else "עריכת אירוע", style = MaterialTheme.typography.titleLarge)

            Text("סוג האירוע", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityType.entries.forEach { t ->
                    FilterChip(type == t, { type = t; if (t == ActivityType.REST) allDay = true }, { Text(t.labelHe) })
                }
            }

            if (type == ActivityType.STRENGTH && plan != null) {
                Text("אימון מהתוכנית", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    plan.workouts.forEach { w ->
                        FilterChip(
                            plannedWorkoutId == w.workout.id,
                            { plannedWorkoutId = w.workout.id; if (title.isBlank()) title = w.workout.name },
                            { Text(w.workout.name) },
                        )
                    }
                    FilterChip(plannedWorkoutId == null, { plannedWorkoutId = null }, { Text("ללא קישור לתוכנית") })
                }
            }

            if (rule != null) {
                Text(
                    "זה אירוע חוזר: השינויים כאן חלים על כל הסדרה. כדי לשנות מופע אחד, השתמש בתפריט של המופע (העבר, סמן, מחק).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                title, { title = it }, label = { Text("כותרת (לא חובה)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("כל היום", Modifier.weight(1f))
                Switch(allDay, { allDay = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) { Text("תאריך: ${Formatters.date(date)}") }
                if (!allDay) {
                    TextButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) { Text("שעה: ${ltr("%02d:%02d".format(hour, minute))}") }
                }
            }
            if (!allDay) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("משך (דקות)", Modifier.weight(1f))
                    IntField(duration, { duration = it }, Modifier.width(96.dp))
                }
            }

            Text("סטטוס", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventStatus.entries.forEach { s -> FilterChip(status == s, { status = s }, { Text(s.labelHe) }) }
            }

            if (status == EventStatus.DONE && type != ActivityType.REST) {
                Text("קישור לאימון שנרשם", style = MaterialTheme.typography.labelMedium)
                if (nearSessions.isEmpty()) {
                    Text(
                        "אין אימון רשום בטווח של שבוע מהתאריך הזה.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(linkedSessionId == null, { linkedSessionId = null }, { Text("ללא קישור") })
                        nearSessions.take(8).forEach { s ->
                            FilterChip(linkedSessionId == s.id, { linkedSessionId = s.id }, { Text("${s.title} · ${Formatters.date(s.date)}") })
                        }
                    }
                }
            }

            Text("חזרה", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(frequency == null, { frequency = null }, { Text("לא חוזר") })
                Frequency.entries.forEach { f ->
                    FilterChip(frequency == f, { frequency = f }, { Text(f.labelHe) })
                }
            }
            if (frequency != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when (frequency) {
                            Frequency.DAILY -> "כל כמה ימים"
                            Frequency.WEEKLY -> "כל כמה שבועות"
                            else -> "כל כמה חודשים"
                        },
                        Modifier.weight(1f),
                    )
                    IntField(interval, { interval = it }, Modifier.width(80.dp), maxDigits = 2)
                }
                if (frequency == Frequency.WEEKLY) {
                    Text("בימים", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WEEK_ORDER.forEach { d ->
                            FilterChip(
                                d in weekdays,
                                { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                                { Text(TimeUtils.weekdayShortHe(d)) },
                            )
                        }
                    }
                    if (weekdays.isEmpty()) {
                        Text(
                            "אם לא נבחר יום, האירוע יחזור באותו יום בשבוע שבו התחיל.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("סוף החזרה", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EndMode.entries.forEach { m -> FilterChip(endMode == m, { endMode = m }, { Text(m.labelHe) }) }
                }
                when (endMode) {
                    EndMode.UNTIL -> TextButton(onClick = { pickUntil = true }) { Text("עד: ${Formatters.date(until)}") }
                    EndMode.COUNT -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("מספר מופעים", Modifier.weight(1f))
                        IntField(count, { count = it }, Modifier.width(80.dp), maxDigits = 3)
                    }
                    EndMode.NEVER -> Unit
                }
                val preview = buildRule(frequency, interval, weekdays, endMode, until, count)?.describe()
                if (preview != null) {
                    Text("חוזר: $preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            OutlinedTextField(
                notes, { notes = it }, label = { Text("הערות") }, minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            BigButton(if (initial == null) "הוסף ליומן" else "שמור שינויים", onClick = {
                val startsAt = TimeUtils.toEpochMs(date, if (allDay) 0 else hour, if (allDay) 0 else minute, zone)
                val recurrence = buildRule(frequency, interval, weekdays, endMode, until, count)?.toText().orEmpty()
                // Changing the date of a repeating series restarts the exceptions: they belonged to the old dates.
                val keepExceptions = initial != null && initial.localDate == date.toString() && initial.recurrence == recurrence
                onSave(
                    (initial ?: ActivityEvent(id = "", startsAt = startsAt, localDate = date.toString(), zoneId = zone.id)).copy(
                        type = type,
                        title = title.trim(),
                        notes = notes.trim(),
                        startsAt = startsAt,
                        localDate = date.toString(),
                        zoneId = zone.id,
                        durationMin = if (allDay) null else duration?.coerceIn(1, 1_440),
                        status = status,
                        allDay = allDay,
                        plannedWorkoutId = if (type == ActivityType.STRENGTH) plannedWorkoutId else null,
                        linkedSessionId = if (status == EventStatus.DONE) linkedSessionId else null,
                        recurrence = recurrence,
                        recurrenceExceptions = if (keepExceptions) initial?.recurrenceExceptions.orEmpty() else "",
                    ),
                )
            })
        }
    }
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }) { date = it }
    if (pickUntil) DatePickerSheet(until, onDismiss = { pickUntil = false }) { until = it }
    if (pickTime) TimePickerSheet(hour, minute, onDismiss = { pickTime = false }) { h, m -> hour = h; minute = m }
}

private fun buildRule(
    frequency: Frequency?,
    interval: Int?,
    weekdays: Set<Int>,
    endMode: EndMode,
    until: LocalDate,
    count: Int?,
): RecurrenceRule? {
    val f = frequency ?: return null
    return RecurrenceRule(
        frequency = f,
        interval = (interval ?: 1).coerceIn(1, 52),
        weekdays = if (f == Frequency.WEEKLY) weekdays.sorted() else emptyList(),
        until = if (endMode == EndMode.UNTIL) until else null,
        count = if (endMode == EndMode.COUNT) (count ?: 1).coerceAtLeast(1) else null,
    )
}
