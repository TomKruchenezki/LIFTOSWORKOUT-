package app.liftos.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftos.data.repo.MeasurementInput
import app.liftos.domain.body.BodyAnalytics
import app.liftos.domain.goals.BodyField
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.CustomMetric
import app.liftos.domain.model.MeasurementCondition
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.DecimalTextField
import app.liftos.ui.components.TimePickerSheet
import app.liftos.ui.util.ltr

/**
 * Adds or edits one measurement. Every number is optional. A value far from the recent readings gets a
 * warning (never a block): the user may be right.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MeasurementSheet(
    existing: BodyMeasurement?,
    all: List<BodyMeasurement>,
    metrics: List<CustomMetric>,
    onSave: (MeasurementInput, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    val initialMs = existing?.measuredAt ?: System.currentTimeMillis()
    val initialZdt = remember { java.time.Instant.ofEpochMilli(initialMs).atZone(existing?.let { TimeUtils.zone(it.zoneId) } ?: zone) }
    var date by remember { mutableStateOf(initialZdt.toLocalDate()) }
    var hour by remember { mutableStateOf(initialZdt.hour) }
    var minute by remember { mutableStateOf(initialZdt.minute) }
    var weight by remember { mutableStateOf(existing?.weightKg) }
    var waist by remember { mutableStateOf(existing?.waistCm) }
    var chest by remember { mutableStateOf(existing?.chestCm) }
    var arm by remember { mutableStateOf(existing?.armCm) }
    var thigh by remember { mutableStateOf(existing?.thighCm) }
    var hips by remember { mutableStateOf(existing?.hipsCm) }
    var neck by remember { mutableStateOf(existing?.neckCm) }
    var shoulders by remember { mutableStateOf(existing?.shouldersCm) }
    var fat by remember { mutableStateOf(existing?.bodyFatPct) }
    var conditions by remember { mutableStateOf(existing?.conditions.orEmpty().toSet()) }
    var custom by remember { mutableStateOf(existing?.customValues.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val input = MeasurementInput(
        weightKg = weight, waistCm = waist, chestCm = chest, armCm = arm, thighCm = thigh,
        hipsCm = hips, neckCm = neck, shouldersCm = shoulders, bodyFatPct = fat,
        conditions = MeasurementCondition.entries.filter { it in conditions },
        customValues = custom, notes = notes.trim(),
    )
    val canSave = input.hasAnyValue || input.notes.isNotEmpty()

    // Compare with the other readings only (not with the one being edited).
    val others = remember(all, existing) { all.filter { it.id != existing?.id } }
    val warnings = listOf(
        BodyField.WEIGHT to weight, BodyField.WAIST to waist, BodyField.CHEST to chest, BodyField.ARM to arm,
        BodyField.THIGH to thigh, BodyField.HIPS to hips, BodyField.NECK to neck, BodyField.SHOULDERS to shoulders,
        BodyField.BODY_FAT to fat,
    ).mapNotNull { (field, v) -> v?.let { value -> BodyAnalytics.outlierWarning(others, field, value, date)?.let { "${field.labelHe}: $it" } } }

    val visibleMetrics = metrics.filter { !it.isArchived || it.id in custom }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (existing == null) "מדידה חדשה" else "עריכת מדידה", style = MaterialTheme.typography.titleLarge)
            Text("אפשר למלא רק את מה שמדדת הפעם.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) { Text("תאריך: ${Formatters.date(date)}") }
                TextButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) { Text("שעה: ${ltr("%02d:%02d".format(hour, minute))}") }
            }
            DecimalTextField("משקל", weight, { weight = it }, Modifier.fillMaxWidth(), "ק״ג")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalTextField("היקף מותניים", waist, { waist = it }, Modifier.weight(1f), "ס״מ")
                DecimalTextField("היקף חזה", chest, { chest = it }, Modifier.weight(1f), "ס״מ")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalTextField("היקף זרוע", arm, { arm = it }, Modifier.weight(1f), "ס״מ")
                DecimalTextField("היקף ירך", thigh, { thigh = it }, Modifier.weight(1f), "ס״מ")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalTextField("היקף אגן", hips, { hips = it }, Modifier.weight(1f), "ס״מ")
                DecimalTextField("היקף צוואר", neck, { neck = it }, Modifier.weight(1f), "ס״מ")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalTextField("היקף כתפיים", shoulders, { shoulders = it }, Modifier.weight(1f), "ס״מ")
                DecimalTextField("אחוז שומן (אם נמדד)", fat, { fat = it }, Modifier.weight(1f), "%")
            }
            if (visibleMetrics.isNotEmpty()) {
                Text("שדות מותאמים", style = MaterialTheme.typography.labelMedium)
                visibleMetrics.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { m ->
                            DecimalTextField(
                                m.name, custom[m.id],
                                { v -> custom = if (v == null) custom - m.id else custom + (m.id to v) },
                                Modifier.weight(1f), m.unit,
                            )
                        }
                        if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text("נסיבות המדידה (עוזר להסביר שינויים חדים)", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasurementCondition.entries.forEach { c ->
                    FilterChip(
                        selected = c in conditions,
                        onClick = { conditions = if (c in conditions) conditions - c else conditions + c },
                        label = { Text(c.labelHe) },
                    )
                }
            }

            if (warnings.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    warnings.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text("זו רק אזהרה: אפשר לשמור בכל זאת אם הערך נכון.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(notes, { notes = it }, label = { Text("הערות (למשל: התחלתי קריאטין)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            BigButton("שמור מדידה", enabled = canSave, onClick = {
                onSave(input, TimeUtils.toEpochMs(date, hour, minute, zone))
            })
        }
    }
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }) { date = it }
    if (pickTime) TimePickerSheet(hour, minute, onDismiss = { pickTime = false }) { h, m -> hour = h; minute = m }
}
