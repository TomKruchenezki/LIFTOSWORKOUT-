package app.liftos.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.calendar.CalendarItem
import app.liftos.domain.calendar.CalendarItemKind
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.OverrideAction
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.SoftButton
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.dayLabel
import app.liftos.ui.util.ltr
import java.time.LocalDate

/** Everything a calendar item can do; the screen decides what each action opens or changes. */
class ItemActions(
    val openSession: (String) -> Unit,
    val editSession: (String) -> Unit,
    val start: (CalendarItem) -> Unit,
    val moveWorkout: (CalendarItem) -> Unit,
    val markWorkout: (CalendarItem, OverrideAction) -> Unit,
    val clearOverride: (CalendarItem) -> Unit,
    val addPastFor: (CalendarItem) -> Unit,
    val editEvent: (CalendarItem) -> Unit,
    val moveEvent: (CalendarItem) -> Unit,
    val setStatus: (CalendarItem, EventStatus) -> Unit,
    val linkEvent: (CalendarItem) -> Unit,
    val deleteEvent: (CalendarItem) -> Unit,
)

/** Sunday-first order of the week, as ISO weekday numbers. */
private val WEEK_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

// ---- month ---------------------------------------------------------------------------------------------------------

/** Six weeks starting on the Sunday on or before the first of [anchor]'s month; coloured dots show what is on each day. */
@Composable
fun MonthGrid(
    anchor: LocalDate,
    range: DateRange,
    today: LocalDate,
    selected: LocalDate,
    markers: Map<LocalDate, List<CalendarItemKind>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEK_ORDER.forEach { d ->
                Text(
                    TimeUtils.weekdayShortHe(d),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (dayIndex in 0 until 7) {
                    val date = range.start.plusDays((week * 7 + dayIndex).toLong())
                    DayCell(
                        date = date,
                        inMonth = date.month == anchor.month && date.year == anchor.year,
                        isToday = date == today,
                        isSelected = date == selected,
                        kinds = markers[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    kinds: List<CalendarItemKind>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (isSelected) scheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (isToday) Modifier.border(BorderStroke(1.5.dp, scheme.primary), shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isSelected || isToday -> scheme.primary
                inMonth -> scheme.onSurface
                else -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
        )
        Row(Modifier.height(10.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            kinds.take(4).forEach { KindDot(it, size = 6.dp) }
        }
    }
}

// ---- lists of items ------------------------------------------------------------------------------------------------

@Composable
fun DayHeader(date: LocalDate, today: LocalDate, count: Int, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                dayLabel(date, today),
                style = MaterialTheme.typography.titleMedium,
                color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                Formatters.date(date) + if (count == 0) " · אין פריטים" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

// ---- one item --------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemCard(item: CalendarItem, a: ItemActions, today: LocalDate, modifier: Modifier = Modifier, showDate: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    val color = kindColor(item.kind)
    val struck = item.eventStatus == EventStatus.CANCELLED || item.kind == CalendarItemKind.STRENGTH_SKIPPED
    val sessionId = item.sessionId
    val onClick: () -> Unit = {
        when {
            item.kind == CalendarItemKind.SESSION_DONE && sessionId != null -> a.openSession(sessionId)
            item.eventId != null -> a.editEvent(item)
            else -> menu = true
        }
    }
    LiftCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(4.dp).height(44.dp).background(color, RoundedCornerShape(2.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindDot(item.kind)
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (struck) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val meta = buildList {
                    if (showDate) add(dateLine(item.date))
                    item.startMinutes?.let { add(ltr("%02d:%02d".format(it / 60, it % 60))) }
                    item.durationMin?.let { add("${ltr(it.toString())} דק׳") }
                    if (item.subtitle.isNotBlank()) add(item.subtitle)
                }
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(kindLabel(item.kind), style = MaterialTheme.typography.labelSmall, color = color)
                    if (item.eventStatus != null && item.kind == CalendarItemKind.EVENT) {
                        Text(statusLabel(item.eventStatus), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.moved) {
                        val from = item.originalDate?.let { " מ-${Formatters.date(it)}" }.orEmpty()
                        Text("הועבר$from", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.recurring) Text("חוזר", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.automatic && item.kind == CalendarItemKind.STRENGTH_MISSED) {
                        Text("סומן אוטומטית", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.linkedSessionId != null) {
                        Text("מקושר לאימון", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (item.kind == CalendarItemKind.STRENGTH_PLANNED && item.plannedWorkoutId != null) {
                    SoftButton("התחל אימון זה", onClick = { a.start(item) }, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                ItemMenu(item, a, menu) { menu = false }
            }
        }
    }
}

@Composable
private fun ItemMenu(item: CalendarItem, a: ItemActions, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (item.kind) {
            CalendarItemKind.SESSION_DONE -> {
                item.sessionId?.let { id ->
                    Entry("פתח סיכום", onDismiss) { a.openSession(id) }
                    Entry("ערוך אימון", onDismiss) { a.editSession(id) }
                }
            }
            CalendarItemKind.STRENGTH_PLANNED -> {
                Entry("התחל אימון", onDismiss) { a.start(item) }
                Entry("העבר ליום אחר", onDismiss) { a.moveWorkout(item) }
                Entry("סמן כלא בוצע", onDismiss) { a.markWorkout(item, OverrideAction.MISSED) }
                Entry("דלג בכוונה", onDismiss) { a.markWorkout(item, OverrideAction.SKIPPED) }
                if (item.moved) Entry("החזר ליום המקורי", onDismiss) { a.clearOverride(item) }
            }
            CalendarItemKind.STRENGTH_MISSED -> {
                Entry("העבר ליום אחר", onDismiss) { a.moveWorkout(item) }
                Entry("הוסף אימון שבוצע", onDismiss) { a.addPastFor(item) }
                Entry("דלג בכוונה", onDismiss) { a.markWorkout(item, OverrideAction.SKIPPED) }
                if (item.moved || !item.automatic) Entry("בטל סימון", onDismiss) { a.clearOverride(item) }
            }
            CalendarItemKind.STRENGTH_SKIPPED -> {
                Entry("העבר ליום אחר", onDismiss) { a.moveWorkout(item) }
                Entry("סמן כלא בוצע", onDismiss) { a.markWorkout(item, OverrideAction.MISSED) }
                Entry("בטל דילוג", onDismiss) { a.clearOverride(item) }
            }
            CalendarItemKind.EVENT, CalendarItemKind.REST_DAY -> {
                Entry("ערוך", onDismiss) { a.editEvent(item) }
                Entry("העבר ליום אחר", onDismiss) { a.moveEvent(item) }
                if (item.eventStatus != EventStatus.DONE) Entry("סמן כבוצע", onDismiss) { a.setStatus(item, EventStatus.DONE) }
                if (item.eventStatus != EventStatus.MISSED) Entry("סמן כלא בוצע", onDismiss) { a.setStatus(item, EventStatus.MISSED) }
                if (item.eventStatus != EventStatus.CANCELLED) Entry("בטל אירוע", onDismiss) { a.setStatus(item, EventStatus.CANCELLED) }
                if (item.eventStatus != EventStatus.PLANNED) Entry("החזר למתוכנן", onDismiss) { a.setStatus(item, EventStatus.PLANNED) }
                if (item.eventType != ActivityType.REST) Entry("קשר לאימון שנרשם", onDismiss) { a.linkEvent(item) }
                Entry("מחק", onDismiss, danger = true) { a.deleteEvent(item) }
            }
        }
    }
}

@Composable
private fun Entry(label: String, onDismiss: () -> Unit, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        onClick = { onDismiss(); onClick() },
    )
}
