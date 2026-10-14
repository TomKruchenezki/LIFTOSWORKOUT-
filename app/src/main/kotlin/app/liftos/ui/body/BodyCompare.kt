package app.liftos.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liftos.domain.body.BodyAnalytics
import app.liftos.domain.body.ValueChange
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.CustomMetric
import app.liftos.ui.util.ltr

/** Side-by-side comparison of two measurements the user picks (defaults: the oldest and the newest). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementCompareSheet(all: List<BodyMeasurement>, metrics: List<CustomMetric>, onDismiss: () -> Unit) {
    // `all` is newest first.
    var first by remember { mutableStateOf(all.last()) }
    var second by remember { mutableStateOf(all.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("השוואת מדידות", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker("מדידה א׳", first, all, { first = it }, Modifier.weight(1f))
                Picker("מדידה ב׳", second, all, { second = it }, Modifier.weight(1f))
            }
            val rows = remember(first, second, metrics) { compareRows(first, second, metrics) }
            if (rows.isEmpty()) {
                Text("אין שדות משותפים להשוואה בין שתי המדידות.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.weight(1.2f))
                    Text("א׳", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("ב׳", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("שינוי", Modifier.weight(1.4f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(r.label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
                        Text(r.first?.let { ltr(Formatters.number(it, 1)) } ?: "-", Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(r.second?.let { ltr(Formatters.number(it, 1)) } ?: "-", Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(r.changeText, Modifier.weight(1.4f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(BodyAnalytics.CAVEAT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class CompareRow(val label: String, val first: Double?, val second: Double?, val unit: String) {
    val changeText: String
        get() {
            if (first == null || second == null) return "-"
            val c = ValueChange(first, second)
            val pct = c.percent?.let { " (${signedText(it)}%)" }.orEmpty()
            return ltr(signedText(c.delta) + pct)
        }
}

private fun signedText(v: Double): String {
    val n = Formatters.number(v, 1)
    return if (v > 0) "+$n" else n.replace("-", "−")
}

private fun compareRows(a: BodyMeasurement, b: BodyMeasurement, metrics: List<CustomMetric>): List<CompareRow> {
    val builtIn = BodyAnalytics.compare(a, b).map { CompareRow(shortLabel(it.field), it.first, it.second, it.field.unit) }
    val customs = metrics.mapNotNull { m ->
        val x = a.customValues[m.id]
        val y = b.customValues[m.id]
        if (x == null && y == null) null else CompareRow(m.name, x, y, m.unit)
    }
    return builtIn + customs
}

@Composable
private fun Picker(label: String, selected: BodyMeasurement, all: List<BodyMeasurement>, onPick: (BodyMeasurement) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${TimeUtils.parseDate(selected.localDate)?.let { Formatters.date(it) } ?: selected.localDate}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.forEach { m ->
                DropdownMenuItem(
                    text = {
                        val w = m.weightKg?.let { " · ${Formatters.number(it)} ק״ג" }.orEmpty()
                        Text((TimeUtils.parseDate(m.localDate)?.let { Formatters.date(it) } ?: m.localDate) + w)
                    },
                    onClick = { open = false; onPick(m) },
                )
            }
        }
    }
}
