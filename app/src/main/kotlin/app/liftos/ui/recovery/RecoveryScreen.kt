package app.liftos.ui.recovery

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.ChartPoint
import app.liftos.domain.logic.Formatters
import app.liftos.domain.recovery.RecoveryAnalytics
import app.liftos.ui.components.ChartKind
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.InteractiveChart
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.util.ltr
import java.time.LocalDate

/**
 * A short recovery overview: how often you train, how the load moves and what you reported about
 * energy and fatigue. It shows signals to look at - never a diagnosis.
 */
@Composable
fun RecoveryScreen(container: AppContainer, onBack: () -> Unit, onOpenSession: (String) -> Unit) {
    val data by container.analyticsData.collectAsStateWithLifecycle(initialValue = null)
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar("התאוששות ועומס", onBack = onBack) },
    ) { padding ->
        val d = data
        if (d == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val today = remember { LocalDate.now(container.zones.zone()) }
        val report = remember(d) { RecoveryAnalytics.report(d.facts, d.sessions, today) }
        ScrollPage(Modifier.padding(padding)) {
            if (d.isEmpty) {
                EmptyState(
                    title = "עוד אין נתונים",
                    subtitle = "אחרי כמה אימונים יופיעו כאן עומס שבועי, ימי מנוחה ודירוגי אנרגיה.",
                    icon = Icons.Rounded.MonitorHeart,
                )
                return@ScrollPage
            }
            LiftCard(Modifier.fillMaxWidth()) {
                Text(report.summaryLine, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("אימונים ב-7 הימים האחרונים: ${report.workoutsLast7Days} · ימים בלי אימון: ${report.restDaysLast7Days}")
                Text("אימונים ב-28 הימים האחרונים: ${report.workoutsLast28Days}")
                report.avgEnergyRecent?.let {
                    Text("אנרגיה ממוצעת ב-3 האימונים האחרונים: ${ltr(Formatters.number(it, 1))}/5" +
                        (report.avgEnergyBefore?.let { b -> " (לפני כן: ${ltr(Formatters.number(b, 1))})" } ?: ""))
                } ?: Text("לא הוזנו דירוגי אנרגיה. אפשר להזין אותם בפרטי האימון.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                report.avgFatigueRecent?.let { Text("עייפות ממוצעת בדיווחים האחרונים: ${ltr(Formatters.number(it, 1))}/5") }
                report.avgSleepRecent?.let { Text("שינה ממוצעת בדיווחים האחרונים: ${ltr(Formatters.number(it, 1))} שעות") }
            }

            if (report.signals.isNotEmpty()) {
                SectionTitle("אותות שכדאי לשים לב אליהם")
                LiftCard(Modifier.fillMaxWidth()) {
                    report.signals.forEach { Text("• ${it.text}", Modifier.padding(vertical = 3.dp)) }
                    report.deloadHint?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionTitle("עומס שבועי (7 ימים מתגלגלים)")
            LiftCard(Modifier.fillMaxWidth()) {
                InteractiveChart(
                    points = report.weeklyLoads.map { w ->
                        ChartPoint(
                            date = w.range.end,
                            value = w.volumeKg,
                            title = "${Formatters.thousands(w.volumeKg)} ק״ג-חזרות",
                            detail = "${w.workouts} אימונים · ${w.workingSets} סטים · ${Formatters.date(w.range.start)}–${Formatters.date(w.range.end)}",
                        )
                    },
                    kind = ChartKind.BAR,
                    minPoints = 2,
                    description = "עומס שבועי",
                )
            }

            val energyPoints = d.sessions.filter { it.energyRating != null }.sortedBy { it.startedAt }.takeLast(24).map { s ->
                ChartPoint(s.date, s.energyRating!!.toDouble(), "אנרגיה ${s.energyRating}/5", "${s.title} · ${Formatters.date(s.date)}", s.id)
            }
            SectionTitle("דירוג אנרגיה לאורך זמן")
            LiftCard(Modifier.fillMaxWidth()) {
                InteractiveChart(
                    points = energyPoints,
                    onOpenSession = onOpenSession,
                    description = "דירוג אנרגיה",
                    emptyMessage = "נדרשים לפחות שני אימונים עם דירוג אנרגיה.",
                )
            }
            Text(RecoveryAnalytics.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}
