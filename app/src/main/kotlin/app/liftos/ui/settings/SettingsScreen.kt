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
    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch { container.settings.update(change) }
    }

    fun say(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

            RemindersSection(settings.reminders) { r ->
                // Asking at the moment a reminder is switched on is the natural time to explain the permission.
                if (r.anyScheduled && !settings.reminders.anyScheduled && Build.VERSION.SDK_INT >= 33 && !Notifications.canPostNotifications(context)) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                update { s -> s.copy(reminders = r) }
            }

            BackupSection(container, ::say)

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
