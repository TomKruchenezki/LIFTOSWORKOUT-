package app.liftos.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.PlanDetail
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.PlannedWorkoutDetail
import app.liftos.domain.model.WorkoutPlan
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.IntField
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ReorderEntry
import app.liftos.ui.components.ReorderSheet
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.TextInputDialog
import app.liftos.ui.components.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlanDetailViewModel(private val c: AppContainer, private val planId: String) : ViewModel() {
    val detail: StateFlow<PlanDetail?> = c.plans.observePlanDetail(planId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isActive: StateFlow<Boolean> = c.settings.observe().map { it.activePlanId == planId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Distinguishes "still loading" from "plan was deleted". */
    val exists: StateFlow<Boolean?> = c.plans.observePlans().map { list -> list.any { it.id == planId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(plan: WorkoutPlan) = viewModelScope.launch { c.plans.updatePlan(plan) }
    fun activate() = viewModelScope.launch { c.plans.setActivePlan(planId) }
    fun addWorkout(name: String) = viewModelScope.launch { c.plans.addWorkout(planId, name) }
    fun updateWorkout(w: PlannedWorkout) = viewModelScope.launch { c.plans.updateWorkout(w) }
    fun deleteWorkout(id: String) = viewModelScope.launch { c.plans.deleteWorkout(id) }
    fun duplicateWorkout(id: String) = viewModelScope.launch { c.plans.duplicateWorkout(id) }
    fun reorderWorkouts(ids: List<String>) = viewModelScope.launch { c.plans.reorderWorkouts(planId, ids) }
    fun duplicatePlan(onDone: (String) -> Unit) = viewModelScope.launch { c.plans.duplicatePlan(planId)?.let(onDone) }
    fun saveAsTemplate() = viewModelScope.launch { c.plans.saveAsTemplate(planId) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.plans.deletePlan(planId); onDone() }
}

@Composable
fun PlanDetailScreen(
    container: AppContainer,
    planId: String,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val vm = appViewModel(key = "plan-$planId") { PlanDetailViewModel(container, planId) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val isActive by vm.isActive.collectAsStateWithLifecycle()
    val exists by vm.exists.collectAsStateWithLifecycle()
    var editInfo by remember { mutableStateOf(false) }
    var addWorkout by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editDay by remember { mutableStateOf<PlannedWorkout?>(null) }
    var deleteDay by remember { mutableStateOf<PlannedWorkout?>(null) }

    LaunchedEffect(exists) { if (exists == false) onBack() }
    val plan = detail?.plan
    if (detail == null || plan == null) {
        LoadingBox()
        return
    }
    val d = detail!!

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar(plan.name, onBack = onBack, actions = {
                IconButton(onClick = { editInfo = true }) { Icon(Icons.Rounded.Edit, contentDescription = "ערוך פרטי תוכנית") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "עוד") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!isActive && !plan.isTemplate) DropdownMenuItem(text = { Text("הפוך לתוכנית פעילה") }, onClick = { menu = false; vm.activate() })
                        DropdownMenuItem(text = { Text("שכפל תוכנית") }, onClick = { menu = false; vm.duplicatePlan(onBack) })
                        if (!plan.isTemplate) DropdownMenuItem(text = { Text("שמור כתבנית") }, onClick = { menu = false; vm.saveAsTemplate() })
                        DropdownMenuItem(text = { Text("מחק תוכנית", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            })
        },
    ) { padding ->
        ScrollPage(Modifier.padding(padding)) {
            LiftCard(Modifier.fillMaxWidth()) {
                if (plan.isTemplate) Text("תבנית", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                if (isActive) Text("תוכנית פעילה", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (plan.goal.isNotBlank()) {
                    Text("מטרה: ${plan.goal}", style = MaterialTheme.typography.titleMedium)
                }
                if (plan.description.isNotBlank()) {
                    Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "יעד שבועי: ${plan.targetPerWeek(d.workouts.size)} אימונים",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionTitle("סידור השבוע")
            WeekStrip(d.workouts)

            SectionTitle("ימי אימון", trailing = {
                if (d.workouts.size > 1) TextButton(onClick = { reorder = true }) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text("שנה סדר")
                }
            })
            d.workouts.forEach { w ->
                WorkoutCard(
                    w = w,
                    onClick = { onOpenWorkout(w.workout.id) },
                    onEdit = { editDay = w.workout },
                    onDuplicate = { vm.duplicateWorkout(w.workout.id) },
                    onDelete = { deleteDay = w.workout },
                )
            }
            SoftButton("הוסף יום אימון", onClick = { addWorkout = true }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
            if (!isActive && !plan.isTemplate) {
                BigButton("הפוך לתוכנית הפעילה", onClick = vm::activate)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editInfo) PlanInfoDialog(plan, onSave = { vm.update(it); editInfo = false }, onDismiss = { editInfo = false })
    if (addWorkout) {
        TextInputDialog("יום אימון חדש", "שם האימון (למשל Push)", "", "הוסף", onConfirm = { vm.addWorkout(it); addWorkout = false }, onDismiss = { addWorkout = false })
    }
    if (reorder) {
        ReorderSheet(
            title = "סדר ימי האימון",
            entries = d.workouts.map { ReorderEntry(it.workout.id, it.workout.name, it.workout.description) },
            onReordered = vm::reorderWorkouts,
            onDismiss = { reorder = false },
        )
    }
    editDay?.let { w ->
        WorkoutInfoDialog(w, onSave = { vm.updateWorkout(it); editDay = null }, onDismiss = { editDay = null })
    }
    deleteDay?.let { w ->
        ConfirmDialog(
            "למחוק את יום האימון?", "'${w.name}' וכל התרגילים שבו יימחקו מהתוכנית. אימונים שכבר בוצעו לא יושפעו.", "מחק", destructive = true,
            onConfirm = { vm.deleteWorkout(w.id); deleteDay = null }, onDismiss = { deleteDay = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "למחוק את התוכנית?", "התוכנית תימחק. אימונים שכבר בוצעו לא יושפעו.", "מחק", destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(onBack) }, onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun WeekStrip(workouts: List<PlannedWorkoutDetail>) {
    val byDay = workouts.groupBy { it.workout.weekday }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TimeUtils.ISRAELI_WEEK_ORDER.forEach { iso ->
            val here = byDay[iso].orEmpty()
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (here.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TimeUtils.weekdayShortHe(iso), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (here.isEmpty()) "מנוחה" else here.joinToString("+") { shortName(it.workout.name) },
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = if (here.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun shortName(name: String): String = if (name.length <= 8) name else name.take(7) + "…"

@Composable
private fun WorkoutCard(w: PlannedWorkoutDetail, onClick: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    LiftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(w.workout.name, style = MaterialTheme.typography.titleLarge)
                if (w.workout.description.isNotBlank()) {
                    Text(w.workout.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val day = w.workout.weekday?.let { "יום ${TimeUtils.weekdayHe(it)}" } ?: "ללא יום קבוע"
                Text(
                    "$day · ${w.exercises.size} תרגילים · ${w.exercises.sumOf { it.targetSets }} סטים",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("ערוך שם, תיאור ויום") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("שכפל") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("מחק", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun PlanInfoDialog(plan: WorkoutPlan, onSave: (WorkoutPlan) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(plan.name) }
    var goal by remember { mutableStateOf(plan.goal) }
    var desc by remember { mutableStateOf(plan.description) }
    var perWeek by remember { mutableStateOf<Int?>(plan.workoutsPerWeek.takeIf { it > 0 }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פרטי התוכנית") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("שם") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(goal, { goal = it }, label = { Text("מטרה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("תיאור") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("אימונים בשבוע (ריק = לפי מספר הימים)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IntField(perWeek, { perWeek = it }, Modifier.width(72.dp), maxDigits = 1)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(plan.copy(name = name, goal = goal.trim(), description = desc.trim(), workoutsPerWeek = (perWeek ?: 0).coerceIn(0, 7))) }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun WorkoutInfoDialog(w: PlannedWorkout, onSave: (PlannedWorkout) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(w.name) }
    var desc by remember { mutableStateOf(w.description) }
    var day by remember { mutableStateOf(w.weekday) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פרטי יום האימון") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("שם") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("תיאור") }, modifier = Modifier.fillMaxWidth())
                Text("יום קבוע בשבוע", style = MaterialTheme.typography.labelMedium)
                WeekdayPicker(day) { day = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(w.copy(name = name, description = desc.trim(), weekday = day)) }) { Text("שמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WeekdayPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("ללא") })
        TimeUtils.ISRAELI_WEEK_ORDER.forEach { iso ->
            FilterChip(selected = selected == iso, onClick = { onSelect(if (selected == iso) null else iso) }, label = { Text(TimeUtils.weekdayShortHe(iso)) })
        }
    }
}
