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

@Composable
fun HistoryScreen(container: AppContainer, onOpenSession: (String) -> Unit, onEditSession: (String) -> Unit) {
    val vm = appViewModel { HistoryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }
    var addPast by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<ActivityEvent?>(null) }
    var deleteWorkout by remember { mutableStateOf<String?>(null) }
    var deleteEvent by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar("יומן אימונים", actions = {
                Box {
                    IconButton(onClick = { addMenu = true }) { Icon(Icons.Rounded.Add, contentDescription = "הוסף") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("אימון שבוצע בעבר") }, onClick = { addMenu = false; addPast = true })
                        DropdownMenuItem(text = { Text("פעילות אחרת או הערה") }, onClick = {
                            addMenu = false
                            val zone = java.time.ZoneId.systemDefault()
                            editEvent = ActivityEvent(id = "", startsAt = System.currentTimeMillis(), localDate = "", zoneId = zone.id)
                        })
                    }
                }
            })
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        if (state.entries.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    title = "עוד אין אימונים ביומן",
                    subtitle = "אימונים שתסיים יופיעו כאן. אפשר גם להוסיף אימון שבוצע בעבר.",
                    icon = Icons.Rounded.History,
                    action = { BigButton("הוסף אימון מהעבר", onClick = { addPast = true }) },
                )
            }
        } else {
            val grouped = state.entries.groupBy { e -> TimeUtils.parseDate(e.localDate)?.withDayOfMonth(1) ?: LocalDate.MIN }
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (month, entries) ->
                    item(key = "m-$month") { SectionTitle(if (month == LocalDate.MIN) "ללא תאריך" else monthYear(month)) }
                    items(entries, key = { e -> when (e) { is HistoryEntry.Workout -> "w-" + e.item.session.id; is HistoryEntry.Activity -> "a-" + e.event.id } }) { e ->
                        when (e) {
                            is HistoryEntry.Workout -> WorkoutRow(e, { onOpenSession(e.item.session.id) }, { onEditSession(e.item.session.id) }, { deleteWorkout = e.item.session.id })
                            is HistoryEntry.Activity -> ActivityRow(e.event, { editEvent = e.event }, { deleteEvent = e.event.id })
                        }
                    }
                }
            }
        }
    }

    if (addPast) {
        AddPastWorkoutSheet(
            plan = state.activePlan,
            onCreate = { pwId, title, start, dur -> addPast = false; vm.createPast(pwId, title, start, dur, onEditSession) },
            onDismiss = { addPast = false },
        )
    }
    editEvent?.let { ev ->
        ActivityDialog(ev, onSave = { vm.saveEvent(it); editEvent = null }, onDismiss = { editEvent = null })
    }
    deleteWorkout?.let { id ->
        ConfirmDialog("למחוק את האימון?", "האימון וכל הסטים שלו יימחקו לצמיתות.", "מחק", destructive = true,
            onConfirm = { vm.deleteWorkout(id); deleteWorkout = null }, onDismiss = { deleteWorkout = null })
    }
    deleteEvent?.let { id ->
        ConfirmDialog("למחוק את הרשומה?", "הרשומה תימחק לצמיתות.", "מחק", destructive = true,
            onConfirm = { vm.deleteEvent(id); deleteEvent = null }, onDismiss = { deleteEvent = null })
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
private fun AddPastWorkoutSheet(
    plan: app.liftos.domain.model.PlanDetail?,
    onCreate: (String?, String, Long, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    var workoutId by remember { mutableStateOf<String?>(plan?.workouts?.firstOrNull()?.workout?.id) }
    var title by remember { mutableStateOf(plan?.workouts?.firstOrNull()?.workout?.name ?: "אימון") }
    var date by remember { mutableStateOf(LocalDate.now(zone).minusDays(1)) }
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActivityDialog(ev: ActivityEvent, onSave: (ActivityEvent) -> Unit, onDismiss: () -> Unit) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    var type by remember { mutableStateOf(ev.type) }
    var title by remember { mutableStateOf(ev.title) }
    var notes by remember { mutableStateOf(ev.notes) }
    var duration by remember { mutableStateOf(ev.durationMin) }
    var date by remember { mutableStateOf(TimeUtils.localDate(ev.startsAt, zone)) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ev.id.isBlank()) "פעילות חדשה" else "עריכת פעילות") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActivityType.entries.forEach { t -> FilterChip(type == t, { type = t }, { Text(t.labelHe) }) }
                }
                OutlinedTextField(title, { title = it }, label = { Text("כותרת") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { pickDate = true }) { Text("תאריך: ${Formatters.date(date)}") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("משך (דקות)", Modifier.weight(1f))
                    IntField(duration, { duration = it }, Modifier.width(96.dp))
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("הערות") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val startsAt = TimeUtils.toEpochMs(date, 12, 0, zone).let { base ->
                    // keep the original time of day when only editing
                    val orig = java.time.Instant.ofEpochMilli(ev.startsAt).atZone(zone)
                    TimeUtils.toEpochMs(date, orig.hour, orig.minute, zone).takeIf { ev.id.isNotBlank() } ?: base
                }
                onSave(ev.copy(type = type, title = title.trim(), notes = notes.trim(), durationMin = duration, startsAt = startsAt))
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }) { date = it }
}
