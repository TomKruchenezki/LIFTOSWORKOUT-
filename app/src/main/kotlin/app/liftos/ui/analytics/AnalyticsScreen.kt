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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.liftos.domain.analytics.ChartSeries
import app.liftos.domain.analytics.DashboardCards
import app.liftos.domain.analytics.MetricChange
import app.liftos.domain.analytics.MuscleModel
import app.liftos.domain.insights.Insight
import app.liftos.domain.insights.InsightTone
import app.liftos.domain.logic.Formatters
import app.liftos.ui.components.ChartKind
import app.liftos.ui.components.DistributionBars
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.InteractiveChart
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.PeriodSelector
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.StatTile
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.ltr

class AnalyticsNav(
    val openExercise: (String) -> Unit,
    val openSession: (String) -> Unit,
    val openRecords: () -> Unit,
    val openGoals: () -> Unit,
    val openRecovery: () -> Unit,
    val openSettings: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(container: AppContainer, nav: AnalyticsNav) {
    val vm = appViewModel { AnalyticsViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar("התקדמות") },
    ) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        ScrollPage(Modifier.padding(padding)) {
            if (!ui.hasData) {
                EmptyState(
                    title = "עוד אין נתוני אימונים",
                    subtitle = "אחרי שתסיים את האימון הראשון יופיעו כאן כרטיסי התקדמות, גרפים, שיאים אישיים והצעות.",
                    icon = Icons.Rounded.Insights,
                )
                ShortcutRow(nav, ui.activeGoals)
                return@ScrollPage
            }

            ui.cards?.let { Dashboard(it, nav) }
            ShortcutRow(nav, ui.activeGoals)

            SectionTitle("תקופה")
            PeriodSelector(ui.controls.period, ui.today, onChange = vm::setPeriod)
            ComparisonCard(ui)

            SectionTitle("גרפים")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = ui.controls.chart == choice,
                        onClick = { vm.setChart(choice) },
                        label = { Text(choice.labelHe) },
                    )
                }
            }
            LiftCard(Modifier.fillMaxWidth()) {
                val choice = ui.controls.chart
                Text(choice.labelHe, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InteractiveChart(
                    points = ui.chartPoints,
                    kind = if (choice.barLike) ChartKind.BAR else ChartKind.LINE,
                    minPoints = if (choice.barLike) 1 else 2,
                    onOpenSession = nav.openSession,
                    description = choice.labelHe,
                    emptyMessage = "אין מספיק נתונים בתקופה שנבחרה. נסה תקופה ארוכה יותר.",
                )
                Text(
                    when (choice) {
                        ChartChoice.WEEKLY_VOLUME, ChartChoice.MONTHLY_VOLUME, ChartChoice.WORKOUT_VOLUME ->
                            "נפח = משקל × חזרות, לסטי עבודה שהושלמו בלבד (ללא חימום)."
                        ChartChoice.WEEKLY_SETS -> "סטי עבודה שהושלמו. זוג ימין+שמאל בתרגיל חד-צדדי נחשב סט אחד."
                        ChartChoice.WEEKLY_WORKOUTS -> "מספר האימונים שתועדו בכל שבוע (שבוע מתחיל ביום ראשון)."
                        ChartChoice.WORKOUT_DURATION -> "משך = מהתחלת האימון ועד סיומו כפי שנרשם."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("סטים לפי קבוצת שרירים")
            LiftCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("כולל סטים עקיפים (הערכה)", Modifier.weight(1f))
                    Switch(checked = ui.controls.includeIndirect, onCheckedChange = vm::setIncludeIndirect)
                }
                Spacer(Modifier.height(8.dp))
                DistributionBars(
                    slices = ChartSeries.muscleDistribution(ui.muscles, ui.controls.includeIndirect),
                    valueSuffix = "סטים",
                )
                val weeks = ui.range.weeks().coerceAtLeast(1.0)
                val top = ui.muscles.filter { it.directSets > 0 }.take(6)
                if (top.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    top.forEach {
                        Text(
                            "${it.muscle.labelHe}: ${it.directSets} סטים ישירים (כ-${ltr(Formatters.number(it.directSets / weeks, 1))} בשבוע) · ${it.workouts} אימונים",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(MuscleModel.ASSUMPTIONS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (ui.insights.isNotEmpty()) {
                SectionTitle("תובנות מהנתונים שלך")
                LiftCard(Modifier.fillMaxWidth()) {
                    ui.insights.forEach { InsightRow(it) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "התובנות מחושבות מקומית מהנתונים שהזנת, לפי כללים פשוטים – אין כאן בינה מלאכותית.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("התקדמות לפי תרגיל")
            OutlinedTextField(
                value = ui.controls.exerciseQuery,
                onValueChange = vm::setExerciseQuery,
                label = { Text("חיפוש תרגיל") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            var showAll by remember { mutableStateOf(false) }
            val rows = if (showAll || ui.controls.exerciseQuery.isNotBlank()) ui.exercises else ui.exercises.take(8)
            if (rows.isEmpty()) {
                Text("לא נמצאו תרגילים עם נתונים.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach { row ->
                LiftCard(Modifier.fillMaxWidth(), onClick = { nav.openExercise(row.key) }, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${row.primaryMuscle.labelHe} · ${row.sessions} אימונים" + (row.lastTop?.let { " · אחרון: ${ltr(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
                    }
                }
            }
            if (!showAll && ui.controls.exerciseQuery.isBlank() && ui.exercises.size > 8) {
                TextButton(onClick = { showAll = true }) { Text("הצג את כל ${ui.exercises.size} התרגילים") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShortcutRow(nav: AnalyticsNav, activeGoals: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = nav.openRecords,
            label = { Text("שיאים אישיים") },
            leadingIcon = { Icon(Icons.Rounded.EmojiEvents, contentDescription = null) },
        )
        AssistChip(
            onClick = nav.openGoals,
            label = { Text(if (activeGoals > 0) "יעדים ($activeGoals)" else "יעדים") },
            leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
        )
        AssistChip(
            onClick = nav.openRecovery,
            label = { Text("התאוששות") },
            leadingIcon = { Icon(Icons.Rounded.MonitorHeart, contentDescription = null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(cards: DashboardCards, nav: AnalyticsNav) {
    Text("השבוע (מיום ראשון)", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(cards.week.workouts.toString(), "אימונים השבוע", Modifier.weight(1f), accent = true)
        StatTile(cards.week.workingSets.toString(), "סטי עבודה", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VolumeTile(cards, Modifier.weight(1f))
        LiftCard(Modifier.weight(1f).fillMaxWidth(), onClick = nav.openRecords, contentPadding = 14.dp) {
            Text(cards.newPrsThisWeek.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("שיאים חדשים השבוע", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    LiftCard(Modifier.fillMaxWidth()) {
        Text("קבוצות שרירים שאומנו השבוע", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cards.musclesTrainedThisWeek.isEmpty()) {
            Text("עוד לא אומנו שרירים השבוע.", style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.musclesTrainedThisWeek.forEach { AssistChip(onClick = {}, label = { Text(it.labelHe) }) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val a = cards.adherence
        StatTile(
            value = if (a != null) "${a.percent}%" else "–",
            label = if (a != null) "עמידה בתוכנית (${a.completed}/${a.expected} ב-${a.windowDays} ימים)" else "עמידה בתוכנית – אין יעד שבועי",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = cards.avgDurationMs?.let { "${it / 60_000} דק׳" } ?: "–",
            label = "זמן אימון ממוצע (30 ימים)",
            modifier = Modifier.weight(1f),
        )
    }
    LiftCard(Modifier.fillMaxWidth()) {
        Text("מגמת כוח (${cards.strength.windowDays} ימים מול התקופה שלפניהם)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val t = cards.strength
        if (!t.hasEnoughData) {
            Text("נדרשים אימונים נוספים כדי לזהות מגמה.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "מגמה נמדדת רק בתרגילים שבוצעו בשתי התקופות.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "${t.improved} בעלייה · ${t.same} ללא שינוי · ${t.declined} בירידה",
                style = MaterialTheme.typography.titleMedium,
            )
            t.medianPercent?.let {
                Text(
                    "שינוי חציוני בביצועים הטובים ביותר: ${ltr(signedPct(it))} (מתוך ${t.tracked} תרגילים)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "לפי הביצוע הטוב ביותר בכל תרגיל (1RM משוער או משקל). הפרש מתחת ל-2% נחשב ללא שינוי.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VolumeTile(cards: DashboardCards, modifier: Modifier) {
    LiftCard(modifier = modifier, contentPadding = 14.dp) {
        Text(ltr(Formatters.thousands(cards.week.volumeKg)), style = MaterialTheme.typography.headlineMedium)
        Text("נפח השבוע (ק״ג-חזרות)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        deltaLine(cards.weekComparison.volumeKg, "לעומת שבוע שעבר")?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ComparisonCard(ui: AnalyticsUi) {
    val c = ui.comparison ?: return
    LiftCard(Modifier.fillMaxWidth()) {
        Text(
            "${Formatters.date(ui.range.start)} – ${Formatters.date(ui.range.end)} (${ui.range.days} ימים)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        CompareRow("אימונים", c.workouts, 0)
        CompareRow("סטי עבודה", c.workingSets, 0)
        CompareRow("נפח (ק״ג-חזרות)", c.volumeKg, 0)
        c.avgDurationMin?.let { CompareRow("משך אימון ממוצע (דק׳)", it, 0) }
        Text(
            if (c.previous.workouts == 0) "אין נתונים בתקופה הקודמת באותו אורך, ולכן אין השוואה."
            else "ההשוואה היא מול ${c.previous.range.days} הימים שקדמו לתקופה (${Formatters.date(c.previous.range.start)} – ${Formatters.date(c.previous.range.end)}).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompareRow(label: String, change: MetricChange, decimals: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            ltr(if (decimals == 0 && change.current >= 1000) Formatters.thousands(change.current) else Formatters.number(change.current, decimals)),
            style = MaterialTheme.typography.titleSmall,
        )
        val pct = change.percent
        Text(
            if (pct != null) ltr("  ${signedPct(pct)}") else if (change.previous == 0.0 && change.current > 0) "  חדש" else "",
            style = MaterialTheme.typography.labelMedium,
            color = if ((pct ?: 0.0) >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightRow(i: Insight) {
    val color = when (i.tone) {
        InsightTone.POSITIVE -> MaterialTheme.colorScheme.primary
        InsightTone.ATTENTION -> MaterialTheme.colorScheme.tertiary
        InsightTone.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = color, style = MaterialTheme.typography.titleMedium)
        Text(i.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun signedPct(v: Double): String {
    val n = Formatters.number(v, 1)
    return (if (v > 0) "+$n" else n) + "%"
}

private fun deltaLine(change: MetricChange, suffix: String): String? {
    val pct = change.percent ?: return null
    return "${ltr(signedPct(pct))} $suffix"
}
