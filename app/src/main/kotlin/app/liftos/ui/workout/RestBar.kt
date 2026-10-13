package app.liftos.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.RestTimer
import app.liftos.domain.logic.RestTimerState
import app.liftos.ui.theme.NumberStyle
import app.liftos.ui.util.ltr
import kotlinx.coroutines.delay

/**
 * Rest countdown pinned above the keyboard/bottom edge. It is driven purely by the persisted
 * timestamps, so it is always right after returning from the background.
 */
@Composable
fun RestBar(
    rest: RestTimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAdd: (Int) -> Unit,
    onRestart: () -> Unit,
    onSkip: () -> Unit,
    onExpired: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(System.currentTimeMillis(), rest) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    val remaining = RestTimer.remainingMs(rest, now)
    val progress = RestTimer.progress(rest, now)

    // If the app is in front when the rest ends and nobody else claimed it, end it from here.
    LaunchedEffect(rest.endsAt) {
        val end = rest.endsAt ?: return@LaunchedEffect
        val wait = end - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        delay(1500) // give the service the first chance
        onExpired(end)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ltr(Formatters.duration(remaining)),
                        style = NumberStyle.copy(fontSize = androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        color = if (remaining == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (remaining == 0L) "המנוחה הסתיימה" else if (rest.isPaused) "מושהה" else rest.label.ifBlank { "מנוחה" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = if (rest.isPaused) onResume else onPause, modifier = Modifier.size(44.dp)) {
                    Icon(if (rest.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = if (rest.isPaused) "המשך" else "עצור")
                }
                FilledTonalButton(onClick = { onAdd(15) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                    Text(ltr("+15"))
                }
                FilledTonalButton(onClick = { onAdd(30) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                    Text(ltr("+30"))
                }
                IconButton(onClick = onRestart, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "אפס מנוחה")
                }
                IconButton(onClick = onSkip, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "דלג על המנוחה")
                }
            }
        }
    }
}

/** Quick adjust buttons shown above the keyboard while a number field has focus. */
@Composable
fun FieldToolbar(
    field: FocusedField,
    onAdjust: (Double) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps: List<Double> = when (field.kind) {
        FieldKind.WEIGHT -> listOf(-5.0, -2.5, 2.5, 5.0)
        FieldKind.REPS -> listOf(-1.0, 1.0, 2.0)
        FieldKind.EFFORT -> listOf(-1.0, 1.0)
        FieldKind.DURATION -> listOf(-5.0, 5.0, 15.0)
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 4.dp) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEach { d ->
                FilledTonalButton(
                    onClick = { onAdjust(d) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                ) {
                    val n = Formatters.number(kotlin.math.abs(d), 2)
                    Text(ltr((if (d > 0) "+" else "−") + n), style = MaterialTheme.typography.titleSmall)
                }
            }
            FilledTonalButton(
                onClick = onDone,
                modifier = Modifier.weight(1f).height(46.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) { Text("סגור") }
        }
    }
}
