package app.liftos.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.model.WorkoutPlan
import app.liftos.ui.components.InfoChip
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.TextInputDialog
import app.liftos.ui.components.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanRow(val plan: WorkoutPlan, val workouts: Int, val exercises: Int, val isActive: Boolean)

data class PlansState(val loading: Boolean = true, val plans: List<PlanRow> = emptyList(), val templates: List<PlanRow> = emptyList())

class PlansViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<PlansState> = combine(c.plans.observePlans(), c.plans.observePlanStats(), c.settings.observe()) { plans, stats, settings ->
        val byId = stats.associateBy { it.planId }
        val rows = plans.map { PlanRow(it, byId[it.id]?.workouts ?: 0, byId[it.id]?.exercises ?: 0, it.id == settings.activePlanId) }
        PlansState(false, rows.filter { !it.plan.isTemplate }, rows.filter { it.plan.isTemplate })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansState())

    fun create(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(c.plans.createPlan(name)) }
    }

    fun activate(id: String) = viewModelScope.launch { c.plans.setActivePlan(id) }
    fun duplicate(id: String) = viewModelScope.launch { c.plans.duplicatePlan(id) }
    fun saveAsTemplate(id: String) = viewModelScope.launch { c.plans.saveAsTemplate(id) }
    fun useTemplate(id: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { c.plans.createFromTemplate(id)?.let(onCreated) }
    }

    fun delete(id: String) = viewModelScope.launch { c.plans.deletePlan(id) }
}

@Composable
fun PlansScreen(container: AppContainer, onOpenPlan: (String) -> Unit, onOpenLibrary: () -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel { PlansViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlanRow?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar("תוכניות אימון", actions = {
                IconButton(onClick = onOpenLibrary) { Icon(Icons.Rounded.FitnessCenter, contentDescription = "ספריית תרגילים") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "הגדרות") }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("תוכנית חדשה") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.loading && state.plans.isEmpty()) {
                item {
                    EmptyState(
                        title = "אין עדיין תוכניות",
                        subtitle = "צור תוכנית חדשה, או השתמש בתבנית שמורה.",
                        icon = Icons.Rounded.CalendarMonth,
                    )
                }
            }
            items(state.plans, key = { it.plan.id }) { row ->
                PlanCard(
                    row = row,
                    onClick = { onOpenPlan(row.plan.id) },
                    onActivate = { vm.activate(row.plan.id) },
                    onDuplicate = { vm.duplicate(row.plan.id) },
                    onTemplate = { vm.saveAsTemplate(row.plan.id) },
                    onUseTemplate = null,
                    onDelete = { deleting = row },
                )
            }
            if (state.templates.isNotEmpty()) {
                item { SectionTitle("תבניות") }
                items(state.templates, key = { "t-" + it.plan.id }) { row ->
                    PlanCard(
                        row = row,
                        onClick = { onOpenPlan(row.plan.id) },
                        onActivate = null,
                        onDuplicate = { vm.duplicate(row.plan.id) },
                        onTemplate = null,
                        onUseTemplate = { vm.useTemplate(row.plan.id, onOpenPlan) },
                        onDelete = { deleting = row },
                    )
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "תוכנית חדשה",
            label = "שם התוכנית",
            initial = "",
            confirmText = "צור",
            onConfirm = { name -> creating = false; vm.create(name, onOpenPlan) },
            onDismiss = { creating = false },
        )
    }
    deleting?.let { row ->
        ConfirmDialog(
            title = "למחוק את התוכנית?",
            text = "התוכנית '${row.plan.name}' תימחק. אימונים שכבר בוצעו לא יושפעו וייוותרו בהיסטוריה.",
            confirmText = "מחק",
            destructive = true,
            onConfirm = { deleting = null; vm.delete(row.plan.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PlanCard(
    row: PlanRow,
    onClick: () -> Unit,
    onActivate: (() -> Unit)?,
    onDuplicate: () -> Unit,
    onTemplate: (() -> Unit)?,
    onUseTemplate: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    LiftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.plan.name, style = MaterialTheme.typography.titleLarge)
                if (row.plan.goal.isNotBlank()) {
                    Text(row.plan.goal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${row.workouts} אימונים · ${row.exercises} תרגילים",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.isActive) {
                    InfoChip("תוכנית פעילה")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onActivate?.let { DropdownMenuItem(text = { Text("הפוך לתוכנית פעילה") }, onClick = { menu = false; it() }) }
                    onUseTemplate?.let { DropdownMenuItem(text = { Text("צור תוכנית מהתבנית") }, onClick = { menu = false; it() }) }
                    DropdownMenuItem(text = { Text("שכפל") }, onClick = { menu = false; onDuplicate() })
                    onTemplate?.let { DropdownMenuItem(text = { Text("שמור כתבנית") }, onClick = { menu = false; it() }) }
                    DropdownMenuItem(text = { Text("מחק", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
