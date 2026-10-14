package app.liftos.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.ai.AiProviders
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.export.ExportFormat
import app.liftos.domain.export.ReportOptions
import app.liftos.domain.export.ReportScope
import app.liftos.domain.logic.Formatters
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.ShareHelper
import app.liftos.ui.util.ltr
import kotlinx.coroutines.launch

private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
private const val CLAUDE_PACKAGE = "com.anthropic.claude"

/**
 * Prepares a report of the user's training for an AI chat. The app never sends it: the user copies it or
 * shares it, and sees the full text first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiExportScreen(container: AppContainer, sessionId: String?, onBack: () -> Unit) {
    val vm = appViewModel(key = "ai-$sessionId") { AiExportViewModel(container, sessionId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var showAll by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar("נתח את האימון עם AI", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val sel = ui.selection
        val opts = sel.options
        val report = ui.report
        val sensitive = opts.includeBody || opts.includeNotes || opts.includeGoals

        ScrollPage(Modifier.padding(padding)) {
            LiftCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("איך זה עובד", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "האפליקציה מכינה דוח טקסט מהנתונים שהזנת, ואתה מעתיק או משתף אותו ל-ChatGPT או ל-Claude. " +
                        "האפליקציה לא שולחת שום מידע לשום שירות ואין לה הרשאת אינטרנט. מה שמופיע בתצוגה המקדימה למטה הוא בדיוק מה שיועתק.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("איזו תקופה לנתח")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportScope.entries.forEach { s ->
                    FilterChip(sel.scope == s, { vm.setScope(s) }, { Text(s.labelHe) })
                }
            }
            if (sel.scope == ReportScope.SINGLE) {
                if (ui.recentSessions.isEmpty()) {
                    Text("עוד אין אימונים שהושלמו.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("בחר אימון", style = MaterialTheme.typography.labelMedium)
                    val chosen = report?.sessionId
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.recentSessions.forEach { s ->
                            FilterChip(chosen == s.id, { vm.setSession(s.id) }, { Text("${s.title} · ${Formatters.date(s.date)}") })
                        }
                    }
                }
            }
            if (sel.scope == ReportScope.CUSTOM) {
                val custom = sel.custom ?: DateRange.lastDays(30, ui.today)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pickFrom = true }) { Text("מ: ${Formatters.date(custom.start)}") }
                    TextButton(onClick = { pickTo = true }) { Text("עד: ${Formatters.date(custom.end)}") }
                }
                if (pickFrom) DatePickerSheet(custom.start, onDismiss = { pickFrom = false }) { d ->
                    vm.setCustom(DateRange(d, if (d.isAfter(custom.end)) d else custom.end))
                }
                if (pickTo) DatePickerSheet(custom.end, onDismiss = { pickTo = false }) { d ->
                    vm.setCustom(DateRange(if (d.isBefore(custom.start)) d else custom.start, d))
                }
            }

            SectionTitle("מה לכלול")
            LiftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                OptionRow("הערות שכתבתי", opts.includeNotes) { vm.setOptions(opts.copy(includeNotes = it)) }
                OptionRow("התאוששות ודירוגי אנרגיה", opts.includeRecovery, enabled = sel.scope != ReportScope.SINGLE) { vm.setOptions(opts.copy(includeRecovery = it)) }
                OptionRow("שיאים אישיים", opts.includeRecords, enabled = sel.scope != ReportScope.SINGLE) { vm.setOptions(opts.copy(includeRecords = it)) }
                OptionRow("המלצות התקדמות של האפליקציה", opts.includeProgressionHints, enabled = sel.scope != ReportScope.SINGLE) { vm.setOptions(opts.copy(includeProgressionHints = it)) }
                OptionRow("יעדים פעילים", opts.includeGoals, enabled = sel.scope != ReportScope.SINGLE) { vm.setOptions(opts.copy(includeGoals = it)) }
                OptionRow("מדדי גוף (משקל, היקפים)", opts.includeBody) { vm.setOptions(opts.copy(includeBody = it)) }
            }
            Text(
                "מדדי גוף והערות הם מידע אישי: הם כבויים או מופעלים לפי בחירתך בלבד ויופיעו בדוח רק אם סימנת אותם.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("תצוגה מקדימה של הדוח")
            if (report == null || report.workouts == 0) {
                Text(
                    report?.text?.takeIf { sel.scope == ReportScope.SINGLE } ?: "לא תועדו אימונים בתקופה שנבחרה, ולכן אין מה לנתח. בחר תקופה אחרת.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val text = report.text
                val shown = if (showAll || text.length <= PREVIEW_CHARS) text else text.take(PREVIEW_CHARS) + "…"
                LiftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    Text(
                        "${ltr(report.workouts.toString())} אימונים · ${ltr(Formatters.thousands(text.length.toDouble()))} תווים",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            Text(shown, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (text.length > PREVIEW_CHARS) {
                        TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "הצג פחות" else "הצג את כל הדוח") }
                    }
                }
                if (text.length > LONG_REPORT_CHARS) {
                    Text(
                        "הדוח ארוך. בשיחה עם בינה מלאכותית עדיף לנתח טווח קצר יותר (למשל חודש) כדי לקבל תשובה ממוקדת.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            val ready = report != null && report.workouts > 0
            SectionTitle("העתקה ושיתוף")
            BigButton("העתק לניתוח", icon = Icons.Rounded.ContentCopy, enabled = ready, onClick = {
                report?.let {
                    ShareHelper.copy(context, "דוח LiftOS", it.text, sensitive = sensitive)
                    scope.launch {
                        snackbar.showSnackbar("הדוח הועתק ללוח. הדבק אותו בשיחה עם ChatGPT או Claude. האפליקציה לא שלחה אותו לשום מקום.")
                    }
                }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SoftButton("פתח ChatGPT", modifier = Modifier.weight(1f), enabled = ready, onClick = {
                    openAssistant(context, report?.text.orEmpty(), sensitive, CHATGPT_PACKAGE, "https://chatgpt.com/", "ChatGPT") { msg ->
                        scope.launch { snackbar.showSnackbar(msg) }
                    }
                })
                SoftButton("פתח Claude", modifier = Modifier.weight(1f), enabled = ready, onClick = {
                    openAssistant(context, report?.text.orEmpty(), sensitive, CLAUDE_PACKAGE, "https://claude.ai/new", "Claude") { msg ->
                        scope.launch { snackbar.showSnackbar(msg) }
                    }
                })
            }
            Text(
                "הכפתורים האלה מעתיקים את הדוח ופותחים את האפליקציה (או האתר) כדי שתדביק. שום דבר לא נשלח אוטומטית.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SoftButton("שתף כטקסט", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Share, enabled = ready, onClick = {
                report?.let { ShareHelper.shareText(context, "דוח LiftOS", it.text) }
            })

            SectionTitle("ייצוא לקובץ")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f -> FilterChip(format == f, { format = f }, { Text(f.labelHe) }) }
            }
            Text(
                when (format) {
                    ExportFormat.TXT -> "קובץ טקסט עם הדוח המלא."
                    ExportFormat.PDF -> "מסמך PDF עם הדוח המלא, מתאים לשליחה או הדפסה."
                    ExportFormat.JSON -> "הנתונים הגולמיים (סט אחר סט) במבנה JSON, לעיבוד ממוחשב."
                    ExportFormat.CSV -> "טבלה: שורה לכל סט, נפתחת באקסל. ההערות נכללות רק אם בחרת בהן."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SoftButton("שתף כקובץ ${format.labelHe}", modifier = Modifier.fillMaxWidth(), enabled = ready, onClick = {
                scope.launch {
                    val payload = vm.payload(format)
                    if (payload == null) snackbar.showSnackbar("אין מה לייצא בתקופה שנבחרה.")
                    else runCatching { ShareHelper.shareBytes(context, payload.fileName, payload.mime, payload.bytes) }
                        .onFailure { snackbar.showSnackbar("לא הצלחתי ליצור את הקובץ.") }
                }
            })

            if (!AiProviders.isDirectAnalysisAvailable) {
                Text(
                    "ניתוח ישיר בתוך האפליקציה (חיבור ל-API) אינו פעיל. אם יתווסף בעתיד, הוא יופעל רק באישור מפורש שלך, עם מפתח אישי שלא נשמר בקוד האפליקציה.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "תשובות של בינה מלאכותית עלולות להיות שגויות או לא מתאימות לך. הן אינן תחליף למאמן או לרופא; אם יש כאב או חשש, פנה לאיש מקצוע.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private const val PREVIEW_CHARS = 1_800
private const val LONG_REPORT_CHARS = 24_000

@Composable
private fun OptionRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Copies the report, then opens the assistant app; the website is the fallback. Always says what really happened. */
private fun openAssistant(
    context: android.content.Context,
    report: String,
    sensitive: Boolean,
    packageName: String,
    webUrl: String,
    name: String,
    say: (String) -> Unit,
) {
    ShareHelper.copy(context, "דוח LiftOS", report, sensitive = sensitive)
    when {
        ShareHelper.openApp(context, packageName) -> say("הדוח הועתק ואפליקציית $name נפתחה. הדבק אותו בשיחה: האפליקציה שלנו לא שלחה אותו.")
        ShareHelper.openLink(context, webUrl) -> say("אפליקציית $name לא מותקנת. הדוח הועתק ואתר $name נפתח בדפדפן. הדבק אותו שם.")
        else -> say("הדוח הועתק ללוח, אבל לא נמצאה אפליקציה או דפדפן לפתיחת $name.")
    }
}
