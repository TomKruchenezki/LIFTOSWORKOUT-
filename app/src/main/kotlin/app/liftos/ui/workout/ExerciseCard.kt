package app.liftos.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.PreviousPerformance
import app.liftos.domain.logic.SetEditing
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.EffortMode
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType
import app.liftos.domain.progression.ProgressionEngine
import app.liftos.domain.progression.SuggestionKind
import app.liftos.ui.components.DecimalField
import app.liftos.ui.components.IntField
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.TextInputDialog
import app.liftos.ui.util.ltr

/** Everything a card can ask the screen to do. */
class ExerciseActions(
    val setWeight: (String, Double?) -> Unit,
    val setReps: (String, Int?) -> Unit,
    val setDuration: (String, Int?) -> Unit,
    val setRir: (String, Int?) -> Unit,
    val setRpe: (String, Double?) -> Unit,
    val toggle: (String) -> Unit,
    val addSet: (String, SetType) -> Unit,
    val deleteSet: (String) -> Unit,
    val changeType: (String, SetType) -> Unit,
    val setFailure: (String, Boolean) -> Unit,
    val setSetNotes: (String, String) -> Unit,
    val setExerciseNotes: (String, String) -> Unit,
    val replace: (String) -> Unit,
    val remove: (String) -> Unit,
    val skip: (String) -> Unit,
    val unskip: (String) -> Unit,
    val openInfo: (String) -> Unit,
    val onFocus: (FocusedField, Boolean) -> Unit,
)

