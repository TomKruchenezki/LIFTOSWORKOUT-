package app.liftos.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.library.ExerciseFilter
import app.liftos.domain.library.ExerciseSearch
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.MuscleGroup
import app.liftos.ui.components.ChipRow

/**
 * Full-height sheet for choosing an exercise (add to a plan, add to a workout, swap).
 * [suggestedIds] (usually the alternatives of the exercise being replaced) are listed first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    container: AppContainer,
    title: String,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    suggestedIds: List<String> = emptyList(),
) {
    val all by container.exercises.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(ExerciseFilter()) }
    val results = remember(all, filter) { ExerciseSearch.apply(all, filter) }
    val suggested = remember(all, suggestedIds) { all.filter { it.id in suggestedIds && !it.isArchived } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = filter.query,
                onValueChange = { filter = filter.copy(query = it) },
                placeholder = { Text("חיפוש לפי שם בעברית או באנגלית") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ChipRow(
                options = MuscleGroup.entries.filter { m -> all.any { it.primaryMuscle == m } },
                selected = filter.muscle,
                label = { it.labelHe },
                onSelect = { filter = filter.copy(muscle = it) },
            )
            ChipRow(
                options = Equipment.entries.filter { e -> all.any { it.equipment == e } },
                selected = filter.equipment,
                label = { it.labelHe },
                onSelect = { filter = filter.copy(equipment = it) },
                modifier = Modifier.padding(top = 4.dp),
            )
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                if (suggested.isNotEmpty() && !filter.isActive) {
                    item { Text("חלופות מומלצות", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                    items(suggested, key = { "s-" + it.id }) { ExerciseRow(it, onClick = { onPick(it) }) }
                    item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                }
                items(results, key = { it.id }) { ExerciseRow(it, onClick = { onPick(it) }) }
                if (results.isEmpty()) {
                    item { Text("לא נמצאו תרגילים", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun ExerciseRow(exercise: Exercise, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.nameHe, style = MaterialTheme.typography.titleSmall)
            Text(exercise.nameEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(exercise.primaryMuscle.labelHe, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(exercise.equipment.labelHe, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
