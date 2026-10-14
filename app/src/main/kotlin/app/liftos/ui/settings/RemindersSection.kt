package app.liftos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ReminderSettings
import app.liftos.domain.reminders.ReminderPlanner
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.TimePickerSheet
import app.liftos.ui.util.ltr
import java.time.LocalTime

private val WEEK_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

/** Every reminder has its own switch; nothing is on until the user turns it on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemindersSection(r: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    SectionTitle("תזכורות והודעות")
    LiftCard(Modifier.fillMaxWidth()) {
        Toggle("תזכורת לאימון מתוכנן", "בימים שיש בהם אימון בתוכנית הפעילה, ולא אם כבר התאמנת היום", r.workoutEnabled) { onChange(r.copy(workoutEnabled = it)) }
        if (r.workoutEnabled) {
            TimeRow("שעה", r.workoutTime, LocalTime.of(17, 30)) { onChange(r.copy(workoutTime = it)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        Toggle("תזכורת לשקילה", "לא תופיע אם כבר תיעדת משקל באותו יום", r.weighInEnabled) { onChange(r.copy(weighInEnabled = it)) }
        if (r.weighInEnabled) {
            TimeRow("שעה", r.weighInTime, LocalTime.of(7, 30)) { onChange(r.copy(weighInTime = it)) }
            Text("בימים", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WEEK_ORDER.forEach { d ->
                    FilterChip(
                        selected = d in r.weighInWeekdays,
                        onClick = { onChange(r.copy(weighInWeekdays = (if (d in r.weighInWeekdays) r.weighInWeekdays - d else r.weighInWeekdays + d).sorted())) },
                        label = { Text(TimeUtils.weekdayShortHe(d)) },
                    )
                }
            }
            if (r.weighInWeekdays.isEmpty()) Hint("לא נבחר אף יום, ולכן לא תישלח תזכורת.")
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        Toggle("תזכורת למדידת היקפים", "אחרי מספר ימים קבוע מהמדידה האחרונה", r.measureEnabled) { onChange(r.copy(measureEnabled = it)) }
        if (r.measureEnabled) {
            TimeRow("שעה", r.measureTime, LocalTime.of(8, 0)) { onChange(r.copy(measureTime = it)) }
            DaysStepper("כל כמה ימים", r.measureEveryDays, 3, 60) { onChange(r.copy(measureEveryDays = it)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        Toggle("סיכום שבועי", "הודעה אחת בשבוע עם מספר האימונים והנפח האמיתיים", r.weeklySummaryEnabled) { onChange(r.copy(weeklySummaryEnabled = it)) }
        if (r.weeklySummaryEnabled) {
            TimeRow("שעה", r.weeklySummaryTime, LocalTime.of(20, 0)) { onChange(r.copy(weeklySummaryTime = it)) }
            Text("ביום", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WEEK_ORDER.forEach { d ->
                    FilterChip(r.weeklySummaryWeekday == d, { onChange(r.copy(weeklySummaryWeekday = d)) }, { Text(TimeUtils.weekdayShortHe(d)) })
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        Toggle("תזכורת לגיבוי", "אחרי מספר ימים קבוע מהגיבוי האחרון (בשעה 10:00)", r.backupEnabled) { onChange(r.copy(backupEnabled = it)) }
        if (r.backupEnabled) {
            DaysStepper("כל כמה ימים", r.backupEveryDays, 3, 90) { onChange(r.copy(backupEveryDays = it)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        Toggle("הודעה כשיעד הושג", "מוצגת ברגע שיעד שהגדרת מושג", r.goalReachedEnabled) { onChange(r.copy(goalReachedEnabled = it)) }
        Toggle("הודעה על שיא אישי חדש", "מוצגת בסיום אימון שבו נשבר שיא", r.newRecordEnabled) { onChange(r.copy(newRecordEnabled = it)) }

        Spacer(Modifier.height(8.dp))
        Hint(
            "התזכורות נוצרות על המכשיר בלבד, בלי שרת. הן יכולות להתאחר בכמה דקות כשהטלפון במצב חיסכון, " +
                "מכבדות את מצב נא לא להפריע, ודורשות הרשאת התראות. אחרי הפעלה מחדש של הטלפון הן נטענות מחדש.",
        )
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TimeRow(label: String, value: String, fallback: LocalTime, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val t = ReminderPlanner.parseTime(value, fallback)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { open = true }) { Text(ltr(ReminderPlanner.format(t)), style = MaterialTheme.typography.titleMedium) }
    }
    if (open) {
        TimePickerSheet(t.hour, t.minute, onDismiss = { open = false }) { h, m -> onPick(ReminderPlanner.format(LocalTime.of(h, m))) }
    }
}

@Composable
private fun DaysStepper(label: String, days: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((days - 1).coerceAtLeast(min)) }) { Text("−") }
        Text(ltr(days.toString()), Modifier.width(48.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange((days + 1).coerceAtMost(max)) }) { Text("+") }
    }
}
