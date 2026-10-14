package app.liftos.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.RecordEntry
import app.liftos.domain.analytics.RecordKind
import app.liftos.domain.logic.Formatters
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.util.RecordText
import app.liftos.ui.util.ltr

/** All personal records in one place. Every row opens the workout in which the record was set. */
@Composable
fun RecordsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val data by container.analyticsData.collectAsStateWithLifecycle(initialValue = null)
    var query by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar("שיאים אישיים", onBack = onBack) },
    ) { padding ->
        val d = data
        if (d == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        ScrollPage(Modifier.padding(padding)) {
            if (d.records.current.isEmpty()) {
                EmptyState(
                    title = "עוד אין שיאים",
                    subtitle = "שיא נרשם מהאימון השני בתרגיל ואילך, כשמשהו משתפר לעומת הפעמים הקודמות. האימון הראשון בתרגיל רק קובע בסיס להשוואה.",
                    icon = Icons.Rounded.EmojiEvents,
                )
                return@ScrollPage
            }

            val recent = d.records.events
                .filter { it.kind != RecordKind.REPS_AT_WEIGHT && it.kind != RecordKind.BEST_VOLUME }
                .sortedByDescending { it.startedAt }.take(8)
            if (recent.isNotEmpty()) {
                SectionTitle("שיאים אחרונים")
                LiftCard(Modifier.fillMaxWidth()) {
                    recent.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(e.exerciseName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    ltr("${e.kind.labelHe}${if (e.kind.isEstimate) " (הערכה)" else ""}: ${RecordText.valueText(e.kind, e.newValue, e.weightKg, e.reps)}") +
                                        " · ${Formatters.date(e.date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.TextButton(onClick = { onOpenSession(e.sessionId) }) { Text("פתח") }
                        }
                    }
                }
            }

            SectionTitle("כל השיאים לפי תרגיל")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("חיפוש תרגיל") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val byExercise = d.records.current.groupBy { it.exerciseKey }
                .filter { (_, list) -> query.isBlank() || list.first().exerciseName.contains(query.trim(), ignoreCase = true) }
                .toList().sortedBy { it.second.first().exerciseName }
            if (byExercise.isEmpty()) Text("לא נמצאו תרגילים.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            byExercise.forEach { (key, list) -> ExerciseRecords(key, list, onOpenSession, onOpenExercise) }
            Text(
                "שיא נקבע רק כשערך חדש גבוה בבירור מהקודם. 1RM משוער הוא הערכה לפי נוסחת Epley, לא הרמה שנמדדה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExerciseRecords(
    key: String,
    list: List<RecordEntry>,
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val name = list.first().exerciseName
    LiftCard(Modifier.fillMaxWidth(), onClick = { onOpenExercise(key) }, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        list.sortedBy { it.kind.priority }.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.kind.labelHe + if (r.kind.isEstimate) " (הערכה)" else "", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${Formatters.date(r.date)} · ${r.sessionTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(ltr(RecordText.describe(r)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                androidx.compose.material3.TextButton(onClick = { onOpenSession(r.sessionId) }) { Text("אימון") }
            }
        }
    }
}
