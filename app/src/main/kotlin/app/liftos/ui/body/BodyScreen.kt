package app.liftos.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.liftos.data.repo.MeasurementInput
import app.liftos.di.AppContainer
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.BodyMeasurement
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ChipRow
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.DatedLineChart
import app.liftos.ui.components.DecimalTextField
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.TimePickerSheet
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.kg
import app.liftos.ui.util.ltr
import app.liftos.ui.util.signedKg
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BodyViewModel(private val c: AppContainer) : ViewModel() {
    val measurements: StateFlow<List<BodyMeasurement>?> = c.measurements.observeAll().map<List<BodyMeasurement>, List<BodyMeasurement>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: String?, input: MeasurementInput, measuredAt: Long) {
        viewModelScope.launch { if (id == null) c.measurements.add(input, measuredAt) else c.measurements.update(id, input, measuredAt) }
    }

    fun delete(id: String) = viewModelScope.launch { c.measurements.delete(id) }
}

private enum class Range(val label: String, val days: Long?) {
    D30("30 יום", 30), M3("3 חודשים", 90), M6("6 חודשים", 180), Y1("שנה", 365), ALL("הכול", null)
}

@Composable
fun BodyScreen(container: AppContainer) {
    val vm = appViewModel { BodyViewModel(container) }
    val list by vm.measurements.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BodyMeasurement?>(null) }
    var range by remember { mutableStateOf<Range?>(Range.M3) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar("מדדי גוף") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("מדידה חדשה") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        val items = list
        if (items == null) {
            app.liftos.ui.components.LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        title = "עוד אין מדידות",
                        subtitle = "אפשר להזין רק משקל, או גם היקפים ואחוז שומן. לא חייבים למלא הכול בכל פעם.",
                        icon = Icons.Rounded.MonitorWeight,
                    )
                }
            } else {
                item { WeightSection(items, range, onRange = { range = it }) }
                item { SectionTitle("כל המדידות") }
                items(items, key = { it.id }) { m ->
                    MeasurementCard(m, onEdit = { editing = m }, onDelete = { deleting = m })
                }
            }
        }
    }

    if (creating) {
        MeasurementSheet(null, onSave = { input, at -> vm.save(null, input, at); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { m ->
        MeasurementSheet(m, onSave = { input, at -> vm.save(m.id, input, at); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { m ->
        ConfirmDialog(
            "למחוק את המדידה?", "המדידה מתאריך ${m.localDate} תימחק לצמיתות.", "מחק", destructive = true,
            onConfirm = { vm.delete(m.id); deleting = null }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun WeightSection(all: List<BodyMeasurement>, range: Range?, onRange: (Range?) -> Unit) {
    val today = LocalDate.now()
    val weighed = remember(all) {
        all.mapNotNull { m -> m.weightKg?.let { w -> TimeUtils.parseDate(m.localDate)?.let { BodyTrends.Point(it, w) } } }
    }
    val c7 = remember(all) { BodyTrends.weightChange(all, 7) }
    val c30 = remember(all) { BodyTrends.weightChange(all, 30) }
    LiftCard(Modifier.fillMaxWidth()) {
        Text("משקל גוף", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val latest = all.firstOrNull { it.weightKg != null }
        if (latest == null) {
            Text("עוד לא תועד משקל", style = MaterialTheme.typography.titleMedium)
            return@LiftCard
        }
        Text(kg(latest.weightKg), style = MaterialTheme.typography.headlineMedium)
        val d = TimeUtils.parseDate(latest.localDate)
        Text(d?.let { dateLine(it) } ?: latest.localDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (c7 != null || c30 != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                c7?.let { Text("7 ימים: ${signedKg(it.deltaKg)}") }
                c30?.let { Text("30 ימים: ${signedKg(it.deltaKg)}") }
            }
        }
        Spacer(Modifier.height(8.dp))
        ChipRow(Range.entries, range, { it.label }, onRange, allowClear = false)
        val cutoff = range?.days?.let { today.minusDays(it) }
        val shown = if (cutoff == null) weighed else weighed.filter { !it.date.isBefore(cutoff) }
        if (shown.size >= 2) {
            Spacer(Modifier.height(8.dp))
            // The 7-day moving average is computed on all data so the line is right at the left edge of the window.
            val trend = BodyTrends.movingAverage(weighed, 7).filter { t -> shown.any { it.date == t.date } }
            DatedLineChart(points = shown, trend = trend, unit = "ק״ג")
            Text(
                "הנקודות הן שקילות בודדות והקו הירוק הוא ממוצע נע של 7 ימים. שינויים קצרי טווח (נוזלים, ארוחות, קריאטין) לא בהכרח מעידים על שומן או שריר.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("נדרשות לפחות שתי שקילות בטווח הזה כדי להציג גרף.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MeasurementCard(m: BodyMeasurement, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val d = TimeUtils.parseDate(m.localDate)
    LiftCard(Modifier.fillMaxWidth(), onClick = onEdit, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d?.let { dateLine(it) } ?: m.localDate, style = MaterialTheme.typography.titleSmall)
                val parts = buildList {
                    m.weightKg?.let { add("משקל ${ltr(Formatters.number(it))}") }
                    m.waistCm?.let { add("מותניים ${ltr(Formatters.number(it))}") }
                    m.chestCm?.let { add("חזה ${ltr(Formatters.number(it))}") }
                    m.armCm?.let { add("זרוע ${ltr(Formatters.number(it))}") }
                    m.thighCm?.let { add("ירך ${ltr(Formatters.number(it))}") }
                    m.hipsCm?.let { add("אגן ${ltr(Formatters.number(it))}") }
                    m.neckCm?.let { add("צוואר ${ltr(Formatters.number(it))}") }
                    m.bodyFatPct?.let { add("שומן ${ltr(Formatters.number(it))}%") }
                }
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (m.notes.isNotBlank()) Text(m.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "אפשרויות") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("ערוך") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("מחק", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementSheet(existing: BodyMeasurement?, onSave: (MeasurementInput, Long) -> Unit, onDismiss: () -> Unit) {
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
    var fat by remember { mutableStateOf(existing?.bodyFatPct) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val input = MeasurementInput(
        weightKg = weight, waistCm = waist, chestCm = chest, armCm = arm, thighCm = thigh,
        hipsCm = hips, neckCm = neck, bodyFatPct = fat, notes = notes.trim(),
    )
    val canSave = input.hasAnyValue || input.notes.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
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
            DecimalTextField("אחוז שומן (אם נמדד)", fat, { fat = it }, Modifier.fillMaxWidth(), "%")
            OutlinedTextField(notes, { notes = it }, label = { Text("הערות (למשל: התחלתי קריאטין)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            BigButton("שמור מדידה", enabled = canSave, onClick = {
                onSave(input, TimeUtils.toEpochMs(date, hour, minute, zone))
            })
        }
    }
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }) { date = it }
    if (pickTime) TimePickerSheet(hour, minute, onDismiss = { pickTime = false }) { h, m -> hour = h; minute = m }
}
