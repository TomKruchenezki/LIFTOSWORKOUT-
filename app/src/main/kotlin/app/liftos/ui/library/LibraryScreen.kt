package app.liftos.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import app.liftos.domain.model.Difficulty
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup
import app.liftos.ui.components.ChipRow
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.ScreenTopBar

@Composable
fun LibraryScreen(container: AppContainer, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val all by container.exercises.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(ExerciseFilter()) }
    val results = remember(all, filter) { ExerciseSearch.apply(all, filter) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar("ספריית תרגילים", actions = {
                IconButton(onClick = onCreate) { Icon(Icons.Rounded.Add, contentDescription = "תרגיל חדש") }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { filter = filter.copy(query = it) },
                placeholder = { Text("חיפוש לפי שם בעברית או באנגלית") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ChipRow(
                options = MuscleGroup.entries.filter { m -> all.any { it.primaryMuscle == m } },
                selected = filter.muscle, label = { it.labelHe }, onSelect = { filter = filter.copy(muscle = it) },
            )
            ChipRow(
                options = Equipment.entries.filter { e -> all.any { it.equipment == e } },
                selected = filter.equipment, label = { it.labelHe }, onSelect = { filter = filter.copy(equipment = it) },
                modifier = Modifier.padding(top = 2.dp),
            )
            ChipRow(
                options = ExerciseType.entries,
                selected = filter.type, label = { it.labelHe }, onSelect = { filter = filter.copy(type = it) },
                modifier = Modifier.padding(top = 2.dp),
            )
            ChipRow(
                options = Difficulty.entries,
                selected = filter.difficulty, label = { it.labelHe }, onSelect = { filter = filter.copy(difficulty = it) },
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "${results.size} תרגילים" + if (filter.isActive) " (מסונן)" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(results, key = { it.id }) { ex ->
                    ExerciseRow(ex, onClick = { onOpen(ex.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (results.isEmpty()) {
                    item { EmptyState("לא נמצאו תרגילים", "נסה לשנות את החיפוש או לנקות מסננים. אפשר גם ליצור תרגיל אישי עם הכפתור למעלה.") }
                }
            }
        }
    }
}
