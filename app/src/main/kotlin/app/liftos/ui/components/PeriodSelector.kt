package app.liftos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.Period
import app.liftos.domain.analytics.PeriodPreset
import app.liftos.domain.logic.Formatters
import java.time.LocalDate

/**
 * The period filter used next to every chart: 7 / 30 / 90 days, 6 months, a year, everything, or a custom
 * date range chosen with two date pickers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodSelector(
    period: Period,
    today: LocalDate,
    onChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<PeriodPreset> = PeriodPreset.entries,
) {
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val custom = period.custom ?: DateRange.lastDays(30, today)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = period.preset == preset,
                    onClick = { onChange(Period(preset, if (preset == PeriodPreset.CUSTOM) custom else null)) },
                    label = { Text(preset.labelHe) },
                )
            }
        }
        if (period.preset == PeriodPreset.CUSTOM) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pickFrom = true }) { Text("מ: ${Formatters.date(custom.start)}") }
                TextButton(onClick = { pickTo = true }) { Text("עד: ${Formatters.date(custom.end)}") }
            }
        }
    }
    if (pickFrom) {
        DatePickerSheet(custom.start, onDismiss = { pickFrom = false }) { d ->
            val end = if (d.isAfter(custom.end)) d else custom.end
            onChange(Period(PeriodPreset.CUSTOM, DateRange(d, end)))
        }
    }
    if (pickTo) {
        DatePickerSheet(custom.end, onDismiss = { pickTo = false }) { d ->
            val start = if (d.isBefore(custom.start)) d else custom.start
            onChange(Period(PeriodPreset.CUSTOM, DateRange(start, d)))
        }
    }
}
