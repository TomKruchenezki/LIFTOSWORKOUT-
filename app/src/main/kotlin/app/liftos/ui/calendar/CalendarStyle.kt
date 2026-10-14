package app.liftos.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.liftos.domain.calendar.CalendarItemKind
import app.liftos.domain.model.EventStatus

private val RestBlue = Color(0xFF6FA8FF)

/** One colour per kind, so planned / done / missed / rest are told apart at a glance (also by shape, see [KindDot]). */
@Composable
fun kindColor(kind: CalendarItemKind): Color = when (kind) {
    CalendarItemKind.SESSION_DONE -> MaterialTheme.colorScheme.primary
    CalendarItemKind.STRENGTH_PLANNED -> MaterialTheme.colorScheme.primary
    CalendarItemKind.STRENGTH_MISSED -> MaterialTheme.colorScheme.error
    CalendarItemKind.STRENGTH_SKIPPED -> MaterialTheme.colorScheme.outline
    CalendarItemKind.REST_DAY -> RestBlue
    CalendarItemKind.EVENT -> MaterialTheme.colorScheme.tertiary
}

fun kindLabel(kind: CalendarItemKind): String = when (kind) {
    CalendarItemKind.SESSION_DONE -> "אימון שבוצע"
    CalendarItemKind.STRENGTH_PLANNED -> "אימון מתוכנן"
    CalendarItemKind.STRENGTH_MISSED -> "אימון שהוחמץ"
    CalendarItemKind.STRENGTH_SKIPPED -> "אימון שדולג"
    CalendarItemKind.REST_DAY -> "יום מנוחה"
    CalendarItemKind.EVENT -> "אירוע"
}

fun statusLabel(status: EventStatus?): String = status?.labelHe.orEmpty()

/** A filled dot, except planned workouts which are hollow rings (not yet done). */
@Composable
fun KindDot(kind: CalendarItemKind, size: Dp = 8.dp) {
    val color = kindColor(kind)
    if (kind == CalendarItemKind.STRENGTH_PLANNED) {
        Box(Modifier.size(size).border(2.dp, color, CircleShape))
    } else {
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            CalendarItemKind.SESSION_DONE, CalendarItemKind.STRENGTH_PLANNED, CalendarItemKind.STRENGTH_MISSED,
            CalendarItemKind.REST_DAY, CalendarItemKind.EVENT,
        ).forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                KindDot(kind)
                Text(kindLabel(kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
