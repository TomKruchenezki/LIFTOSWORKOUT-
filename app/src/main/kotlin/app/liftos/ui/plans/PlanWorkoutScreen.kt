package app.liftos.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.logic.Formatters
import app.liftos.domain.model.EffortMode
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedWorkoutDetail
import app.liftos.domain.model.TrackingType
import app.liftos.service.WorkoutService
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.DecimalField
import app.liftos.ui.components.IntField
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.appViewModel
import app.liftos.ui.library.ExercisePickerSheet
import app.liftos.ui.util.ltr
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class PlanWorkoutViewModel(private val c: AppContainer, private val workoutId: String) : ViewModel() {
    val detail: StateFlow<PlannedWorkoutDetail?> = c.plans.observeWorkoutDetail(workoutId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val exerciseNames: StateFlow<Map<String, Exercise>> = c.exercises.observeAll().map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun add(e: Exercise) = viewModelScope.launch { c.plans.addExercise(workoutId, e, c.settings.get().defaultRestFor(e.type)) }
    fun update(p: PlannedExercise) = viewModelScope.launch { c.plans.updatePlannedExercise(p) }
    fun delete(id: String) = viewModelScope.launch { c.plans.deletePlannedExercise(id) }
    fun duplicate(id: String) = viewModelScope.launch { c.plans.duplicatePlannedExercise(id) }
    fun swap(id: String, e: Exercise) = viewModelScope.launch { c.plans.swapExercise(id, e) }
    fun reorder(ids: List<String>) = viewModelScope.launch { c.plans.reorderPlannedExercises(workoutId, ids) }
    fun start(onStarted: (String) -> Unit) = viewModelScope.launch {
        val id = c.workouts.startFromPlan(workoutId)
        WorkoutService.start(c.app)
        onStarted(id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanWorkoutScreen(container: AppContainer, workoutId: String, onBack: () -> Unit, onStarted: (String) -> Unit) {
    val vm = appViewModel(key = "pw-$workoutId") { PlanWorkoutViewModel(container, workoutId) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val names by vm.exerciseNames.collectAsStateWithLifecycle()
    val settings by container.settings.observe().collectAsStateWithLifecycle(initialValue = app.liftos.domain.model.AppSettings())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlannedExercise?>(null) }
    var swapping by remember { mutableStateOf<PlannedExercise?>(null) }
    var deleting by remember { mutableStateOf<PlannedExercise?>(null) }

    val d = detail
    if (d == null) {
        LoadingBox()
        return
    }

    // Local copy of the order so dragging animates smoothly; it is written to the database when the drag ends.
    var local by remember(d.exercises) { mutableStateOf(d.exercises) }
    val localState = androidx.compose.runtime.rememberUpdatedState(local)
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        local = local.toMutableList().apply { add(to.index - 1, removeAt(from.index - 1)) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar(d.workout.name, onBack = onBack, actions = {
                IconButton(onClick = { vm.start(onStarted) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "התחל אימון") }
            })
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (d.workout.description.isNotBlank()) Text(d.workout.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${local.size} תרגילים · ${local.sumOf { it.targetSets }} סטים. גרור את הידית כדי לשנות סדר, לחץ על תרגיל כדי לערוך.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(local, key = { _, p -> p.id }) { _, p ->
                ReorderableItem(reorderState, key = p.id) { dragging ->
                    val ex = names[p.exerciseId]
                    PlannedExerciseCard(
                        p = p,
                        exercise = ex,
                        effort = settings.effortMode,
                        dragging = dragging,
                        handle = Modifier.draggableHandle(onDragStopped = { vm.reorder(localState.value.map { it.id }) }),
                        onClick = { editing = p },
                        onSwap = { swapping = p },
                        onDuplicate = { vm.duplicate(p.id) },
                        onDelete = { deleting = p },
                    )
                }
            }
            item(key = "add") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    app.liftos.ui.components.SoftButton("הוסף תרגיל", onClick = { adding = true }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                    BigButton("התחל אימון זה", onClick = { vm.start(onStarted) }, icon = Icons.Rounded.PlayArrow)
                }
            }
        }
    }

    if (adding) {
        ExercisePickerSheet(container, "הוספת תרגיל לאימון", onPick = { vm.add(it); adding = false }, onDismiss = { adding = false })
    }
    swapping?.let { p ->
        ExercisePickerSheet(
            container, "החלפת תרגיל",
            suggestedIds = names[p.exerciseId]?.alternativeIds.orEmpty(),
            onPick = { vm.swap(p.id, it); swapping = null },
            onDismiss = { swapping = null },
        )
    }
    editing?.let { p ->
        PlannedExerciseSheet(
            p = p,
            exercise = names[p.exerciseId],
            effort = settings.effortMode,
            onSave = { vm.update(it); editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            "להסיר את התרגיל?", "'${names[p.exerciseId]?.nameHe ?: "התרגיל"}' יוסר מהתוכנית. אימונים שכבר בוצעו לא יושפעו.", "הסר", destructive = true,
            onConfirm = { vm.delete(p.id); deleting = null }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PlannedExerciseCard(
    p: PlannedExercise,
    exercise: Exercise?,
    effort: EffortMode,
    dragging: Boolean,
    handle: Modifier,
    onClick: () -> Unit,
    onSwap: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val unit = if (exercise?.trackingType == TrackingType.DURATION) "שנ׳" else ""
    LiftCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        container = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DragHandle, contentDescription = "גרור לשינוי סדר", modifier = handle.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(exercise?.nameHe ?: "תרגיל לא ידוע", style = MaterialTheme.typography.titleSmall)
                Text(exercise?.nameEn.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val range = if (p.repsMin == p.repsMax) "${p.repsMin}" else "${p.repsMin}–${p.repsMax}"
                val parts = buildList {
                    add(ltr("${p.targetSets} × $range$unit"))
                    p.targetWeightKg?.let { add(ltr(Formatters.number(it)) + " ק״ג") }
                    if (effort == EffortMode.RPE) p.targetRpe?.let { add("RPE ${ltr(Formatters.number(it, 1))}") } else p.targetRir?.let { add("RIR ${ltr(it.toString())}") }
                    if (p.restSeconds > 0) add("מנוחה " + ltr(Formatters.duration(p.restSeconds * 1000L)))
                    if (p.warmupSets > 0) add("${p.warmupSets} חימום")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("ערוך") }, onClick = { menu = false; onClick() })
                    DropdownMenuItem(text = { Text("החלף תרגיל") }, onClick = { menu = false; onSwap() })
                    DropdownMenuItem(text = { Text("שכפל") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("הסר", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** Editor for sets / reps / target weight / effort / rest / warm-ups / notes of one planned exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannedExerciseSheet(
    p: PlannedExercise,
    exercise: Exercise?,
    effort: EffortMode,
    onSave: (PlannedExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var sets by remember { mutableStateOf<Int?>(p.targetSets) }
    var min by remember { mutableStateOf<Int?>(p.repsMin) }
    var max by remember { mutableStateOf<Int?>(p.repsMax) }
    var weight by remember { mutableStateOf(p.targetWeightKg) }
    var rir by remember { mutableStateOf(p.targetRir) }
    var rpe by remember { mutableStateOf(p.targetRpe) }
    var rest by remember { mutableStateOf<Int?>(p.restSeconds) }
    var warm by remember { mutableStateOf<Int?>(p.warmupSets) }
    var notes by remember { mutableStateOf(p.notes) }
    val isDuration = exercise?.trackingType == TrackingType.DURATION

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(exercise?.nameHe ?: "תרגיל", style = MaterialTheme.typography.titleLarge)
            Text(exercise?.nameEn.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LabeledStepper("מספר סטים", sets, { sets = it }, 1, 30)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isDuration) "טווח שניות" else "טווח חזרות", Modifier.weight(1f))
                IntField(min, { min = it }, Modifier.width(80.dp), maxDigits = 3, placeholder = "מ-")
                Text("עד")
                IntField(max, { max = it }, Modifier.width(80.dp), maxDigits = 3, placeholder = "עד")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("משקל יעד (ק״ג, לא חובה)", Modifier.weight(1f))
                DecimalField(weight, { weight = it }, Modifier.width(110.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (effort == EffortMode.RPE) "RPE יעד (1-10)" else "RIR יעד (חזרות במאגר)", Modifier.weight(1f))
                if (effort == EffortMode.RPE) DecimalField(rpe, { rpe = it }, Modifier.width(80.dp))
                else IntField(rir, { rir = it }, Modifier.width(80.dp), maxDigits = 2)
            }
            LabeledStepper("זמן מנוחה (שניות)", rest, { rest = it }, 0, 600, step = 15)
            LabeledStepper("סטי חימום אוטומטיים", warm, { warm = it }, 0, 5)
            OutlinedTextField(notes, { notes = it }, label = { Text("הערות טכניות") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            BigButton("שמור", onClick = {
                val lo = (min ?: p.repsMin).coerceAtLeast(1)
                val hi = (max ?: p.repsMax).coerceAtLeast(lo)
                onSave(
                    p.copy(
                        targetSets = (sets ?: p.targetSets).coerceIn(1, 30), repsMin = lo, repsMax = hi,
                        targetWeightKg = weight?.takeIf { it > 0 }, targetRir = rir?.coerceIn(0, 10),
                        targetRpe = rpe?.coerceIn(0.0, 10.0), restSeconds = (rest ?: p.restSeconds).coerceIn(0, 3600),
                        warmupSets = (warm ?: 0).coerceIn(0, 10), notes = notes.trim(),
                    ),
                )
            })
        }
    }
}

@Composable
private fun LabeledStepper(label: String, value: Int?, onChange: (Int?) -> Unit, minV: Int, maxV: Int, step: Int = 1) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        androidx.compose.material3.FilledTonalIconButton(onClick = { onChange(((value ?: minV) - step).coerceAtLeast(minV)) }) { Text("−") }
        IntField(value, onChange, Modifier.width(84.dp), maxDigits = 3)
        androidx.compose.material3.FilledTonalIconButton(onClick = { onChange(((value ?: minV) + step).coerceAtMost(maxV)) }) { Text("+") }
    }
}