@Composable
fun ExerciseCard(
    item: ExerciseWithSets,
    previous: PreviousPerformance?,
    effortMode: EffortMode,
    actions: ExerciseActions,
    modifier: Modifier = Modifier,
    hint: ExerciseHint? = null,
    hasRecord: Boolean = false,
    onHint: () -> Unit = {},
) {
    val ex = item.exercise
    val allDone = item.sets.isNotEmpty() && item.sets.all { it.isCompleted } && !ex.skipped
    var menu by remember { mutableStateOf(false) }
    var showNotes by remember(ex.id) { mutableStateOf(ex.notes.isNotBlank()) }
    var confirmRemove by remember { mutableStateOf(false) }

    LiftCard(modifier = modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ex.nameHe, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (hasRecord) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            contentDescription = "שיא אישי חדש בתרגיל",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (allDone) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "כל הסטים הושלמו",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (ex.nameEn.isNotBlank()) {
                    Text(ex.nameEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    ex.primaryMuscle.labelHe,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות תרגיל") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (ex.exerciseId != null) {
                        DropdownMenuItem(text = { Text("הסבר והדגמה") }, onClick = { menu = false; actions.openInfo(ex.exerciseId!!) })
                    }
                    DropdownMenuItem(text = { Text("החלף תרגיל") }, onClick = { menu = false; actions.replace(ex.id) })
                    DropdownMenuItem(text = { Text("הוסף סט חימום") }, onClick = { menu = false; actions.addSet(ex.id, SetType.WARMUP) })
                    DropdownMenuItem(text = { Text("הערות לתרגיל") }, onClick = { menu = false; showNotes = true })
                    if (ex.skipped) {
                        DropdownMenuItem(text = { Text("בטל דילוג") }, onClick = { menu = false; actions.unskip(ex.id) })
                    } else {
                        DropdownMenuItem(text = { Text("דלג על התרגיל") }, onClick = { menu = false; actions.skip(ex.id) })
                    }
                    DropdownMenuItem(
                        text = { Text("הסר מהאימון", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirmRemove = true },
                    )
                }
            }
        }

        TargetLine(ex)
        previous?.let { PreviousLine(it, ex) }
        if (hint != null && hint.visibility == ProgressionEngine.Visibility.SHOW && hint.suggestion.kind != SuggestionKind.INSUFFICIENT_DATA) {
            HintChip(hint, onHint)
        }

        if (ex.skipped) {
            Spacer(Modifier.height(8.dp))
            Text(
                "דולג" + if (ex.skipReason.isNotBlank()) ": ${ex.skipReason}" else "",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(10.dp))
        SetsTable(item, previous, effortMode, actions)

        TextButton(onClick = { actions.addSet(ex.id, SetType.WORKING) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("הוסף סט")
        }

        if (showNotes) {
            DebouncedText(
                value = ex.notes,
                label = "הערות לתרגיל",
                onSave = { actions.setExerciseNotes(ex.id, it) },
                minLines = 1,
            )
        }
    }

    if (confirmRemove) {
        app.liftos.ui.components.ConfirmDialog(
            title = "להסיר את התרגיל?",
            text = "התרגיל וכל הסטים שלו יוסרו מהאימון הזה.",
            confirmText = "הסר",
            destructive = true,
            onConfirm = { confirmRemove = false; actions.remove(ex.id) },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun HintChip(hint: ExerciseHint, onClick: () -> Unit) {
    val s = hint.suggestion
    val w = s.suggestedWeightKg?.let { ltr(Formatters.number(it, 2)) + " ק״ג" }
    val text = when (s.kind) {
        SuggestionKind.INCREASE_WEIGHT -> "הצעה: ${w ?: "להעלות משקל"}"
        SuggestionKind.REDUCE_WEIGHT -> "הצעה: ${w ?: "להפחית משקל"}"
        SuggestionKind.ADD_REPS -> "הצעה: להוסיף חזרה" + (w?.let { " ב-$it" } ?: "")
        SuggestionKind.HOLD -> "הצעה: להישאר" + (w?.let { " ב-$it" } ?: "")
        SuggestionKind.REVIEW -> "הצעה: לבדוק התאוששות"
        SuggestionKind.INSUFFICIENT_DATA -> ""
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
            Text("למה?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TargetLine(ex: SessionExercise) {
    if (ex.plannedSets <= 0 && ex.repsMin == null) return
    val unit = if (ex.trackingType == TrackingType.DURATION) "שניות" else "חזרות"
    val range = when {
        ex.repsMin == null || ex.repsMax == null -> ""
        ex.repsMin == ex.repsMax -> "${ex.repsMin}"
        else -> "${ex.repsMin}–${ex.repsMax}"
    }
    val side = if (ex.isUnilateral) " לכל צד" else ""
    val parts = buildList {
        if (range.isNotEmpty()) add("יעד: ${ltr("${ex.plannedSets} × $range")} $unit$side")
        ex.targetRir?.let { add("RIR ${ltr(it.toString())}") }
        if (ex.restSeconds > 0) add("מנוחה ${ltr(Formatters.duration(ex.restSeconds * 1000L))}")
    }
    if (parts.isNotEmpty()) {
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviousLine(prev: PreviousPerformance, ex: SessionExercise) {
    val working = prev.sets.filter { it.setType != SetType.WARMUP && it.isCompleted }
    if (working.isEmpty()) return
    val text = working.joinToString(" · ") { s ->
        when (ex.trackingType) {
            TrackingType.DURATION -> "${s.durationSec ?: 0}״"
            else -> "${Formatters.number(s.weightKg).ifEmpty { "0" }}×${s.reps ?: 0}"
        }
    }
    val date = TimeUtils.parseDate(prev.localDate)?.let { Formatters.date(it).substring(0, 5) } ?: prev.localDate
    Text(
        "קודם ($date): ${ltr(text)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---- sets table ------------------------------------------------------------------------------------------

private val BadgeWidth = 54.dp
private val CheckWidth = 52.dp

@Composable
private fun SetsTable(item: ExerciseWithSets, previous: PreviousPerformance?, effortMode: EffortMode, actions: ExerciseActions) {
    val ex = item.exercise
    val labels = SetEditing.labels(ex, item.sets)
    val prevWorking = previous?.sets.orEmpty().filter { it.setType != SetType.WARMUP }.sortedBy { it.orderIndex }
    val isDuration = ex.trackingType == TrackingType.DURATION
    val weightHeader = if (ex.trackingType == TrackingType.BODYWEIGHT_REPS) "ק״ג נוסף" else "ק״ג"

    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("סט", Modifier.width(BadgeWidth))
        if (isDuration) {
            HeaderCell("שניות", Modifier.weight(1f))
        } else {
            HeaderCell(weightHeader, Modifier.weight(1.1f))
            HeaderCell("חזרות", Modifier.weight(1f))
            HeaderCell(if (effortMode == EffortMode.RPE) "RPE" else "RIR", Modifier.weight(0.8f))
        }
        HeaderCell("בוצע", Modifier.width(CheckWidth))
    }

    var workIndex = 0
    item.sets.forEach { set ->
        val nth = if (set.setType == SetType.WARMUP) -1 else workIndex
        if (set.setType != SetType.WARMUP && (!ex.isUnilateral || set.side != Side.RIGHT)) workIndex++
        // Previous row for this position, used as a hint when the field is empty.
        val hint = if (nth >= 0) {
            val sidePool = if (set.side != null) prevWorking.filter { it.side == set.side } else prevWorking
            val pool = sidePool.ifEmpty { prevWorking }
            val pos = if (ex.isUnilateral) nth else nth
            pool.getOrNull(pos)
        } else null
        SetRow(ex, set, labels[set.id], hint, effortMode, actions)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun SetRow(
    ex: SessionExercise,
    set: ExerciseSet,
    label: app.liftos.domain.logic.SetLabel?,
    hint: ExerciseSet?,
    effortMode: EffortMode,
    actions: ExerciseActions,
) {
    var typeMenu by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf(false) }
    val done = set.isCompleted
    val colors = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(14.dp)
    // A short fade when a set is ticked, so the change is noticed without looking for it.
    val rowColor by animateColorAsState(
        if (done) colors.primaryContainer.copy(alpha = 0.45f) else colors.surfaceContainerLow,
        animationSpec = tween(durationMillis = 220),
        label = "set-row-color",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowColor)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Set badge - tap for warm-up / drop / failure / note / delete.
        Box(Modifier.width(BadgeWidth)) {
            val badgeColor = when (set.setType) {
                SetType.WARMUP -> colors.tertiaryContainer
                SetType.DROP -> colors.secondaryContainer
                SetType.WORKING -> colors.surfaceContainerHighest
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor)
                    .clickable { typeMenu = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(label?.number ?: "", style = MaterialTheme.typography.titleSmall)
                val sub = buildString {
                    label?.side?.let { append(it.shortHe) }
                    if (set.setType == SetType.DROP) append(if (isNotEmpty()) " D" else "D")
                    if (set.isFailure) append(if (isNotEmpty()) " ·כשל" else "כשל")
                }
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                DropdownMenuItem(text = { Text("סט עבודה") }, onClick = { typeMenu = false; actions.changeType(set.id, SetType.WORKING) })
                DropdownMenuItem(text = { Text("סט חימום") }, onClick = { typeMenu = false; actions.changeType(set.id, SetType.WARMUP) })
                DropdownMenuItem(text = { Text("דרופ סט") }, onClick = { typeMenu = false; actions.changeType(set.id, SetType.DROP) })
                DropdownMenuItem(
                    text = { Text(if (set.isFailure) "בטל סימון כשל שרירי" else "סמן כשל שרירי") },
                    onClick = { typeMenu = false; actions.setFailure(set.id, !set.isFailure) },
                )
                DropdownMenuItem(text = { Text("הערה לסט") }, onClick = { typeMenu = false; editNote = true })
                DropdownMenuItem(
                    text = { Text("מחק סט", color = colors.error) },
                    onClick = { typeMenu = false; actions.deleteSet(set.id) },
                )
            }
        }

        if (ex.trackingType == TrackingType.DURATION) {
            IntField(
                value = set.durationSec,
                onValueChange = { actions.setDuration(set.id, it) },
                modifier = Modifier.weight(1f),
                placeholder = hint?.durationSec?.toString() ?: ex.repsMin?.toString().orEmpty(),
                maxDigits = 4,
                highlight = done,
                onFocusChanged = { f -> actions.onFocus(FocusedField(set.id, FieldKind.DURATION), f) },
            )
        } else {
            DecimalField(
                value = set.weightKg,
                onValueChange = { actions.setWeight(set.id, it) },
                modifier = Modifier.weight(1.1f),
                placeholder = Formatters.number(hint?.weightKg ?: ex.targetWeightKg),
                highlight = done,
                onFocusChanged = { f -> actions.onFocus(FocusedField(set.id, FieldKind.WEIGHT), f) },
            )
            IntField(
                value = set.reps,
                onValueChange = { actions.setReps(set.id, it) },
                modifier = Modifier.weight(1f),
                placeholder = repsHint(ex),
                highlight = done,
                onFocusChanged = { f -> actions.onFocus(FocusedField(set.id, FieldKind.REPS), f) },
            )
            if (effortMode == EffortMode.RPE) {
                DecimalField(
                    value = set.rpe,
                    onValueChange = { actions.setRpe(set.id, it) },
                    modifier = Modifier.weight(0.8f),
                    placeholder = Formatters.number(ex.targetRpe, 1),
                    highlight = done,
                    onFocusChanged = { f -> actions.onFocus(FocusedField(set.id, FieldKind.EFFORT), f) },
                )
            } else {
                IntField(
                    value = set.rir,
                    onValueChange = { actions.setRir(set.id, it) },
                    modifier = Modifier.weight(0.8f),
                    placeholder = ex.targetRir?.toString().orEmpty(),
                    maxDigits = 2,
                    highlight = done,
                    onFocusChanged = { f -> actions.onFocus(FocusedField(set.id, FieldKind.EFFORT), f) },
                )
            }
        }

        CheckButton(done = done, onClick = { actions.toggle(set.id) })
    }

    if (editNote) {
        TextInputDialog(
            title = "הערה לסט",
            label = "הערה",
            initial = set.notes,
            singleLine = false,
            onConfirm = { editNote = false; actions.setSetNotes(set.id, it) },
            onDismiss = { editNote = false },
        )
    }
    if (set.notes.isNotBlank()) {
        Text(
            "הערה: ${set.notes}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp),
        )
    }
}

private fun repsHint(ex: SessionExercise): String = when {
    ex.repsMin == null || ex.repsMax == null -> ""
    ex.repsMin == ex.repsMax -> ex.repsMin.toString()
    else -> "${ex.repsMin}-${ex.repsMax}"
}

@Composable
private fun CheckButton(done: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (done) colors.primary else colors.surfaceContainerHighest,
        border = if (done) null else BorderStroke(2.dp, colors.outline),
        modifier = Modifier.width(CheckWidth).height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Rounded.Check, contentDescription = "בוטל סימון", tint = colors.onPrimary, modifier = Modifier.size(28.dp))
            else Icon(Icons.Rounded.Check, contentDescription = "סמן כבוצע", tint = colors.outline, modifier = Modifier.size(24.dp))
        }
    }
}

/** Text field that saves ~0.5 s after the last keystroke and once more when it leaves the screen. */
@Composable
fun DebouncedText(
    value: String,
    label: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 2,
) {
    var text by remember { mutableStateOf(value) }
    var latestSaved by remember { mutableStateOf(value) }
    androidx.compose.runtime.LaunchedEffect(text) {
        if (text != latestSaved) {
            kotlinx.coroutines.delay(500)
            onSave(text)
            latestSaved = text
        }
    }
    val textNow by androidx.compose.runtime.rememberUpdatedState(text)
    val savedNow by androidx.compose.runtime.rememberUpdatedState(latestSaved)
    val onSaveNow by androidx.compose.runtime.rememberUpdatedState(onSave)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (textNow != savedNow) onSaveNow(textNow) }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
    )
}
