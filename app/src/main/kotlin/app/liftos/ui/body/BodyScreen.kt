package app.liftos.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.ChartPoint
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.Period
import app.liftos.domain.analytics.PeriodPreset
import app.liftos.domain.body.BodyAnalytics
import app.liftos.domain.body.ValueChange
import app.liftos.domain.goals.BodyField
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.CustomMetric
import app.liftos.ui.components.ChipRow
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.InteractiveChart
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.PeriodSelector
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.ltr
import java.time.LocalDate

/** One thing that can be charted: a built-in body field or one of the user's own metrics. */
private data class SeriesDef(val key: String, val label: String, val unit: String, val field: BodyField?, val metricId: String?)

@Composable
fun BodyScreen(container: AppContainer, onOpenPhotos: () -> Unit = {}) {
    val vm = appViewModel { BodyViewModel(container) }
    val list by vm.measurements.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val photoCount by vm.photoCount.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BodyMeasurement?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var managingMetrics by remember { mutableStateOf(false) }
    var seriesKey by rememberSaveable { mutableStateOf(BodyField.WEIGHT.key) }
    var period by remember { mutableStateOf(Period(PeriodPreset.DAYS_90)) }
    val today = remember { LocalDate.now() }

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
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
                item {
                    SeriesSection(
                        all = items, metrics = metrics, selectedKey = seriesKey, onSelect = { seriesKey = it },
                        period = period, onPeriod = { period = it }, today = today,
                    )
                }
                item { PeriodComparison(items, seriesKey, today) }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (items.size >= 2) SoftButton("השוואת מדידות", onClick = { comparing = true })
                    SoftButton("שדות מותאמים", onClick = { managingMetrics = true })
                    SoftButton(if (photoCount > 0) "תמונות התקדמות ($photoCount)" else "תמונות התקדמות", onClick = onOpenPhotos)
                }
            }
            if (items.isNotEmpty()) {
                item { SectionTitle("כל המדידות") }
                val names = metrics.associate { it.id to it }
                items(items, key = { it.id }) { m ->
                    MeasurementCard(m, names, onEdit = { editing = m }, onDelete = { deleting = m })
                }
            }
        }
    }

    val all = list.orEmpty()
    if (creating) {
        MeasurementSheet(null, all, metrics, onSave = { input, at -> vm.save(null, input, at); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { m ->
        MeasurementSheet(m, all, metrics, onSave = { input, at -> vm.save(m.id, input, at); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { m ->
        ConfirmDialog(
            "למחוק את המדידה?", "המדידה מתאריך ${m.localDate} תימחק לצמיתות.", "מחק", destructive = true,
            onConfirm = { vm.delete(m.id); deleting = null }, onDismiss = { deleting = null },
        )
    }
    if (comparing) MeasurementCompareSheet(all, metrics, onDismiss = { comparing = false })
    if (managingMetrics) {
        MetricsSheet(
            metrics = metrics,
            onAdd = vm::addMetric,
            onRename = vm::renameMetric,
            onRemove = vm::removeMetric,
            onRestore = vm::restoreMetric,
            onDismiss = { managingMetrics = false },
        )
    }
}

// ---- chart section ---------------------------------------------------------------------------------------------

@Composable
private fun SeriesSection(
    all: List<BodyMeasurement>,
    metrics: List<CustomMetric>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    period: Period,
    onPeriod: (Period) -> Unit,
    today: LocalDate,
) {
    val defs = remember(all, metrics) {
        buildList {
            BodyField.entries.forEach { f ->
                if (f == BodyField.WEIGHT || all.any { f.valueOf(it) != null }) add(SeriesDef(f.key, f.labelHe, f.unit, f, null))
            }
            metrics.filter { !it.isArchived || all.any { m -> it.id in m.customValues } }.forEach { cm ->
                if (all.any { cm.id in it.customValues }) add(SeriesDef("custom:${cm.id}", cm.name, cm.unit, null, cm.id))
            }
        }
    }
    val def = defs.firstOrNull { it.key == selectedKey } ?: defs.first()
    val points = remember(all, def) {
        if (def.field != null) BodyAnalytics.series(all, def.field) else BodyAnalytics.customSeries(all, def.metricId!!)
    }
    val range = remember(period, points, today) { period.resolve(today, points.firstOrNull()?.date) }
    val shown = remember(points, range) { points.filter { it.date in range } }
    val unit = def.unit

    LiftCard(Modifier.fillMaxWidth()) {
        Text("מגמות", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        ChipRow(defs, def, { it.label }, { it?.let { d -> onSelect(d.key) } }, allowClear = false, modifier = Modifier.padding(horizontal = 0.dp))
        Spacer(Modifier.height(8.dp))

        val latest = points.lastOrNull()
        if (latest == null) {
            Text("עוד לא הוזן ערך עבור ${def.label}", style = MaterialTheme.typography.titleMedium)
            return@LiftCard
        }
        Text("${ltr(Formatters.number(latest.value, 1))} $unit", style = MaterialTheme.typography.headlineMedium)
        Text(dateLine(latest.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(8.dp))
        PeriodSelector(period, today, onPeriod)
        Spacer(Modifier.height(8.dp))

        val chart = remember(shown, unit) {
            shown.map { p ->
                val cond = if (p.conditions.isEmpty()) "" else " · " + p.conditions.joinToString(", ") { it.labelHe }
                ChartPoint(p.date, p.value, "${Formatters.number(p.value, 1)} $unit", Formatters.date(p.date) + cond)
            }
        }
        val trend = remember(points, range, unit) {
            BodyAnalytics.movingAverage(points, 7).filter { it.date in range }
                .map { ChartPoint(it.date, it.value, "ממוצע נע (7 ימים): ${Formatters.number(it.value, 1)} $unit") }
        }
        InteractiveChart(
            points = chart,
            trend = trend,
            emptyMessage = "נדרשות לפחות שתי מדידות בטווח הזה כדי להציג גרף.",
            description = "גרף ${def.label}",
        )
        if (shown.size >= 2) {
            Spacer(Modifier.height(8.dp))
            val change = ValueChange(shown.first().value, shown.last().value)
            val pct = change.percent?.let { " (${signed(it, 1)}%)" }.orEmpty()
            Text(
                "בטווח שנבחר: ${signed(change.delta, 1)} $unit$pct · מינימום ${ltr(Formatters.number(shown.minOf { it.value }, 1))} · מקסימום ${ltr(Formatters.number(shown.maxOf { it.value }, 1))} · ממוצע ${ltr(Formatters.number(shown.map { it.value }.average(), 1))}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (shown.any { it.conditions.isNotEmpty() }) {
                Text(
                    "חלק מהנקודות מסומנות בנסיבות המדידה (למשל אחרי ארוחה): לחיצה על נקודה מציגה אותן.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (def.field != null) {
            Spacer(Modifier.height(8.dp))
            val t = remember(all, def.field, today) { BodyAnalytics.trend(all, def.field, today) }
            Text("מגמה ב-28 הימים האחרונים", style = MaterialTheme.typography.labelMedium)
            Text(t.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                BodyAnalytics.CAVEAT,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun signed(v: Double, decimals: Int): String {
    val n = Formatters.number(v, decimals)
    return ltr(if (v > 0) "+$n" else n.replace("-", "−"))
}

/** Average of the last 30 days against the 30 days before them, for the selected built-in field. */
@Composable
private fun PeriodComparison(all: List<BodyMeasurement>, seriesKey: String, today: LocalDate) {
    val field = BodyField.fromKey(seriesKey) ?: return
    val current = DateRange.lastDays(30, today)
    val result = remember(all, field, today) { BodyAnalytics.comparePeriods(all, field, current.previous(), current) }
    LiftCard(Modifier.fillMaxWidth()) {
        Text("30 הימים האחרונים מול 30 הימים שלפניהם", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(field.labelHe, style = MaterialTheme.typography.titleMedium)
        val change = result.change
        if (change == null || result.a.count < 2 || result.b.count < 2) {
            Text(
                "אין מספיק מדידות בשתי התקופות (נדרשות לפחות שתיים בכל אחת): ${result.a.count} לפני, ${result.b.count} אחרי.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val pct = change.percent?.let { " (${signed(it, 1)}%)" }.orEmpty()
            Text(
                "ממוצע לפני ${ltr(Formatters.number(change.from, 1))} (${result.a.count} מדידות), ממוצע אחרי ${ltr(Formatters.number(change.to, 1))} (${result.b.count} מדידות): ${signed(change.delta, 1)} ${field.unit}$pct",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ---- the list ----------------------------------------------------------------------------------------------------

@Composable
private fun MeasurementCard(m: BodyMeasurement, names: Map<String, CustomMetric>, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val d = TimeUtils.parseDate(m.localDate)
    LiftCard(Modifier.fillMaxWidth(), onClick = onEdit, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d?.let { dateLine(it) } ?: m.localDate, style = MaterialTheme.typography.titleSmall)
                val parts = buildList {
                    BodyField.entries.forEach { f -> f.valueOf(m)?.let { add("${shortLabel(f)} ${ltr(Formatters.number(it))}${if (f == BodyField.BODY_FAT) "%" else ""}") } }
                    m.customValues.forEach { (id, v) -> add("${names[id]?.name ?: "שדה"} ${ltr(Formatters.number(v))}") }
                }
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (m.conditions.isNotEmpty()) {
                    Text(m.conditions.joinToString(" · ") { it.labelHe }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
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

internal fun shortLabel(f: BodyField): String = when (f) {
    BodyField.WEIGHT -> "משקל"
    BodyField.WAIST -> "מותניים"
    BodyField.CHEST -> "חזה"
    BodyField.ARM -> "זרוע"
    BodyField.THIGH -> "ירך"
    BodyField.HIPS -> "אגן"
    BodyField.NECK -> "צוואר"
    BodyField.SHOULDERS -> "כתפיים"
    BodyField.BODY_FAT -> "שומן"
}
