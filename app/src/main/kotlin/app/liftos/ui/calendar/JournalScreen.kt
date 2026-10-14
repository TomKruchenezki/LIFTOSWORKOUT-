package app.liftos.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.calendar.CalendarBuilder
import app.liftos.domain.calendar.CalendarItem
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityEvent
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.appViewModel
import app.liftos.ui.history.AddPastWorkoutSheet
import app.liftos.ui.history.HistoryList
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.ltr
import app.liftos.ui.util.monthYear
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What is open on top of the journal (at most one thing at a time). */
private sealed interface JournalDialog {
    data class EditEvent(val event: ActivityEvent?, val date: LocalDate) : JournalDialog
    data class MoveWorkout(val item: CalendarItem) : JournalDialog
    data class MoveEvent(val item: CalendarItem) : JournalDialog
    data class DeleteEvent(val item: CalendarItem) : JournalDialog
    data class Link(val item: CalendarItem) : JournalDialog
    data class AddPast(val item: CalendarItem?) : JournalDialog
    data class DeleteWorkout(val id: String) : JournalDialog
    data class DeleteHistoryEvent(val id: String) : JournalDialog
}

@Composable
fun JournalScreen(container: AppContainer, onOpenSession: (String) -> Unit, onEditSession: (String) -> Unit) {
    val vm = appViewModel { JournalViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<JournalDialog?>(null) }
    val ctl = ui.controls

    fun findEvent(id: String?): ActivityEvent? = id?.let { eventId -> ui.events.firstOrNull { it.id == eventId } }

    val actions = ItemActions(
        openSession = onOpenSession,
        editSession = onEditSession,
        start = { item -> item.plannedWorkoutId?.let { id -> vm.startWorkout(id) { onEditSession(it) } } },
        moveWorkout = { dialog = JournalDialog.MoveWorkout(it) },
        markWorkout = { item, action -> item.plannedWorkoutId?.let { vm.markWorkout(it, item.originalDate ?: item.date, action) } },
        clearOverride = { item -> item.plannedWorkoutId?.let { vm.clearOverride(it, item.originalDate ?: item.date) } },
        addPastFor = { dialog = JournalDialog.AddPast(it) },
        editEvent = { item -> findEvent(item.eventId)?.let { dialog = JournalDialog.EditEvent(it, item.date) } },
        moveEvent = { dialog = JournalDialog.MoveEvent(it) },
        setStatus = { item, status -> item.eventId?.let { vm.setEventStatus(it, item.date, status) } },
        linkEvent = { dialog = JournalDialog.Link(it) },
        deleteEvent = { dialog = JournalDialog.DeleteEvent(it) },
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar("יומן אימונים", actions = {
                Box {
                    IconButton(onClick = { addMenu = true }) { Icon(Icons.Rounded.Add, contentDescription = "הוסף") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("אירוע או פעילות חדשים") }, onClick = {
                            addMenu = false
                            dialog = JournalDialog.EditEvent(null, ctl.selected)
                        })
                        DropdownMenuItem(text = { Text("אימון שבוצע בעבר") }, onClick = {
                            addMenu = false
                            dialog = JournalDialog.AddPast(null)
                        })
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ViewSelector(ctl.view, vm::setView)
            if (ctl.view == JournalView.MONTH || ctl.view == JournalView.WEEK || ctl.view == JournalView.DAY) {
                NavRow(title = titleFor(ui), onPrev = { vm.step(-1) }, onNext = { vm.step(1) }, onToday = vm::today)
            }
            when {
                ui.loading -> LoadingBox()
                ctl.view == JournalView.HISTORY -> HistoryList(
                    entries = ui.history,
                    onOpenSession = onOpenSession,
                    onEditSession = onEditSession,
                    onDeleteWorkout = { dialog = JournalDialog.DeleteWorkout(it) },
                    onEditEvent = { ev -> dialog = JournalDialog.EditEvent(ev, TimeUtils.parseDate(ev.localDate) ?: ui.today) },
                    onDeleteEvent = { dialog = JournalDialog.DeleteHistoryEvent(it) },
                    onAddPast = { dialog = JournalDialog.AddPast(null) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (ctl.view) {
                        JournalView.MONTH -> monthContent(ui, vm, actions) { dialog = JournalDialog.EditEvent(null, it) }
                        JournalView.WEEK -> weekContent(ui, vm, actions)
                        JournalView.DAY -> dayContent(ui, actions) { dialog = JournalDialog.EditEvent(null, it) }
                        else -> listContent(ui, actions)
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is JournalDialog.EditEvent -> EventEditorSheet(
            initial = d.event,
            initialDate = d.date,
            plan = ui.plan,
            sessions = ui.recentSessions,
            onSave = { vm.saveEvent(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is JournalDialog.MoveWorkout -> DatePickerSheet(d.item.date, onDismiss = { dialog = null }) { newDate ->
            val id = d.item.plannedWorkoutId ?: return@DatePickerSheet
            val original = d.item.originalDate ?: d.item.date
            if (newDate == original) vm.clearOverride(id, original) else vm.moveWorkout(id, original, newDate)
        }
        is JournalDialog.MoveEvent -> DatePickerSheet(d.item.date, onDismiss = { dialog = null }) { newDate ->
            val ev = findEvent(d.item.eventId) ?: return@DatePickerSheet
            val minutes = CalendarBuilder.minutesOfDay(ev.startsAt, TimeUtils.zone(ev.zoneId))
            val at = TimeUtils.toEpochMs(newDate, minutes / 60, minutes % 60, ZoneId.systemDefault())
            vm.moveOccurrence(ev.id, d.item.date, at)
        }
        is JournalDialog.DeleteEvent -> DeleteEventDialog(
            item = d.item,
            onOccurrence = { d.item.eventId?.let { id -> vm.deleteOccurrence(id, d.item.date) }; dialog = null },
            onSeries = { d.item.eventId?.let { id -> vm.deleteEvent(id) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is JournalDialog.Link -> LinkDialog(
            item = d.item,
            sessions = ui.recentSessions,
            onPick = { sessionId -> d.item.eventId?.let { vm.linkEvent(it, d.item.date, sessionId) }; dialog = null },
            onDismiss = { dialog = null },
        )
        is JournalDialog.AddPast -> AddPastWorkoutSheet(
            plan = ui.plan,
            initialWorkoutId = d.item?.plannedWorkoutId,
            initialDate = d.item?.date,
            onDismiss = { dialog = null },
            onCreate = { workoutId, title, startedAt, durationMin ->
                val item = d.item
                vm.createPast(workoutId, title, startedAt, durationMin) { id ->
                    // A plain "missed" mark would keep showing next to the workout that now exists.
                    if (item != null && !item.moved && !item.automatic) {
                        item.plannedWorkoutId?.let { vm.clearOverride(it, item.originalDate ?: item.date) }
                    }
                    dialog = null
                    onEditSession(id)
                }
            },
        )
        is JournalDialog.DeleteWorkout -> ConfirmDialog(
            title = "למחוק את האימון?",
            text = "האימון וכל הסטים שלו יימחקו, והנתונים יוסרו מהסטטיסטיקות והגרפים. אי אפשר לשחזר.",
            confirmText = "מחק",
            destructive = true,
            onConfirm = { vm.deleteWorkout(d.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is JournalDialog.DeleteHistoryEvent -> ConfirmDialog(
            title = "למחוק את האירוע?",
            text = "האירוע יימחק מהיומן. אימונים שנרשמו לא ייפגעו.",
            confirmText = "מחק",
            destructive = true,
            onConfirm = { vm.deleteEvent(d.id); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

// ---- controls ---------------------------------------------------------------------------------------------------------

@Composable
private fun ViewSelector(current: JournalView, onSelect: (JournalView) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        JournalView.entries.forEach { v ->
            FilterChip(
                selected = v == current,
                onClick = { onSelect(v) },
                label = {
                    Text(
                        v.labelHe,
                        Modifier.fillMaxWidth(),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavRow(title: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "הקודם") }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "הבא") }
        TextButton(onClick = onToday) { Text("היום") }
    }
}

private fun titleFor(ui: JournalUi): String = when (ui.controls.view) {
    JournalView.MONTH -> monthYear(ui.controls.anchor)
    JournalView.WEEK -> "${ltr(Formatters.date(ui.range.start))} – ${ltr(Formatters.date(ui.range.end))}"
    JournalView.DAY -> dateLine(ui.controls.anchor)
    else -> ""
}

// ---- the views ----------------------------------------------------------------------------------------------------

private fun LazyListScope.monthContent(
    ui: JournalUi,
    vm: JournalViewModel,
    actions: ItemActions,
    onAdd: (LocalDate) -> Unit,
) {
    val selected = ui.controls.selected
    val dayItems = ui.items.filter { it.date == selected }
    item {
        MonthGrid(
            anchor = ui.controls.anchor,
            range = ui.range,
            today = ui.today,
            selected = selected,
            markers = CalendarBuilder.markers(ui.items),
            onSelect = vm::select,
        )
    }
    item { CalendarLegend() }
    item {
        DayHeader(selected, ui.today, dayItems.size, trailing = {
            TextButton(onClick = { vm.openDay(selected) }) { Text("פתח יום") }
        })
    }
    if (dayItems.isEmpty()) {
        item { EmptyDay(onAdd = { onAdd(selected) }) }
    } else {
        items(dayItems) { ItemCard(it, actions, ui.today) }
    }
}

private fun LazyListScope.weekContent(ui: JournalUi, vm: JournalViewModel, actions: ItemActions) {
    item { CalendarLegend() }
    for (offset in 0L..6L) {
        val day = ui.range.start.plusDays(offset)
        val list = ui.items.filter { it.date == day }
        item {
            DayHeader(day, ui.today, list.size, trailing = {
                TextButton(onClick = { vm.openDay(day) }) { Text("פתח יום") }
            })
        }
        items(list) { ItemCard(it, actions, ui.today) }
    }
}

private fun LazyListScope.dayContent(ui: JournalUi, actions: ItemActions, onAdd: (LocalDate) -> Unit) {
    val day = ui.controls.anchor
    val list = ui.items.filter { it.date == day }
    item { CalendarLegend() }
    item { DayHeader(day, ui.today, list.size) }
    if (list.isEmpty()) {
        item { EmptyDay(onAdd = { onAdd(day) }) }
    } else {
        items(list) { ItemCard(it, actions, ui.today) }
    }
}

/** What is coming (and the last two weeks), day by day; days with nothing in them are left out. */
private fun LazyListScope.listContent(ui: JournalUi, actions: ItemActions) {
    if (ui.items.isEmpty()) {
        item {
            EmptyState(
                title = "אין פריטים בטווח הזה",
                subtitle = "אימונים מהתוכנית הפעילה ואירועים שתוסיף יופיעו כאן.",
                icon = Icons.Rounded.EventAvailable,
            )
        }
        return
    }
    item { CalendarLegend() }
    ui.items.groupBy { it.date }.toSortedMap().forEach { (day, list) ->
        item { DayHeader(day, ui.today, list.size) }
        items(list) { ItemCard(it, actions, ui.today) }
    }
}

@Composable
private fun EmptyDay(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("אין אירועים או אימונים ביום הזה.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SoftButton("הוסף אירוע", onClick = onAdd, icon = Icons.Rounded.Add)
    }
}

// ---- small dialogs --------------------------------------------------------------------------------------------------

@Composable
private fun DeleteEventDialog(item: CalendarItem, onOccurrence: () -> Unit, onSeries: () -> Unit, onDismiss: () -> Unit) {
    if (!item.recurring) {
        ConfirmDialog(
            title = "למחוק את האירוע?",
            text = "האירוע יימחק מהיומן. אימונים שנרשמו לא ייפגעו.",
            confirmText = "מחק",
            destructive = true,
            onConfirm = onSeries,
            onDismiss = onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("אירוע חוזר") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("מה למחוק?")
                TextButton(onClick = onOccurrence) { Text("רק את המופע הזה (${ltr(Formatters.date(item.date))})") }
            }
        },
        confirmButton = { TextButton(onClick = onSeries) { Text("את כל הסדרה", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun LinkDialog(
    item: CalendarItem,
    sessions: List<app.liftos.domain.analytics.SessionFact>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = remember(sessions, item.date) {
        sessions.filter { abs(ChronoUnit.DAYS.between(it.date, item.date)) <= 14 }
            .sortedBy { abs(ChronoUnit.DAYS.between(it.date, item.date)) }
            .take(10)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("קישור לאימון שנרשם") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (candidates.isEmpty()) {
                    Text("אין אימון רשום בטווח של שבועיים מהתאריך הזה.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                candidates.forEach { s ->
                    TextButton(onClick = { onPick(s.id) }) { Text("${s.title} · ${ltr(Formatters.date(s.date))}") }
                }
                if (item.linkedSessionId != null) {
                    TextButton(onClick = { onPick(null) }) { Text("הסר קישור", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
    )
}
