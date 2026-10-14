package app.liftos.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.ChangeVerdict
import app.liftos.domain.analytics.ExerciseOverview
import app.liftos.domain.analytics.PerformanceMark
import app.liftos.domain.analytics.TrendMetric
import app.liftos.domain.analytics.TrendResult
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.OneRepMax
import app.liftos.domain.model.TrackingType
import app.liftos.domain.progression.ProgressionEngine
import app.liftos.domain.progression.ProgressionSuggestion
import app.liftos.domain.progression.SuggestionKind
import app.liftos.ui.components.ChartKind
import app.liftos.ui.components.InteractiveChart
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.PeriodSelector
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.RecordText
import app.liftos.ui.util.ltr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseAnalyticsScreen(
    container: AppContainer,
    exerciseKey: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenLibraryEntry: (String) -> Unit,
) {
    val vm = appViewModel(key = "exAnalytics-$exerciseKey") { ExerciseAnalyticsViewModel(container, exerciseKey) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    val overview = ui.overview
    LaunchedEffect(ui.missing) { if (ui.missing) onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar(overview?.name ?: "תרגיל", onBack = onBack) },
    ) { padding ->
        if (ui.loading || overview == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        ScrollPage(Modifier.padding(padding)) {
            Header(overview, showLibraryLink = !exerciseKey.startsWith("name:"), onOpenLibrary = { onOpenLibraryEntry(exerciseKey) })

            val suggestion = ui.suggestion
            if (suggestion != null && suggestion.kind != SuggestionKind.INSUFFICIENT_DATA) {
                SuggestionCard(suggestion, ui.visibility, onIgnore = { vm.ignore(suggestion) })
            }

            SectionTitle("הביצועים הטובים ביותר")
            BestTiles(overview)

            SectionTitle("מגמה")
            TrendCard(overview)

            SectionTitle("גרף")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseChart.entries.forEach { ch ->
                    if (ch == ExerciseChart.REPS_AT_WEIGHT && ui.repeatedWeights.isEmpty()) return@forEach
                    FilterChip(selected = ui.controls.chart == ch, onClick = { vm.setChart(ch) }, label = { Text(ch.labelHe) })
                }
            }
            if (ui.controls.chart == ExerciseChart.REPS_AT_WEIGHT) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.repeatedWeights.forEach { w ->
                        FilterChip(
                            selected = ui.controls.weightKg == w.weightKg,
                            onClick = { vm.setWeight(w.weightKg) },
                            label = { Text("${Formatters.number(w.weightKg, 2)} ק״ג (${w.sessions} אימונים)") },
                        )
                    }
                }
            }
            PeriodSelector(ui.controls.period, ui.today, onChange = vm::setPeriod)
            LiftCard(Modifier.fillMaxWidth()) {
                Text(ui.chartTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InteractiveChart(
                    points = ui.chartPoints,
                    kind = if (ui.controls.chart == ExerciseChart.VOLUME) ChartKind.BAR else ChartKind.LINE,
                    minPoints = if (ui.controls.chart == ExerciseChart.VOLUME) 1 else 2,
                    onOpenSession = onOpenSession,
                    description = ui.chartTitle,
                    emptyMessage = "אין מספיק אימונים בתקופה שנבחרה כדי להציג גרף.",
                )
                if (overview.metric == TrendMetric.E1RM && ui.controls.chart == ExerciseChart.PRIMARY) {
                    Text(OneRepMax.EXPLANATION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (ui.records.isNotEmpty()) {
                SectionTitle("שיאים אישיים")
                LiftCard(Modifier.fillMaxWidth()) {
                    ui.records.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(r.kind.labelHe + if (r.kind.isEstimate) " (הערכה)" else "", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${ltr(RecordText.describe(r))} · ${Formatters.date(r.date)} · ${r.sessionTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onOpenSession(r.sessionId) }) { Text("פתח") }
                        }
                    }
                }
            }

            if (overview.weightTable.isNotEmpty() && overview.trackingType != TrackingType.DURATION) {
                SectionTitle("חזרות בכל משקל")
                LiftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("משקל", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("סטים", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("מקסימום חזרות", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("אחרון", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    overview.weightTable.take(12).forEach { w ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(if (w.weightKg == 0.0) "משקל גוף" else ltr(Formatters.number(w.weightKg, 2)), Modifier.weight(1f))
                            Text(w.sets.toString(), Modifier.weight(0.7f))
                            Text(w.bestReps.toString(), Modifier.weight(1.2f))
                            Text(ltr(Formatters.date(w.lastDate)), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            SectionTitle("מדרגת משקל להצעות")
            LiftCard(Modifier.fillMaxWidth()) {
                Text(
                    "הקפיצה הקטנה ביותר שתוצע בהעלאת משקל. ברירת המחדל נקבעת לפי הציוד (למשל 2.5 ק״ג למוט).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ui.incrementKg == null, onClick = { vm.setIncrement(null) }, label = { Text("אוטומטי") })
                    listOf(1.0, 1.25, 2.0, 2.5, 5.0).forEach { v ->
                        FilterChip(
                            selected = ui.incrementKg == v,
                            onClick = { vm.setIncrement(v) },
                            label = { Text(ltr(Formatters.number(v, 2)) + " ק״ג") },
                        )
                    }
                }
            }

            SectionTitle("היסטוריית הסטים")
            var showAll by remember { mutableStateOf(false) }
            val sessions = overview.sessions.asReversed()
            (if (showAll) sessions else sessions.take(8)).forEach { s ->
                LiftCard(Modifier.fillMaxWidth(), onClick = { onOpenSession(s.sessionId) }, contentPadding = 12.dp) {
                    Text("${Formatters.date(s.date)} · ${s.sessionTitle}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        s.sets.joinToString(" · ") { set ->
                            when {
                                overview.trackingType == TrackingType.DURATION -> "${set.durationSec ?: 0} שנ׳"
                                (set.weightKg ?: 0.0) > 0.0 -> "${Formatters.number(set.weightKg)}×${set.reps ?: 0}"
                                else -> "גוף×${set.reps ?: 0}"
                            }
                        }.let { ltr(it) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${s.workingSets} סטים · נפח ${ltr(Formatters.thousands(s.volumeKg))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!showAll && sessions.size > 8) {
                TextButton(onClick = { showAll = true }) { Text("הצג את כל ${sessions.size} האימונים") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Header(o: ExerciseOverview, showLibraryLink: Boolean, onOpenLibrary: () -> Unit) {
    LiftCard(Modifier.fillMaxWidth()) {
        Text(o.name, style = MaterialTheme.typography.headlineMedium)
        if (o.nameEn.isNotBlank()) Text(o.nameEn, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${o.primaryMuscle.labelHe} · ${o.sessionCount} אימונים · ${o.totalWorkingSets} סטי עבודה · נפח ${ltr(Formatters.thousands(o.totalVolumeKg))}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val last = o.lastVsPrevious
        if (last != null) {
            Spacer(Modifier.height(6.dp))
            val (label, color) = when (last.verdict) {
                ChangeVerdict.UP -> "שיפור לעומת האימון הקודם" to MaterialTheme.colorScheme.primary
                ChangeVerdict.DOWN -> "ירידה לעומת האימון הקודם" to MaterialTheme.colorScheme.tertiary
                ChangeVerdict.SAME -> "ללא שינוי לעומת האימון הקודם" to MaterialTheme.colorScheme.onSurfaceVariant
                ChangeVerdict.NEW -> "אימון ראשון" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(label, style = MaterialTheme.typography.titleSmall, color = color)
            val parts = buildList {
                last.topWeightDeltaKg?.let { add("משקל מוביל ${ltr(signed(it))} ק״ג") }
                last.e1rmDeltaKg?.let { add("1RM משוער ${ltr(signed(it))} ק״ג") }
                add("חזרות ${ltr(signed(last.repsDelta.toDouble(), 0))}")
                add("נפח ${ltr(signed(last.volumeDeltaKg, 0))}")
            }
            Text(
                parts.joinToString(" · ") + " (מול ${Formatters.date(last.previousDate)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showLibraryLink) TextButton(onClick = onOpenLibrary) { Text("הוראות ביצוע וחיפוש הדגמה") }
    }
}

private fun signed(v: Double, decimals: Int = 1): String {
    val n = Formatters.number(v, decimals)
    return if (v > 0) "+$n" else n
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BestTiles(o: ExerciseOverview) {
    val items = buildList {
        o.maxWeight?.let { add("המשקל הגבוה ביותר" to markText(it, "ק״ג")) }
        o.bestE1rm?.let { add("1RM משוער (הערכה)" to "${ltr(Formatters.number(it.value, 1))} ק״ג") }
        add(
            "1RM בפועל (חזרה בודדת)" to (o.actualOneRm?.let { "${ltr(Formatters.number(it.value))} ק״ג" } ?: "לא תועדה חזרה בודדת"),
        )
        listOf(5, 8, 10).forEach { n ->
            o.repMax[n]?.let { add("משקל מקסימלי ל-$n חזרות" to "${ltr(Formatters.number(it.value))} ק״ג × ${it.reps}") }
        }
        o.maxRepsUnloaded?.let { add("שיא חזרות בלי משקל" to "${it.value.toInt()} חזרות") }
        o.maxDuration?.let { add("זמן מקסימלי" to "${it.value.toInt()} שניות") }
    }
    if (items.isEmpty()) {
        Text("אין עדיין סטי עבודה עם נתונים לתרגיל הזה.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LiftCard(Modifier.fillMaxWidth()) {
        items.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        }
        if (o.bestE1rm != null) {
            Text(OneRepMax.EXPLANATION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun markText(m: PerformanceMark, unit: String): String =
    "${ltr(Formatters.number(m.value))} $unit" + (m.reps?.let { " × $it" } ?: "") + " · ${Formatters.date(m.date)}"

@Composable
private fun TrendCard(o: ExerciseOverview) {
    LiftCard(Modifier.fillMaxWidth()) {
        Text("שינוי ב${o.metric.labelHe} (${o.metric.unit})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val labels = listOf("חודש אחרון", "3 חודשים", "שנה")
        o.trends.forEachIndexed { i, t ->
            TrendRow(labels[i], t)
        }
        Text(
            "השוואה בין האימון הראשון לאחרון בכל תקופה; נדרשים לפחות שני אימונים בתקופה.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendRow(label: String, t: TrendResult?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (t == null) {
            Text("נדרשים אימונים נוספים", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val pct = t.percent?.let { " (${signedPct(it)})" }.orEmpty()
            Text(
                ltr("${Formatters.number(t.fromValue, 1)} ← ${Formatters.number(t.toValue, 1)}  ${signed(t.delta)}$pct"),
                style = MaterialTheme.typography.titleSmall,
                color = if (t.delta > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SuggestionCard(s: ProgressionSuggestion, visibility: ProgressionEngine.Visibility, onIgnore: () -> Unit) {
    LiftCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("הצעה לאימון הבא", style = MaterialTheme.typography.titleMedium)
            AssistChip(onClick = {}, label = { Text(s.confidence.labelHe) })
        }
        when (visibility) {
            ProgressionEngine.Visibility.IGNORED -> Text(
                "התעלמת מההצעה הזו. היא תחושב מחדש אחרי האימון הבא בתרגיל.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProgressionEngine.Visibility.APPLIED -> Text(
                "ההצעה הוחלה באימון. היא תחושב מחדש אחרי האימון הבא בתרגיל.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProgressionEngine.Visibility.SHOW -> {
                Text(s.headline, style = MaterialTheme.typography.titleSmall)
                s.suggestedWeightKg?.let { w ->
                    if (s.changesWeight) {
                        Text(
                            "משקל מוצע: ${ltr(Formatters.number(w, 2))} ק״ג" + (s.targetRepsMin?.let { " · יעד ${it}–${s.targetRepsMax} חזרות" } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (s.targetRepsMin != null) {
                        Text(
                            "בלי שינוי במשקל (${ltr(Formatters.number(w, 2))} ק״ג) · יעד ${s.targetRepsMin}–${s.targetRepsMax} חזרות",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                Text(s.explanation, style = MaterialTheme.typography.bodyMedium)
                if (s.evidence.isNotEmpty()) {
                    Text("הנתונים שעליהם ההצעה מבוססת:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.evidence.forEach { Text(ltr(it), style = MaterialTheme.typography.bodySmall) }
                }
                s.cautions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                Text(
                    "זו הצעה בלבד, מחושבת מהנתונים שלך בכללים פשוטים – שום דבר לא משתנה אוטומטית. במסך האימון אפשר להחיל אותה בלחיצה.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SoftButton("התעלם מההצעה", onClick = onIgnore, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
