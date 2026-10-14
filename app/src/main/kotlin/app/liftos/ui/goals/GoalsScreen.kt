package app.liftos.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import app.liftos.domain.goals.BodyField
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalStatus
import app.liftos.domain.model.GoalType
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.DecimalTextField
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.appViewModel
import app.liftos.ui.library.ExercisePickerSheet
import app.liftos.ui.util.ltr
import java.time.LocalDate

@Composable
fun GoalsScreen(container: AppContainer, onBack: () -> Unit, onOpenExercise: (String) -> Unit) {
    val vm = appViewModel { GoalsViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar("יעדים", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("יעד חדש") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        ScrollPage(Modifier.padding(padding)) {
            if (ui.rows.isEmpty()) {
                EmptyState(
                    title = "עוד לא הגדרת יעדים",
                    subtitle = "אפשר להגדיר יעד משקל גוף, היקף, משקל או חזרות בתרגיל, מספר אימונים בשבוע או בחודש, או יעד אישי. ההתקדמות תחושב מהנתונים שתזין.",
                    icon = Icons.Rounded.Flag,
                )
            }
            ui.rows.forEach { row -> GoalCard(row.goal, row.progress, onClick = { openId = row.goal.id; vm.select(row.goal.id) }) }
            Spacer(Modifier.height(88.dp))
        }
    }

    if (creating || editing != null) {
        GoalEditorSheet(
            container = container,
            ui = ui,
            existing = editing,
            suggestStart = vm::suggestedStart,
            onSave = { goal -> vm.save(goal) { creating = false; editing = null } },
            onDismiss = { creating = false; editing = null },
        )
    }
    val open = ui.rows.firstOrNull { it.goal.id == openId }
    if (open != null) {
        GoalDetailSheet(
            vm = vm,
            row = open,
            onEdit = { editing = open.goal; openId = null },
            onOpenExercise = onOpenExercise,
            onDismiss = { openId = null; vm.select(null) },
        )
    }
}

@Composable
private fun GoalCard(goal: Goal, p: app.liftos.domain.goals.GoalProgress, onClick: () -> Unit) {
    val achieved = goal.status == GoalStatus.ACHIEVED
    LiftCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        container = if (achieved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (achieved) Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f).padding(start = if (achieved) 8.dp else 0.dp)) {
                Text(goal.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${goal.category.labelHe} · ${goal.type.labelHe}" + if (goal.status != GoalStatus.ACTIVE) " · ${goal.status.labelHe}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val pct = p.percent
        if (pct != null) {
            LinearProgressIndicator(progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                ltr("${Formatters.number(goal.startValue, 1)} ← ${Formatters.number(p.current, 1)} → ${Formatters.number(goal.targetValue, 1)} ${goal.unit}") +
                    "  (${Formatters.number(pct, 0)}%)",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("יעד: ${ltr(Formatters.number(goal.targetValue, 1))} ${goal.unit}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(p.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        goal.targetDate?.let { d ->
            val left = p.daysLeft
            Text(
                "עד ${TimeUtils.parseDate(d)?.let { Formatters.date(it) } ?: d}" + (left?.let { if (it >= 0) " · נותרו $it ימים" else " · עבר התאריך" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalDetailSheet(
    vm: GoalsViewModel,
    row: GoalRow,
    onEdit: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val changes by vm.changes.collectAsStateWithLifecycle()
    val goal = row.goal
    var confirmDelete by remember { mutableStateOf(false) }
    var customValue by remember(goal.id, goal.currentValue) { mutableStateOf(goal.currentValue) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(goal.title, style = MaterialTheme.typography.titleLarge)
            Text(row.progress.message, style = MaterialTheme.typography.bodyMedium)
            if (goal.notes.isNotBlank()) Text(goal.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "התחלה: ${ltr(Formatters.number(goal.startValue, 1))} ${goal.unit} (${TimeUtils.parseDate(goal.startDate)?.let { Formatters.date(it) } ?: goal.startDate})" +
                    "\nיעד: ${ltr(Formatters.number(goal.targetValue, 1))} ${goal.unit}",
            )
            if (goal.type == GoalType.CUSTOM) {
                DecimalTextField("ערך נוכחי", customValue, { customValue = it }, Modifier.fillMaxWidth(), goal.unit)
                SoftButton("שמור ערך נוכחי", onClick = { vm.updateCurrent(goal.id, customValue) }, modifier = Modifier.fillMaxWidth())
            }
            if (goal.type == GoalType.EXERCISE_WEIGHT || goal.type == GoalType.EXERCISE_REPS) {
                SoftButton("התקדמות בתרגיל", onClick = { onOpenExercise(goal.metricKey); onDismiss() }, modifier = Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onEdit, label = { Text("עריכה") })
                when (goal.status) {
                    GoalStatus.ACTIVE -> {
                        AssistChip(onClick = { vm.setStatus(goal.id, GoalStatus.PAUSED) }, label = { Text("השהה") })
                        AssistChip(onClick = { vm.setStatus(goal.id, GoalStatus.ABANDONED) }, label = { Text("נטשתי את היעד") })
                    }
                    else -> AssistChip(onClick = { vm.setStatus(goal.id, GoalStatus.ACTIVE) }, label = { Text("הפעל מחדש") })
                }
                AssistChip(onClick = { confirmDelete = true }, label = { Text("מחק") })
            }
            Text("היסטוריית שינויים", style = MaterialTheme.typography.titleSmall)
            if (changes.isEmpty()) Text("אין עדיין שינויים.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            changes.forEach { ch ->
                val whenText = java.time.Instant.ofEpochMilli(ch.changedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                Text(
                    "${Formatters.date(whenText)} · ${ch.kind.labelHe}" +
                        (if (ch.oldValue != null || ch.newValue != null) ": ${ltr("${ch.oldValue ?: "-"} ← ${ch.newValue ?: "-"}")}" else "") +
                        (if (ch.note.isNotBlank()) " (${ch.note})" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "למחוק את היעד?",
            text = "היעד והיסטוריית השינויים שלו יימחקו. נתוני האימונים והמדידות לא ייפגעו.",
            confirmText = "מחק",
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(goal.id); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalEditorSheet(
    container: AppContainer,
    ui: GoalsUi,
    existing: Goal?,
    suggestStart: (GoalType, String, app.liftos.domain.goals.GoalContext) -> Double?,
    onSave: (Goal) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var type by remember { mutableStateOf(existing?.type ?: GoalType.BODY_WEIGHT) }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var metricKey by remember { mutableStateOf(existing?.metricKey.orEmpty()) }
    var exerciseName by remember {
        mutableStateOf(existing?.takeIf { it.type == GoalType.EXERCISE_WEIGHT || it.type == GoalType.EXERCISE_REPS }
            ?.let { g -> ui.exercises.firstOrNull { it.id == g.metricKey }?.nameHe ?: g.metricKey }.orEmpty())
    }
    var customUnit by remember { mutableStateOf(existing?.takeIf { it.type == GoalType.CUSTOM }?.unit.orEmpty()) }
    var start by remember { mutableStateOf(existing?.startValue) }
    var target by remember { mutableStateOf(existing?.targetValue) }
    var current by remember { mutableStateOf(existing?.currentValue) }
    var startDate by remember { mutableStateOf(existing?.startDate?.let { TimeUtils.parseDate(it) } ?: today) }
    var targetDate by remember { mutableStateOf(existing?.targetDate?.let { TimeUtils.parseDate(it) }) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var pickStartDate by remember { mutableStateOf(false) }
    var pickTargetDate by remember { mutableStateOf(false) }
    var pickExercise by remember { mutableStateOf(false) }

    val unit = unitFor(type, metricKey, customUnit, ui)
    val needsExercise = type == GoalType.EXERCISE_WEIGHT || type == GoalType.EXERCISE_REPS
    val needsField = type == GoalType.BODY_MEASURE
    val valid = target != null && start != null && target != start &&
        (!needsExercise || metricKey.isNotBlank()) && (!needsField || metricKey.isNotBlank())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (existing == null) "יעד חדש" else "עריכת יעד", style = MaterialTheme.typography.titleLarge)

            Text("סוג היעד", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        enabled = existing == null,
                        onClick = {
                            type = t
                            metricKey = if (t == GoalType.PROGRAM_WEEKS) "3" else ""
                            start = suggestStart(t, metricKey, ui.context)
                        },
                        label = { Text(t.labelHe) },
                    )
                }
            }

            if (needsField) {
                Text("איזה מדד?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyField.entries.filter { it != BodyField.WEIGHT }.forEach { f ->
                        FilterChip(
                            selected = metricKey == f.key,
                            onClick = { metricKey = f.key; start = suggestStart(type, f.key, ui.context) },
                            label = { Text(f.labelHe) },
                        )
                    }
                    ui.customMetrics.forEach { m ->
                        FilterChip(
                            selected = metricKey == m.id,
                            onClick = { metricKey = m.id; start = suggestStart(type, m.id, ui.context) },
                            label = { Text(m.name) },
                        )
                    }
                }
            }
            if (needsExercise) {
                SoftButton(
                    if (exerciseName.isBlank()) "בחר תרגיל" else "תרגיל: $exerciseName",
                    onClick = { pickExercise = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (type == GoalType.PROGRAM_WEEKS) {
                Text(
                    "שבוע נחשב כשהושלמו בו לפחות $metricKey אימונים.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1", "2", "3", "4", "5").forEach { n ->
                        FilterChip(selected = metricKey == n, onClick = { metricKey = n }, label = { Text("$n בשבוע") })
                    }
                }
            }
            if (type == GoalType.CUSTOM) {
                OutlinedTextField(customUnit, { customUnit = it }, label = { Text("יחידה (למשל ק״מ, דקות)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                title, { title = it },
                label = { Text("שם היעד (אופציונלי)") },
                placeholder = { Text(defaultTitle(type, exerciseName, metricKey, ui, target, unit)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalTextField("ערך התחלתי", start, { start = it }, Modifier.weight(1f), unit)
                DecimalTextField("ערך יעד", target, { target = it }, Modifier.weight(1f), unit)
            }
            if (type != GoalType.CUSTOM && start == null) {
                Text(
                    "אין עדיין נתונים שממנם אפשר לחשב ערך התחלתי – הזן אותו ידנית.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (type == GoalType.CUSTOM) {
                DecimalTextField("ערך נוכחי", current, { current = it }, Modifier.fillMaxWidth(), unit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickStartDate = true }) { Text("התחלה: ${Formatters.date(startDate)}") }
                TextButton(onClick = { pickTargetDate = true }) { Text("יעד: ${targetDate?.let { Formatters.date(it) } ?: "ללא תאריך"}") }
                if (targetDate != null) TextButton(onClick = { targetDate = null }) { Text("נקה") }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("הערות") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            BigButton("שמור יעד", enabled = valid, onClick = {
                onSave(
                    Goal(
                        id = existing?.id.orEmpty(),
                        title = title.ifBlank { defaultTitle(type, exerciseName, metricKey, ui, target, unit) },
                        type = type, metricKey = metricKey, unit = unit,
                        startValue = start ?: 0.0, targetValue = target ?: 0.0,
                        currentValue = if (type == GoalType.CUSTOM) current else existing?.currentValue,
                        startDate = startDate.toString(), targetDate = targetDate?.toString(),
                        status = existing?.status ?: GoalStatus.ACTIVE, achievedAt = existing?.achievedAt,
                        notes = notes.trim(), createdAt = existing?.createdAt ?: 0L,
                    ),
                )
            })
        }
    }
    if (pickStartDate) DatePickerSheet(startDate, onDismiss = { pickStartDate = false }) { startDate = it }
    if (pickTargetDate) DatePickerSheet(targetDate ?: today.plusDays(30), onDismiss = { pickTargetDate = false }) { targetDate = it }
    if (pickExercise) {
        ExercisePickerSheet(
            container = container,
            title = "בחר תרגיל ליעד",
            onPick = { ex ->
                pickExercise = false
                metricKey = ex.id
                exerciseName = ex.nameHe
                start = suggestStart(type, ex.id, ui.context)
            },
            onDismiss = { pickExercise = false },
        )
    }
}

private fun unitFor(type: GoalType, metricKey: String, customUnit: String, ui: GoalsUi): String = when (type) {
    GoalType.BODY_WEIGHT, GoalType.EXERCISE_WEIGHT -> "ק״ג"
    GoalType.BODY_MEASURE -> BodyField.fromKey(metricKey)?.unit ?: ui.customMetrics.firstOrNull { it.id == metricKey }?.unit.orEmpty()
    GoalType.EXERCISE_REPS -> "חזרות"
    GoalType.WORKOUTS_PER_MONTH, GoalType.WORKOUTS_PER_WEEK -> "אימונים"
    GoalType.PROGRAM_WEEKS -> "שבועות"
    GoalType.CUSTOM -> customUnit.trim()
}

private fun defaultTitle(type: GoalType, exercise: String, metricKey: String, ui: GoalsUi, target: Double?, unit: String): String {
    val t = target?.let { Formatters.number(it, 1) } ?: ""
    return when (type) {
        GoalType.BODY_WEIGHT -> "להגיע למשקל $t ק״ג".trim()
        GoalType.BODY_MEASURE -> {
            val name = BodyField.fromKey(metricKey)?.labelHe ?: ui.customMetrics.firstOrNull { it.id == metricKey }?.name ?: "מדד גוף"
            "$name: $t $unit".trim()
        }
        GoalType.EXERCISE_WEIGHT -> "${exercise.ifBlank { "תרגיל" }} עם $t ק״ג".trim()
        GoalType.EXERCISE_REPS -> "$t חזרות ב${exercise.ifBlank { "תרגיל" }}".trim()
        GoalType.WORKOUTS_PER_MONTH -> "$t אימונים בחודש".trim()
        GoalType.WORKOUTS_PER_WEEK -> "$t אימונים בשבוע".trim()
        GoalType.PROGRAM_WEEKS -> "להשלים תוכנית של $t שבועות".trim()
        GoalType.CUSTOM -> "יעד אישי"
    }
}
