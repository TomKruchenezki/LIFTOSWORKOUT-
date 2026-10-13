package app.liftos.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import app.liftos.data.repo.ImportPreview
import app.liftos.di.AppContainer
import app.liftos.domain.backup.BackupSource
import app.liftos.domain.backup.ImportMode
import app.liftos.domain.export.CsvExporter
import app.liftos.domain.logic.Formatters
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.EffortMode
import app.liftos.domain.model.ThemeMode
import app.liftos.service.Notifications
import app.liftos.ui.components.ChipRow
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.util.ShareHelper
import app.liftos.ui.util.ltr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val lastBackup by container.devicePrefs.lastBackupAt.collectAsStateWithLifecycle(initialValue = null)
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch { container.settings.update(change) }
    }

    fun say(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                container.backup.writeText(uri, container.backup.exportJson())
                container.backup.markExported()
                snackbar.showSnackbar("הגיבוי נשמר בהצלחה")
            } catch (e: Exception) {
                snackbar.showSnackbar("שמירת הגיבוי נכשלה: ${e.message}")
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = container.backup.readText(uri)
                preview = container.backup.preview(text)
                mode = ImportMode.MERGE
            } catch (e: Exception) {
                snackbar.showSnackbar("קריאת הקובץ נכשלה: ${e.message}")
            } finally {
                busy = false
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun backupName() = "liftos-backup-${LocalDate.now()}.json"

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ScreenTopBar("הגדרות", onBack = onBack) },
    ) { padding ->
        ScrollPage(Modifier.padding(padding)) {
            SectionTitle("מראה")
            LiftCard(Modifier.fillMaxWidth()) {
                Text("ערכת נושא", style = MaterialTheme.typography.labelMedium)
                ChipRow(ThemeMode.entries, settings.themeMode, { it.labelHe }, { it?.let { m -> update { s -> s.copy(themeMode = m) } } }, allowClear = false, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle("אימון")
            LiftCard(Modifier.fillMaxWidth()) {
                Text("מדד מאמץ בסטים", style = MaterialTheme.typography.labelMedium)
                ChipRow(EffortMode.entries, settings.effortMode, { it.labelHe }, { it?.let { m -> update { s -> s.copy(effortMode = m) } } }, allowClear = false, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
                SwitchRow("התחל טיימר מנוחה אוטומטית", "מופעל כשמסמנים סט כבוצע", settings.autoStartRest) { update { s -> s.copy(autoStartRest = it) } }
                SwitchRow("שמור על המסך דלוק באימון", "כדי שלא תצטרך לפתוח את הטלפון שוב ושוב", settings.keepScreenOn) { update { s -> s.copy(keepScreenOn = it) } }
                SwitchRow("רטט בסימון סט", "משוב עדין בכל סט שהושלם", settings.vibrateOnSetComplete) { update { s -> s.copy(vibrateOnSetComplete = it) } }
                SwitchRow("רטט בסיום מנוחה", "לא פעיל כשמצב נא לא להפריע דולק", settings.vibrateOnRestEnd) { update { s -> s.copy(vibrateOnRestEnd = it) } }
                SwitchRow("התראה בסיום מנוחה", "מוצגת גם כשהמסך כבוי", settings.restNotificationEnabled) { update { s -> s.copy(restNotificationEnabled = it) } }
                Spacer(Modifier.height(8.dp))
                StepperRow("זמן מנוחה לתרגילים מורכבים", settings.defaultRestCompoundSec, 30, 600, 15) { update { s -> s.copy(defaultRestCompoundSec = it) } }
                StepperRow("זמן מנוחה לתרגילי בידוד", settings.defaultRestIsolationSec, 15, 600, 15) { update { s -> s.copy(defaultRestIsolationSec = it) } }
                Text(
                    "ברירות המחדל חלות על תרגילים חדשים. לכל תרגיל בתוכנית אפשר לקבוע זמן מנוחה משלו.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("יעד אימונים שבועי (0 = לפי התוכנית)", Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { update { s -> s.copy(weeklyGoalWorkouts = (s.weeklyGoalWorkouts - 1).coerceAtLeast(0)) } }) { Text("−") }
                    Text(ltr(settings.weeklyGoalWorkouts.toString()), Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    FilledTonalIconButton(onClick = { update { s -> s.copy(weeklyGoalWorkouts = (s.weeklyGoalWorkouts + 1).coerceAtMost(14)) } }) { Text("+") }
                }
            }

            SectionTitle("התראות")
            LiftCard(Modifier.fillMaxWidth()) {
                val granted = Notifications.canPostNotifications(context)
                Text(
                    if (granted) "ההרשאה להצגת התראות פעילה." else "אין הרשאה להצגת התראות. טיימר המנוחה ימשיך לפעול, אך ללא התראה במסך נעול.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!granted && Build.VERSION.SDK_INT >= 33) {
                        SoftButton("אפשר התראות", modifier = Modifier.weight(1f), onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
                    }
                    SoftButton("הגדרות המערכת", modifier = Modifier.weight(1f), onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    })
                }
            }

            SectionTitle("גיבוי וייצוא נתונים")
            LiftCard(Modifier.fillMaxWidth()) {
                val last = lastBackup
                Text(
                    if (last == null) "עוד לא ייצאת גיבוי. מומלץ לגבות מדי פעם ולשמור עותק מחוץ לטלפון." else "גיבוי אחרון: ${Formatters.date(java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (last == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                SoftButton("שמור גיבוי מלא לקובץ (JSON)", onClick = { saveLauncher.launch(backupName()) }, icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SoftButton("שתף גיבוי (Drive, מייל ועוד)", icon = Icons.Rounded.Share, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        try {
                            ShareHelper.shareFile(context, backupName(), "application/json", container.backup.exportJson())
                            container.backup.markExported()
                        } catch (e: Exception) {
                            snackbar.showSnackbar("שיתוף הגיבוי נכשל: ${e.message}")
                        }
                    }
                })
                Spacer(Modifier.height(8.dp))
                SoftButton("שחזר או ייבא מקובץ", icon = Icons.Rounded.Upload, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("אימונים ל-CSV", modifier = Modifier.weight(1f), onClick = {
                        scope.launch {
                            val all = container.workouts.observeHistory()
                            val details = mutableListOf<app.liftos.domain.model.WorkoutDetail>()
                            all.first().forEach { item -> container.workouts.getDetail(item.session.id)?.let { details += it } }
                            ShareHelper.shareFile(context, "liftos-workouts-${LocalDate.now()}.csv", "text/csv", CsvExporter.sessions(details), utf8Bom = true)
                        }
                    })
                    SoftButton("מדידות ל-CSV", modifier = Modifier.weight(1f), onClick = {
                        scope.launch {
                            ShareHelper.shareFile(context, "liftos-measurements-${LocalDate.now()}.csv", "text/csv", CsvExporter.measurements(container.measurements.observeAllOnce()), utf8Bom = true)
                        }
                    })
                }
                Text(
                    "הגיבוי נשמר כקובץ JSON עם גרסת סכמה מפורשת. אפשר לבחור בו מ-Google Drive דרך בורר הקבצים של אנדרואיד. קובץ הגיבוי מכיל את כל הנתונים האישיים שלך, שמור אותו במקום בטוח.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            SectionTitle("אודות ופרטיות")
            LiftCard(Modifier.fillMaxWidth()) {
                Text("LiftOS · גרסה ${versionName(context)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "כל הנתונים נשמרים רק על המכשיר הזה. לאפליקציה אין הרשאת אינטרנט, אין פרסומות ואין איסוף נתונים. ייצוא ושיתוף מתבצעים רק כשאתה בוחר בכך, וקישורי וידאו נפתחים באפליקציה חיצונית.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- import flow --------------------------------------------------------------------------------------
    val p = preview
    if (p != null && !confirmReplace) {
        ImportPreviewDialog(
            preview = p, mode = mode, onMode = { mode = it },
            onConfirm = {
                if (mode == ImportMode.REPLACE_ALL) confirmReplace = true else scope.launch { runImport(container, p, mode, ::say); preview = null }
            },
            onDismiss = { preview = null },
        )
    }
    if (p is ImportPreview.Ready && confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("להחליף את כל הנתונים?") },
            text = {
                Text(
                    "כל האימונים, התוכניות והמדידות הקיימים במכשיר יימחקו ויוחלפו בתוכן הגיבוי. " +
                        "לפני ההחלפה נשמר אוטומטית עותק בטיחות של המצב הנוכחי באחסון הפרטי של האפליקציה. " +
                        "מומלץ לשמור גם גיבוי חיצוני עכשיו.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    scope.launch { runImport(container, p, ImportMode.REPLACE_ALL, ::say); preview = null }
                }) { Text("החלף הכול", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { saveLauncher.launch(backupName()) }) { Text("שמור קודם גיבוי של המצב הנוכחי") }
                    TextButton(onClick = { confirmReplace = false }) { Text("חזור") }
                }
            },
        )
    }
}

private suspend fun runImport(container: AppContainer, preview: ImportPreview, mode: ImportMode, say: (String) -> Unit) {
    if (preview !is ImportPreview.Ready) return
    try {
        val r = container.backup.import(preview, mode)
        val s = r.imported
        say("הייבוא הושלם: ${s.completedSessions} אימונים, ${s.plans} תוכניות, ${s.measurements} מדידות" +
            if (r.skippedActiveSessions > 0) " (דולג על אימון פעיל מהקובץ)" else "")
    } catch (e: Exception) {
        say("הייבוא נכשל ולא בוצע שינוי בנתונים: ${e.message}")
    }
}

private fun versionName(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (_: Exception) {
    ""
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(title: String, seconds: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((seconds - step).coerceAtLeast(min)) }) { Text("−") }
        Text(ltr(Formatters.duration(seconds * 1000L)), Modifier.width(64.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange((seconds + step).coerceAtMost(max)) }) { Text("+") }
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    mode: ImportMode,
    onMode: (ImportMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (preview) {
        is ImportPreview.Failure -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("לא ניתן לייבא את הקובץ") },
            text = { Text(preview.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        )
        is ImportPreview.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (preview.canImport) "תצוגה מקדימה של הייבוא" else "הקובץ לא עבר אימות") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (preview.source == BackupSource.FLOOT) "מקור: ייצוא מהאפליקציה הקודמת (המרה אוטומטית, ללא אימות מול קובץ אמיתי)" else "מקור: גיבוי LiftOS",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val s = preview.summary
                    Text("אימונים: ${s.completedSessions}${if (s.sessions > s.completedSessions) " (+${s.sessions - s.completedSessions} פעילים)" else ""}")
                    Text("תוכניות: ${s.plans} (${s.plannedWorkouts} ימי אימון)")
                    Text("מדידות גוף: ${s.measurements}")
                    Text("פעילויות ביומן: ${s.events}")
                    Text("תרגילים אישיים: ${s.customExercises}")
                    if (s.firstSessionDate != null) Text("טווח אימונים: ${s.firstSessionDate} עד ${s.lastSessionDate}", style = MaterialTheme.typography.bodySmall)
                    preview.report.errors.take(4).forEach { Text("שגיאה: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    preview.warnings.take(4).forEach { Text("שים לב: $it", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                    if (preview.canImport) {
                        Spacer(Modifier.height(6.dp))
                        ModeRow("מזג: הוסף רק מה שחסר (לא מוחק ולא דורס)", mode == ImportMode.MERGE) { onMode(ImportMode.MERGE) }
                        ModeRow("החלף: מחק את הנתונים הקיימים ושחזר מהקובץ", mode == ImportMode.REPLACE_ALL) { onMode(ImportMode.REPLACE_ALL) }
                    }
                }
            },
            confirmButton = {
                if (preview.canImport) TextButton(onClick = onConfirm) { Text(if (mode == ImportMode.MERGE) "מזג" else "המשך") }
                else TextButton(onClick = onDismiss) { Text("סגור") }
            },
            dismissButton = { if (preview.canImport) TextButton(onClick = onDismiss) { Text("ביטול") } },
        )
    }
}

@Composable
private fun ModeRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
