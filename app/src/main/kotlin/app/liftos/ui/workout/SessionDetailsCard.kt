package app.liftos.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.WorkoutSession
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.TextInputDialog
import app.liftos.ui.components.TimePickerSheet
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.ltr
import java.time.LocalDate

/** General notes, how the workout felt, and (when editing history) title, date and times. */
@Composable
fun SessionDetailsCard(
    session: WorkoutSession,
    editableTimes: Boolean,
    onNotes: (String) -> Unit,
    onRatings: (Int?, Int?) -> Unit,
    onRecovery: (difficulty: Int?, quality: Int?, soreness: Int?, fatigue: Int?, sleepHours: Double?) -> Unit,
    onTitle: (String) -> Unit,
    onTimes: (Long, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    LiftCard(modifier = modifier.fillMaxWidth()) {
        Text("פרטי האימון", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("שם האימון", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(session.title, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { renaming = true }) { Text("שנה שם") }
        }

        if (editableTimes) TimesEditor(session, onTimes)

        Spacer(Modifier.height(8.dp))
        RatingRow("רמת אנרגיה", session.energyRating) { onRatings(it, session.moodRating) }
        RatingRow("הרגשה כללית", session.moodRating) { onRatings(session.energyRating, it) }
        var recoveryOpen by remember(session.id) {
            mutableStateOf(
                session.difficultyRating != null || session.qualityRating != null || session.sorenessRating != null ||
                    session.fatigueRating != null || session.sleepHours != null,
            )
        }
        TextButton(onClick = { recoveryOpen = !recoveryOpen }) {
            Text(if (recoveryOpen) "הסתר נתוני התאוששות" else "נתוני התאוששות (אופציונלי)")
        }
        if (recoveryOpen) {
            RatingRow("קושי האימון", session.difficultyRating) {
                onRecovery(it, session.qualityRating, session.sorenessRating, session.fatigueRating, session.sleepHours)
            }
            RatingRow("איכות האימון", session.qualityRating) {
                onRecovery(session.difficultyRating, it, session.sorenessRating, session.fatigueRating, session.sleepHours)
            }
            RatingRow("כאבי שרירים", session.sorenessRating) {
                onRecovery(session.difficultyRating, session.qualityRating, it, session.fatigueRating, session.sleepHours)
            }
            RatingRow("עייפות כללית", session.fatigueRating) {
                onRecovery(session.difficultyRating, session.qualityRating, session.sorenessRating, it, session.sleepHours)
            }
            SleepField(session.sleepHours) {
                onRecovery(session.difficultyRating, session.qualityRating, session.sorenessRating, session.fatigueRating, it)
            }
            Text(
                "כל השדות אופציונליים. הם משמשים רק להצגת מגמות עומס והתאוששות בלשונית ההתקדמות.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        DebouncedText(value = session.notes, label = "הערות כלליות לאימון", onSave = onNotes)
    }
    if (renaming) {
        TextInputDialog(
            title = "שם האימון",
            label = "שם",
            initial = session.title,
            onConfirm = { renaming = false; onTitle(it) },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun SleepField(value: Double?, onChange: (Double?) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("שעות שינה בלילה האחרון", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        app.liftos.ui.components.DecimalField(
            value = value,
            onValueChange = { onChange(it?.takeIf { v -> v in 0.0..24.0 }) },
            modifier = Modifier.size(width = 84.dp, height = 48.dp),
            placeholder = "7.5",
        )
    }
}

@Composable
fun RatingRow(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                FilterChip(
                    selected = value == n,
                    onClick = { onChange(if (value == n) null else n) },
                    label = { Text(n.toString(), modifier = Modifier.size(width = 26.dp, height = 22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                )
            }
        }
    }
}

@Composable
private fun TimesEditor(session: WorkoutSession, onTimes: (Long, Long?) -> Unit) {
    val zone = TimeUtils.zone(session.zoneId)
    val startDate = TimeUtils.localDate(session.startedAt, zone)
    val startZdt = java.time.Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val endZdt = session.endedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone) }
    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("תאריך: ${dateLine(startDate)}", Modifier.weight(1f))
            TextButton(onClick = { pickDate = true }) { Text("שנה") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("התחלה: ${ltr(Formatters.time(session.startedAt, zone))}", Modifier.weight(1f))
            TextButton(onClick = { pickStart = true }) { Text("שנה") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("סיום: ${session.endedAt?.let { ltr(Formatters.time(it, zone)) } ?: "-"}", Modifier.weight(1f))
            TextButton(onClick = { pickEnd = true }) { Text("שנה") }
        }
        session.durationMs?.let {
            Text("משך: ${ltr(Formatters.duration(it))}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (pickDate) {
        DatePickerSheet(startDate, onDismiss = { pickDate = false }) { picked ->
            val duration = session.endedAt?.minus(session.startedAt)
            val start = TimeUtils.toEpochMs(picked, startZdt.hour, startZdt.minute, zone)
            onTimes(start, duration?.let { start + it })
        }
    }
    if (pickStart) {
        TimePickerSheet(startZdt.hour, startZdt.minute, onDismiss = { pickStart = false }) { h, m ->
            val duration = session.endedAt?.minus(session.startedAt)
            val start = TimeUtils.toEpochMs(startDate, h, m, zone)
            onTimes(start, duration?.let { start + it })
        }
    }
    if (pickEnd) {
        val base: LocalDate = endZdt?.toLocalDate() ?: startDate
        TimePickerSheet(endZdt?.hour ?: startZdt.hour, endZdt?.minute ?: startZdt.minute, onDismiss = { pickEnd = false }) { h, m ->
            var end = TimeUtils.toEpochMs(base, h, m, zone)
            if (end < session.startedAt) end += 24 * 3600 * 1000L // finished after midnight
            onTimes(session.startedAt, end)
        }
    }
}
