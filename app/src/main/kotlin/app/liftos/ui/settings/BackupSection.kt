package app.liftos.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.data.ExportFiles
import app.liftos.data.repo.ImportPreview
import app.liftos.di.AppContainer
import app.liftos.domain.backup.BackupSource
import app.liftos.domain.backup.BackupSummary
import app.liftos.domain.backup.ImportMode
import app.liftos.domain.export.CsvExporter
import app.liftos.domain.logic.Formatters
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.util.ShareHelper
import app.liftos.ui.util.ltr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val DELETE_WORD = "מחק"

/** Backup, restore, CSV export and "delete all my data" (the last two parts of the settings screen). */
@Composable
internal fun BackupSection(container: AppContainer, say: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lastBackup by container.devicePrefs.lastBackupAt.collectAsStateWithLifecycle(initialValue = null)
    val photoCount by remember { container.photos.observeAll().map { it.size } }.collectAsStateWithLifecycle(initialValue = 0)
    var includePhotos by rememberSaveable { mutableStateOf(true) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val withPhotos = includePhotos && photoCount > 0
    fun backupName() = "liftos-backup-${LocalDate.now()}." + if (withPhotos) "zip" else "json"

    val zipSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val n = container.backup.writeZip(uri, includePhotos = true)
                container.backup.markExported()
                say("הגיבוי נשמר בהצלחה" + if (n > 0) " ($n תמונות)" else "")
            } catch (e: Exception) {
                say("שמירת הגיבוי נכשלה: ${e.message}")
            } finally {
                busy = false
            }
        }
    }
    val jsonSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                container.backup.writeText(uri, container.backup.exportJson())
                container.backup.markExported()
                say("הגיבוי נשמר בהצלחה")
            } catch (e: Exception) {
                say("שמירת הגיבוי נכשלה: ${e.message}")
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                preview = container.backup.previewUri(uri)
                mode = ImportMode.MERGE
            } catch (e: Exception) {
                say("קריאת הקובץ נכשלה: ${e.message}")
            } finally {
                busy = false
            }
        }
    }
    fun saveBackup() { if (withPhotos) zipSave.launch(backupName()) else jsonSave.launch(backupName()) }

    SectionTitle("גיבוי וייצוא נתונים")
    LiftCard(Modifier.fillMaxWidth()) {
        val last = lastBackup
        Text(
            if (last == null) "עוד לא ייצאת גיבוי. מומלץ לגבות מדי פעם ולשמור עותק מחוץ לטלפון."
            else "גיבוי אחרון: ${Formatters.date(java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (last == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("כלול תמונות התקדמות", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (photoCount == 0) "עוד אין תמונות. הגיבוי יהיה קובץ JSON."
                    else if (includePhotos) "$photoCount תמונות. הגיבוי יהיה קובץ ZIP שמכיל גם את התמונות."
                    else "הגיבוי יהיה קובץ JSON בלי התמונות.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = includePhotos, onCheckedChange = { includePhotos = it }, enabled = photoCount > 0)
        }
        Spacer(Modifier.height(10.dp))
        SoftButton("שמור גיבוי מלא לקובץ", onClick = ::saveBackup, icon = Icons.Rounded.Download, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SoftButton("שתף גיבוי (Drive, מייל ועוד)", icon = Icons.Rounded.Share, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                busy = true
                try {
                    val name = backupName()
                    val (file, uri) = ExportFiles.newFile(context, name)
                    withContext(Dispatchers.IO) {
                        if (withPhotos) file.outputStream().use { container.backup.exportZip(it, includePhotos = true) }
                        else file.writeText(container.backup.exportJson(), Charsets.UTF_8)
                    }
                    ShareHelper.shareUri(context, name, if (withPhotos) "application/zip" else "application/json", uri)
                    container.backup.markExported()
                } catch (e: Exception) {
                    say("שיתוף הגיבוי נכשל: ${e.message}")
                } finally {
                    busy = false
                }
            }
        })
        Spacer(Modifier.height(8.dp))
        SoftButton("שחזר או ייבא מקובץ", icon = Icons.Rounded.Upload, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            openLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream", "*/*"))
        })
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("אימונים ל-CSV", modifier = Modifier.weight(1f), onClick = {
                scope.launch {
                    val details = mutableListOf<app.liftos.domain.model.WorkoutDetail>()
                    container.workouts.observeHistory().first().forEach { item -> container.workouts.getDetail(item.session.id)?.let { details += it } }
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
            "הגיבוי כולל גרסת סכמה מפורשת ונבדק לפני כל ייבוא. קובץ הגיבוי מכיל את כל הנתונים האישיים שלך, שמור אותו במקום בטוח. " +
                "כדי לשחזר מ-Google Drive, בחר בקובץ דרך בורר הקבצים של אנדרואיד.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    SectionTitle("מחיקת נתונים")
    LiftCard(Modifier.fillMaxWidth()) {
        Text(
            "אפשר למחוק את כל מה שהאפליקציה שמרה עליך: אימונים, תוכניות, מדדי גוף, יעדים, תמונות, אירועים והעתקי בטיחות. " +
                "ספריית התרגילים ותוכנית ההתחלה ייווצרו מחדש כמו בהתקנה ראשונה. אי אפשר לבטל, ולכן כדאי לשמור גיבוי קודם.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SoftButton("מחק את כל הנתונים שלי", modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { confirmDelete = true })
    }

    // ---- import flow -------------------------------------------------------------------------------------
    val p = preview
    if (p != null && !confirmReplace) {
        ImportPreviewDialog(
            preview = p, mode = mode, onMode = { mode = it },
            onConfirm = {
                if (mode == ImportMode.REPLACE_ALL) confirmReplace = true
                else scope.launch { runImport(container, p, mode, say); preview = null }
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
                        (if (p.file.includesPhotos) "גם התמונות הקיימות יוחלפו (הן יועברו לתיקיית בטיחות). " else "הקובץ לא כולל תמונות, לכן התמונות הקיימות יישארו. ") +
                        "לפני ההחלפה נשמר אוטומטית עותק בטיחות של המצב הנוכחי באחסון הפרטי של האפליקציה. " +
                        "מומלץ לשמור גם גיבוי חיצוני עכשיו.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    scope.launch { runImport(container, p, ImportMode.REPLACE_ALL, say); preview = null }
                }) { Text("החלף הכול", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = ::saveBackup) { Text("שמור קודם גיבוי של המצב הנוכחי") }
                    TextButton(onClick = { confirmReplace = false }) { Text("חזור") }
                }
            },
        )
    }

    if (confirmDelete) {
        DeleteAllDialog(
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    busy = true
                    try {
                        container.backup.deleteEverything()
                        say("כל הנתונים נמחקו. האפליקציה חזרה למצב התחלתי.")
                    } catch (e: Exception) {
                        say("המחיקה נכשלה: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
internal fun DeleteAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("למחוק את כל הנתונים?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("הפעולה תמחק לצמיתות את כל האימונים, המדידות, היעדים, האירועים והתמונות במכשיר הזה. אין דרך לשחזר אותם בלי קובץ גיבוי.")
                Text("כדי לאשר, הקלד את המילה \"$DELETE_WORD\":", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == DELETE_WORD) {
                Text("מחק הכול", color = if (typed.trim() == DELETE_WORD) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

private suspend fun runImport(container: AppContainer, preview: ImportPreview, mode: ImportMode, say: (String) -> Unit) {
    if (preview !is ImportPreview.Ready) return
    try {
        val r = container.backup.import(preview, mode)
        val s = r.imported
        say(
            "הייבוא הושלם: ${s.completedSessions} אימונים, ${s.plans} תוכניות, ${s.measurements} מדידות" +
                (if (r.photosRestored > 0) ", ${r.photosRestored} תמונות" else "") +
                (if (r.skippedActiveSessions > 0) " (דולג על אימון פעיל מהקובץ)" else "") +
                (r.safetyCopyName?.let { ". עותק בטיחות: $it" } ?: ""),
        )
    } catch (e: Exception) {
        say("הייבוא נכשל ולא בוצע שינוי בנתונים: ${e.message}")
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
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val exported = java.time.Instant.ofEpochMilli(preview.file.exportedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    Text(
                        if (preview.source == BackupSource.FLOOT) "מקור: ייצוא מהאפליקציה הקודמת (המרה אוטומטית, ללא אימות מול קובץ אמיתי)"
                        else "מקור: גיבוי LiftOS מתאריך ${Formatters.date(exported)}" +
                            (if (preview.file.appVersion.isNotBlank()) ", גרסה ${preview.file.appVersion}" else "") +
                            ", סכמה ${preview.file.schemaVersion}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val s = preview.summary
                    val n = preview.mergeNew
                    val merge = mode == ImportMode.MERGE
                    fun line(label: String, total: Int, new: Int, extra: String = ""): String =
                        "$label: ${ltr(total.toString())}$extra" + if (merge && preview.canImport) " · חדשים ${ltr(new.toString())}, כבר קיימים ${ltr((total - new).coerceAtLeast(0).toString())}" else ""
                    val extraActive = if (s.sessions > s.completedSessions) " (+${s.sessions - s.completedSessions} פעילים)" else ""
                    Text(line("אימונים", s.completedSessions, n.completedSessions, extraActive))
                    Text(line("תוכניות", s.plans, n.plans, " (${s.plannedWorkouts} ימי אימון)"))
                    Text(line("מדידות גוף", s.measurements, n.measurements))
                    if (s.customMetrics > 0) Text(line("שדות מדידה מותאמים", s.customMetrics, n.customMetrics))
                    Text(line("אירועים ביומן", s.events, n.events))
                    if (s.goals > 0) Text(line("יעדים", s.goals, n.goals))
                    Text(line("תרגילים אישיים", s.customExercises, n.customExercises))
                    if (preview.file.includesPhotos || s.photos > 0) {
                        val inArchive = if (preview.isArchive) " · בארכיון ${ltr(preview.archivePhotos.size.toString())} קבצים" else ""
                        Text(line("תמונות התקדמות", s.photos, n.photos, inArchive))
                    } else {
                        Text("תמונות: הקובץ לא כולל תמונות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.firstSessionDate != null) Text("טווח אימונים: ${s.firstSessionDate} עד ${s.lastSessionDate}", style = MaterialTheme.typography.bodySmall)
                    preview.report.errors.take(4).forEach { Text("שגיאה: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    preview.warnings.take(5).forEach { Text("שים לב: $it", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
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
