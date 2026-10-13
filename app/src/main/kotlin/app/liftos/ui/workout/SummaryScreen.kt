package app.liftos.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.export.CsvExporter
import app.liftos.domain.export.SessionExportFile
import app.liftos.domain.export.SessionJson
import app.liftos.domain.export.TextReport
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.OneRepMax
import app.liftos.domain.logic.RecordType
import app.liftos.domain.logic.SetEditing
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.logic.Verdict
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.SetType

import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.StatTile
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.ShareHelper
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.ltr
import kotlinx.coroutines.launch

@Composable
fun SummaryScreen(
    container: AppContainer,
    sessionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val vm = appViewModel(key = "summary-$sessionId") { SummaryViewModel(container, sessionId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var attachBody by remember { mutableStateOf(false) }

    when (val state = ui) {
        SummaryUi.Loading -> LoadingBox()
        SummaryUi.NotFound -> LaunchedEffect(Unit) { onBack() }
        is SummaryUi.Ready -> {
            val detail = state.detail
            val session = detail.session
            val zone = TimeUtils.zone(session.zoneId)
            val date = TimeUtils.parseDate(session.localDate)

            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    ScreenTopBar("סיכום אימון", onBack = onBack, actions = {
                        IconButton(onClick = { onEdit(session.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "ערוך אימון") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "מחק אימון") }
                    })
                },
            ) { padding ->
                ScrollPage(Modifier.padding(padding)) {
                    LiftCard(Modifier.fillMaxWidth()) {
                        Text(session.title, style = MaterialTheme.typography.headlineMedium)
                        if (session.planName.isNotBlank()) {
                            Text(session.planName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(date?.let { dateLine(it) } ?: session.localDate, style = MaterialTheme.typography.bodyMedium)
                        val times = ltr(Formatters.time(session.startedAt, zone)) +
                            (session.endedAt?.let { " – " + ltr(Formatters.time(it, zone)) } ?: "")
                        Text(
                            times + (session.durationMs?.let { " · משך ${ltr(Formatters.duration(it))}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (session.energyRating != null || session.moodRating != null) {
                            Text(
                                listOfNotNull(
                                    session.energyRating?.let { "אנרגיה $it/5" },
                                    session.moodRating?.let { "הרגשה $it/5" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(ltr(Formatters.thousands(state.totals.volumeKg)), "נפח (ק״ג-חזרות)", Modifier.weight(1f), accent = true)
                        StatTile(state.totals.workingSets.toString(), "סטי עבודה", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(state.totals.totalReps.toString(), "חזרות", Modifier.weight(1f))
                        StatTile(state.totals.exercisesPerformed.toString(), "תרגילים", Modifier.weight(1f))
                    }
                    Text(
                        "נפח = משקל × חזרות, לסטי עבודה שהושלמו בלבד (ללא חימום).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.records.isNotEmpty()) {
                        LiftCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.primaryContainer) {
                            Text("שיאים אישיים", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            state.records.forEach { r ->
                                val what = if (r.type == RecordType.MAX_WEIGHT) "משקל מקסימלי" else "1RM משוער"
                                Text(
                                    "${r.nameHe}: $what ${ltr(Formatters.number(r.newValue, 1))} ק״ג (קודם ${ltr(Formatters.number(r.oldValue, 1))})",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            if (state.records.any { it.type == RecordType.BEST_E1RM }) {
                                Text(OneRepMax.EXPLANATION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    state.comparison?.let { c ->
                        SectionTitle("לעומת האימון הקודם מאותו סוג (${c.previousDate})")
                        LiftCard(Modifier.fillMaxWidth()) {
                            val pct = c.volumePercent?.let { " (${ltr(signed(it, 1))}%)" }.orEmpty()
                            Text("נפח: ${ltr(signed(c.volumeDeltaKg, 0))} ק״ג-חזרות$pct")
                            Text("סטי עבודה: ${ltr(signedInt(c.workingSetsDelta))}")
                            c.durationDeltaMs?.let { Text("משך: ${ltr(signedInt((it / 60_000).toInt()))} דקות") }
                            Spacer(Modifier.height(6.dp))
                            c.exercises.forEach { ec ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ec.nameHe, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(verdictLabel(ec.verdict), color = verdictColor(ec.verdict), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Text(
                                "ההשוואה לפי הסט הטוב ביותר בכל תרגיל (${OneRepMax.EXPLANATION})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } ?: Text(
                        "אין אימון קודם מאותו סוג להשוואה.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SectionTitle("פירוט")
                    detail.exercises.forEach { ExerciseSummary(it) }

                    if (session.notes.isNotBlank()) {
                        LiftCard(Modifier.fillMaxWidth()) {
                            Text("הערות", style = MaterialTheme.typography.titleMedium)
                            Text(session.notes)
                        }
                    }

                    SectionTitle("שיתוף וייצוא")
                    LiftCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = attachBody, onCheckedChange = { attachBody = it })
                            Text("צרף את מדדי הגוף האחרונים שלי לדוח")
                        }
                        Spacer(Modifier.height(6.dp))
                        BigButton("העתק דוח לניתוח ב-ChatGPT", icon = Icons.Rounded.ContentCopy, onClick = {
                            scope.launch {
                                val body = if (attachBody) vm.bodySnapshot() else null
                                val report = TextReport.aiReport(detail, state.previous, body)
                                ShareHelper.copy(context, "דוח אימון LiftOS", report, sensitive = attachBody)
                                snackbar.showSnackbar("הדוח הועתק ללוח. הדבק אותו בשיחה עם ChatGPT או Claude. שום מידע לא נשלח מהאפליקציה.")
                            }
                        })
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoftButton("העתק סיכום", Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, onClick = {
                                ShareHelper.copy(context, "סיכום אימון", TextReport.workout(detail, state.previous))
                                scope.launch { snackbar.showSnackbar("הסיכום הועתק ללוח") }
                            })
                            SoftButton("שתף", Modifier.weight(1f), icon = Icons.Rounded.Share, onClick = {
                                scope.launch {
                                    val body = if (attachBody) vm.bodySnapshot() else null
                                    ShareHelper.shareText(context, "דוח אימון", TextReport.aiReport(detail, state.previous, body))
                                }
                            })
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoftButton("ייצא JSON", Modifier.weight(1f), onClick = {
                                val json = SessionJson.encode(SessionExportFile(exportedAt = System.currentTimeMillis(), workout = detail, previous = state.previous))
                                ShareHelper.shareFile(context, "liftos-workout-${session.localDate}.json", "application/json", json)
                            })
                            SoftButton("ייצא CSV", Modifier.weight(1f), onClick = {
                                ShareHelper.shareFile(context, "liftos-workout-${session.localDate}.csv", "text/csv", CsvExporter.sessions(listOf(detail)), utf8Bom = true)
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (confirmDelete) {
                ConfirmDialog(
                    title = "למחוק את האימון?",
                    text = "האימון וכל הסטים שלו יימחקו לצמיתות.",
                    confirmText = "מחק",
                    destructive = true,
                    onConfirm = { confirmDelete = false; vm.delete(onBack) },
                    onDismiss = { confirmDelete = false },
                )
            }
        }
    }
}

@Composable
private fun ExerciseSummary(item: ExerciseWithSets) {
    LiftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text(item.exercise.nameHe, style = MaterialTheme.typography.titleSmall)
        if (item.exercise.nameEn.isNotBlank()) {
            Text(item.exercise.nameEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.exercise.skipped) {
            Text("דולג" + if (item.exercise.skipReason.isNotBlank()) ": ${item.exercise.skipReason}" else "", color = MaterialTheme.colorScheme.tertiary)
        }
        val labels = SetEditing.labels(item.exercise, item.sets)
        item.sets.forEach { s ->
            val lb = labels[s.id]
            val prefix = if (s.setType == SetType.WARMUP) "חימום ${lb?.number?.removePrefix("ח")}" else "סט ${lb?.number}"
            val side = lb?.side?.let { " (${it.labelHe})" }.orEmpty()
            Text(
                "$prefix$side: ${TextReport.setText(item, s)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (s.isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.exercise.notes.isNotBlank()) {
            Text("הערה: ${item.exercise.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun verdictLabel(v: Verdict) = when (v) {
    Verdict.NEW -> "חדש"
    Verdict.UP -> "שיפור"
    Verdict.SAME -> "ללא שינוי"
    Verdict.DOWN -> "ירידה"
}

@Composable
private fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.UP -> MaterialTheme.colorScheme.primary
    Verdict.DOWN -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun signed(v: Double, decimals: Int): String {
    val n = Formatters.number(v, decimals)
    return if (v > 0) "+$n" else n.replace("-", "−")
}

private fun signedInt(v: Int): String = if (v > 0) "+$v" else v.toString().replace("-", "−")
