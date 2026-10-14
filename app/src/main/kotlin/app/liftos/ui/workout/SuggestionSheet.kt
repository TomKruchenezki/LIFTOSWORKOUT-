package app.liftos.ui.workout

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
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.liftos.domain.logic.Formatters
import app.liftos.domain.progression.ProgressionSuggestion
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.DecimalTextField
import app.liftos.ui.components.SoftButton
import app.liftos.ui.util.ltr

/**
 * Explains a progression suggestion and lets the user act on it. Nothing is changed unless a button is
 * pressed: apply the suggested weight, type another weight, or ignore the suggestion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionSheet(
    exerciseName: String,
    suggestion: ProgressionSuggestion,
    hasOpenSets: Boolean,
    onApply: (Double) -> Unit,
    onManual: (Double) -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var manual by remember { mutableStateOf<Double?>(suggestion.suggestedWeightKg ?: suggestion.currentWeightKg) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("הצעה: $exerciseName", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            AssistChip(onClick = {}, label = { Text(suggestion.confidence.labelHe) })
            Text(suggestion.headline, style = MaterialTheme.typography.titleMedium)
            suggestion.suggestedWeightKg?.let { w ->
                val repsText = suggestion.targetRepsMin?.let { " · יעד ${it}–${suggestion.targetRepsMax} חזרות" }.orEmpty()
                Text(
                    (if (suggestion.changesWeight) "משקל מוצע: " else "משקל להמשך: ") + ltr(Formatters.number(w, 2)) + " ק״ג" + repsText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text("למה?", style = MaterialTheme.typography.labelLarge)
            Text(suggestion.explanation, style = MaterialTheme.typography.bodyMedium)
            if (suggestion.evidence.isNotEmpty()) {
                Text("האימונים האחרונים:", style = MaterialTheme.typography.labelLarge)
                suggestion.evidence.forEach { Text(ltr(it), style = MaterialTheme.typography.bodySmall) }
            }
            suggestion.cautions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            Text(
                "זו הצעה בלבד: היא מחושבת מההיסטוריה שלך בכללים פשוטים (Double Progression) ולא משנה כלום בלי אישור שלך.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val suggested = suggestion.suggestedWeightKg
            if (suggestion.changesWeight && suggested != null && hasOpenSets) {
                BigButton("החל ${Formatters.number(suggested, 2)} ק״ג על הסטים שנותרו", onClick = { onApply(suggested) })
            }
            if (hasOpenSets) {
                Spacer(Modifier.height(2.dp))
                Text("או קבע משקל אחר:", style = MaterialTheme.typography.labelLarge)
                DecimalTextField("משקל", manual, { manual = it }, Modifier.fillMaxWidth(), "ק״ג")
                SoftButton(
                    "החל משקל זה",
                    enabled = manual != null && manual!! >= 0.0,
                    onClick = { manual?.let(onManual) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("כל הסטים של התרגיל כבר סומנו, ולכן אין סטים שאפשר לעדכן.", style = MaterialTheme.typography.bodySmall)
            }
            SoftButton("התעלם מההצעה", onClick = onIgnore, modifier = Modifier.fillMaxWidth())
        }
    }
}
