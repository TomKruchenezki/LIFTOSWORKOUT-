package app.liftos.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.ActivityType
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.IntField
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.TimePickerSheet
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.ltr
import app.liftos.ui.util.monthYear
import java.time.LocalDate

/** The full history as a list grouped by month (the "היסטוריה" view of the journal). */
@Composable
fun HistoryList(
    entries: List<HistoryEntry>,
    onOpenSession: (String) -> Unit,
    onEditSession: (String) -> Unit,
    onDeleteWorkout: (String) -> Unit,
    onEditEvent: (ActivityEvent) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onAddPast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Column(modifier) {
            EmptyState(
                title = "עוד אין אימונים ביומן",
                subtitle = "אימונים שתסיים יופיעו כאן. אפשר גם להוסיף אימון שבוצע בעבר.",
                icon = Icons.Rounded.History,
                action = { BigButton("הוסף אימון מהעבר", onClick = onAddPast) },
            )
        }
        return
    }
    val grouped = entries.groupBy { e -> TimeUtils.parseDate(e.localDate)?.withDayOfMonth(1) ?: LocalDate.MIN }
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        grouped.forEach { (month, list) ->
            item(key = "m-$month") { SectionTitle(if (month == LocalDate.MIN) "ללא תאריך" else monthYear(month)) }
            items(list, key = { e -> when (e) { is HistoryEntry.Workout -> "w-" + e.item.session.id; is HistoryEntry.Activity -> "a-" + e.event.id } }) { e ->
                when (e) {
                    is HistoryEntry.Workout -> WorkoutRow(e, { onOpenSession(e.item.session.id) }, { onEditSession(e.item.session.id) }, { onDeleteWorkout(e.item.session.id) })
                    is HistoryEntry.Activity -> ActivityRow(e.event, { onEditEvent(e.event) }, { onDeleteEvent(e.event.id) })
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(e: HistoryEntry.Workout, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val s = e.item.session
    val date = TimeUtils.parseDate(s.localDate)
    LiftCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    (date?.let { dateLine(it) } ?: s.localDate) + (s.durationMs?.let { " · ${Formatters.minutesHe(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${e.item.exercises} תרגילים · ${e.item.workingSets} סטים · נפח ${ltr(Formatters.thousands(e.item.volumeKg))}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("ערוך אימון") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("מחק", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(ev: ActivityEvent, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val date = TimeUtils.parseDate(ev.localDate)
    LiftCard(Modifier.fillMaxWidth(), onClick = onEdit, container = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ev.type.labelHe + if (ev.title.isNotBlank() && ev.title != ev.type.labelHe) " · ${ev.title}" else "", style = MaterialTheme.typography.titleSmall)
                Text(
                    (date?.let { dateLine(it) } ?: ev.localDate) + (ev.durationMin?.let { " · ${ltr(it.toString())} דק׳" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ev.notes.isNotBlank()) Text(ev.notes, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("ערוך") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("מחק", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AddPastWorkoutSheet(
    plan: app.liftos.domain.model.PlanDetail?,
    onCreate: (String?, String, Long, Int) -> Unit,
    onDismiss: () -> Unit,
    initialWorkoutId: String? = null,
    initialDate: LocalDate? = null,
) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    val firstWorkout = remember(plan, initialWorkoutId) {
        plan?.workouts?.firstOrNull { it.workout.id == initialWorkoutId }?.workout ?: plan?.workouts?.firstOrNull()?.workout
    }
    var workoutId by remember { mutableStateOf<String?>(firstWorkout?.id) }
    var title by remember { mutableStateOf(firstWorkout?.name ?: "אימון") }
    var date by remember { mutableStateOf(initialDate ?: LocalDate.now(zone).minusDays(1)) }
    var hour by remember { mutableStateOf(18) }
    var minute by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf<Int?>(60) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("הוספת אימון מהעבר", style = MaterialTheme.typography.titleLarge)
            Text(
                "האימון ייווצר לפי מבנה האימון שנבחר, ואז תוכל למלא משקלים וחזרות ולסמן סטים כבוצעו.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("אימון", style = MaterialTheme.typography.labelMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plan?.workouts?.forEach { w ->
                    FilterChip(workoutId == w.workout.id, { workoutId = w.workout.id; title = w.workout.name }, { Text(w.workout.name) })
                }
                FilterChip(workoutId == null, { workoutId = null; title = "אימון חופשי" }, { Text("אימון חופשי") })
            }
            OutlinedTextField(title, { title = it }, label = { Text("שם האימון") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) { Text("תאריך: ${Formatters.date(date)}") }
                TextButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) { Text("שעה: ${ltr("%02d:%02d".format(hour, minute))}") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("משך (דקות)", Modifier.weight(1f))
                IntField(duration, { duration = it }, Modifier.width(96.dp))
            }
            BigButton("צור והמשך לעריכה", onClick = {
                val start = TimeUtils.toEpochMs(date, hour, minute, zone)
                onCreate(workoutId, title.trim().ifBlank { "אימון" }, start, (duration ?: 60).coerceIn(1, 600))
            })
        }
    }
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }) { date = it }
    if (pickTime) TimePickerSheet(hour, minute, onDismiss = { pickTime = false }) { h, m -> hour = h; minute = m }
}
