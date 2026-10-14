package app.liftos.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import app.liftos.domain.model.CustomMetric
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.SoftButton

/** Manage the user's own measurement fields (knee circumference, skin fold, ...). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetricsSheet(
    metrics: List<CustomMetric>,
    onAdd: (String, String) -> Unit,
    onRename: (String, String, String) -> Unit,
    onRemove: (String, (Boolean) -> Unit) -> Unit,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<CustomMetric?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CustomMetric?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("שדות מדידה מותאמים", style = MaterialTheme.typography.titleLarge)
            Text(
                "אפשר להוסיף שדות משלך (למשל היקף ברך או קפל עור). הם יופיעו בטופס המדידה, בגרפים, בהשוואות ובגיבוי.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (metrics.isEmpty()) {
                Text("עוד לא הוגדרו שדות מותאמים.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            metrics.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (m.unit.isNotBlank()) " (${m.unit})" else "", style = MaterialTheme.typography.titleSmall)
                        if (m.isArchived) Text("בארכיון: ההיסטוריה נשמרת, השדה לא מוצג בטופס חדש", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("שנה שם או יחידה") }, onClick = { menu = false; editing = m })
                            if (m.isArchived) {
                                DropdownMenuItem(text = { Text("החזר מהארכיון") }, onClick = { menu = false; onRestore(m.id) })
                            } else {
                                DropdownMenuItem(text = { Text("הסר", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; removing = m })
                            }
                        }
                    }
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            SoftButton("הוסף שדה", onClick = { adding = true }, icon = Icons.Rounded.Add)
        }
    }

    if (adding) {
        MetricDialog("שדה חדש", "", "", onConfirm = { n, u -> onAdd(n, u); adding = false }, onDismiss = { adding = false })
    }
    editing?.let { m ->
        MetricDialog("עריכת שדה", m.name, m.unit, onConfirm = { n, u -> onRename(m.id, n, u); editing = null }, onDismiss = { editing = null })
    }
    removing?.let { m ->
        ConfirmDialog(
            title = "להסיר את \"${m.name}\"?",
            text = "אם כבר הוזנו ערכים בשדה, הוא יועבר לארכיון והערכים יישמרו. אם לא הוזנו ערכים, הוא יימחק.",
            confirmText = "הסר",
            destructive = true,
            onConfirm = {
                onRemove(m.id) { deleted -> note = if (deleted) "השדה נמחק." else "השדה הועבר לארכיון (הערכים נשמרו)." }
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun MetricDialog(title: String, name: String, unit: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var u by remember { mutableStateOf(unit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("שם השדה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(u, { u = it }, label = { Text("יחידה (למשל ס״מ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(n, u) }, enabled = n.isNotBlank()) { Text("שמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
