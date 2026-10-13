package app.liftos.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import app.liftos.di.AppContainer
import app.liftos.domain.library.YouTube
import app.liftos.domain.model.Difficulty
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.TrackingType
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.nav.Routes
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    container: AppContainer,
    exerciseId: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val isNew = exerciseId == Routes.NEW
    var source by remember { mutableStateOf<Exercise?>(null) }
    var loaded by remember { mutableStateOf(isNew) }
    LaunchedEffect(exerciseId) {
        if (!isNew) {
            source = container.exercises.get(exerciseId)
            loaded = true
        }
    }
    if (!loaded) {
        LoadingBox()
        return
    }
    if (!isNew && (source == null || source?.isBuiltIn == true)) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val base = source
    val scope = rememberCoroutineScope()

    var nameHe by remember { mutableStateOf(base?.nameHe.orEmpty()) }
    var nameEn by remember { mutableStateOf(base?.nameEn.orEmpty()) }
    var desc by remember { mutableStateOf(base?.description.orEmpty()) }
    var primary by remember { mutableStateOf(base?.primaryMuscle ?: MuscleGroup.CHEST) }
    var secondary by remember { mutableStateOf(base?.secondaryMuscles?.toSet().orEmpty()) }
    var equipment by remember { mutableStateOf(base?.equipment ?: Equipment.MACHINE) }
    var difficulty by remember { mutableStateOf(base?.difficulty ?: Difficulty.INTERMEDIATE) }
    var type by remember { mutableStateOf(base?.type ?: ExerciseType.COMPOUND) }
    var tracking by remember { mutableStateOf(base?.trackingType ?: TrackingType.WEIGHT_REPS) }
    var unilateral by remember { mutableStateOf(base?.isUnilateral ?: false) }
    var video by remember { mutableStateOf(base?.videoUrl.orEmpty()) }
    var steps by remember { mutableStateOf(base?.instructions?.joinToString("\n").orEmpty()) }
    var mistakes by remember { mutableStateOf(base?.mistakes?.joinToString("\n").orEmpty()) }
    var safety by remember { mutableStateOf(base?.safety?.joinToString("\n").orEmpty()) }

    val videoOk = video.isBlank() || YouTube.isValidWebUrl(video)
    val canSave = nameHe.isNotBlank() && videoOk

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar(if (isNew) "תרגיל חדש" else "עריכת תרגיל", onBack = onBack) },
    ) { padding ->
        ScrollPage(Modifier.padding(padding)) {
            OutlinedTextField(nameHe, { nameHe = it }, label = { Text("שם בעברית *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nameEn, { nameEn = it }, label = { Text("שם באנגלית (משמש לחיפוש הדגמה)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("הסבר קצר") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            SectionTitle("קבוצת שרירים ראשית")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleGroup.entries.forEach { m -> FilterChip(primary == m, { primary = m; secondary = secondary - m }, { Text(m.labelHe) }) }
            }
            SectionTitle("שרירים משניים")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleGroup.entries.filter { it != primary }.forEach { m ->
                    FilterChip(m in secondary, { secondary = if (m in secondary) secondary - m else secondary + m }, { Text(m.labelHe) })
                }
            }
            SectionTitle("ציוד")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Equipment.entries.forEach { e -> FilterChip(equipment == e, { equipment = e }, { Text(e.labelHe) }) }
            }
            SectionTitle("סוג ורמת קושי")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.entries.forEach { t -> FilterChip(type == t, { type = t }, { Text(t.labelHe) }) }
                Difficulty.entries.forEach { d -> FilterChip(difficulty == d, { difficulty = d }, { Text(d.labelHe) }) }
            }
            SectionTitle("איך מתעדים סט")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackingType.entries.forEach { t -> FilterChip(tracking == t, { tracking = t }, { Text(t.labelHe) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("תרגיל חד-צדדי (ימין ושמאל בנפרד)", Modifier.weight(1f))
                Switch(unilateral, { unilateral = it })
            }
            OutlinedTextField(
                video, { video = it },
                label = { Text("קישור לסרטון הדגמה (לא חובה)") },
                isError = !videoOk,
                supportingText = { if (!videoOk) Text("הקישור חייב להתחיל ב-https://") else Text("אם לא תזין קישור, יוצג חיפוש ביוטיוב לפי השם באנגלית") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(steps, { steps = it }, label = { Text("שלבי ביצוע (שורה לכל שלב)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(mistakes, { mistakes = it }, label = { Text("טעויות נפוצות (שורה לכל טעות)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(safety, { safety = it }, label = { Text("דגשי בטיחות (שורה לכל דגש)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            BigButton("שמור תרגיל", enabled = canSave, onClick = {
                val draft = (base ?: Exercise(id = "", nameHe = "", nameEn = "", primaryMuscle = primary)).copy(
                    nameHe = nameHe.trim(), nameEn = nameEn.trim().ifBlank { nameHe.trim() }, description = desc.trim(),
                    primaryMuscle = primary, secondaryMuscles = secondary.toList(), equipment = equipment,
                    difficulty = difficulty, type = type, trackingType = tracking, isUnilateral = unilateral,
                    videoUrl = video.trim().ifBlank { null },
                    instructions = steps.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    mistakes = mistakes.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    safety = safety.lines().map { it.trim() }.filter { it.isNotEmpty() },
                )
                scope.launch {
                    val id = if (isNew || base == null) container.exercises.createCustom(draft) else {
                        container.exercises.updateCustom(draft)
                        draft.id
                    }
                    onSaved(id)
                }
            })
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Text("* שדה חובה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
