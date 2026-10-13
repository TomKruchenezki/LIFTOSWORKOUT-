package app.liftos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.liftos.domain.logic.Formatters
import app.liftos.ui.theme.NumberStyle

private val decimalPattern = Regex("^[0-9]{0,4}([.,][0-9]{0,2})?$")

/**
 * A compact numeric box for set tables. It keeps its own text while typing (so "62." is not
 * rewritten to "62" under the cursor) and only takes the model value when it was changed from
 * outside (e.g. by a +2.5 button).
 */
@Composable
private fun NumberBox(
    external: String,
    canonical: (String) -> String,
    accepts: (String) -> Boolean,
    onTextChanged: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    enabled: Boolean,
    highlight: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(external)) }
    LaunchedEffect(external) {
        if (canonical(field.text) != external) field = TextFieldValue(external, TextRange(external.length))
    }
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        focused -> colors.primary
        else -> Color.Transparent
    }
    BasicTextField(
        value = field,
        onValueChange = { new ->
            if (accepts(new.text)) {
                field = new
                onTextChanged(new.text)
            }
        },
        enabled = enabled,
        singleLine = true,
        textStyle = NumberStyle.copy(
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Ltr,
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(),
        modifier = modifier
            .height(48.dp)
            .background(if (highlight) colors.primaryContainer else colors.surfaceContainerHighest, shape)
            .border(2.dp, borderColor, shape)
            .onFocusChanged {
                val changed = focused != it.isFocused
                focused = it.isFocused
                if (it.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
                // The initial "not focused" callback of every freshly composed field must not look like a blur.
                if (changed) onFocusChanged(it.isFocused)
            },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (field.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        style = NumberStyle.copy(textAlign = TextAlign.Center, textDirection = TextDirection.Ltr),
                        color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                inner()
            }
        },
    )
}

/** Decimal input such as weight (62.5). Accepts "," or "." and up to two decimals. */
@Composable
fun DecimalField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    NumberBox(
        external = Formatters.number(value),
        canonical = { Formatters.number(Formatters.parseDecimal(it)) },
        accepts = { decimalPattern.matches(it) },
        onTextChanged = { onValueChange(Formatters.parseDecimal(it)) },
        modifier = modifier,
        placeholder = placeholder,
        keyboardType = KeyboardType.Decimal,
        imeAction = imeAction,
        enabled = enabled,
        highlight = highlight,
        onFocusChanged = onFocusChanged,
    )
}

/** Whole-number input such as reps, RIR or seconds. */
@Composable
fun IntField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxDigits: Int = 3,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val pattern = remember(maxDigits) { Regex("^[0-9]{0,$maxDigits}$") }
    NumberBox(
        external = value?.toString().orEmpty(),
        canonical = { it.toIntOrNull()?.toString().orEmpty() },
        accepts = { pattern.matches(it) },
        onTextChanged = { onValueChange(it.toIntOrNull()) },
        modifier = modifier,
        placeholder = placeholder,
        keyboardType = KeyboardType.Number,
        imeAction = imeAction,
        enabled = enabled,
        highlight = highlight,
        onFocusChanged = onFocusChanged,
    )
}

/** Labelled decimal input for forms (body measurements); keeps its own text while typing. */
@Composable
fun DecimalTextField(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    val external = Formatters.number(value)
    var text by remember { mutableStateOf(external) }
    LaunchedEffect(external) {
        if (Formatters.number(Formatters.parseDecimal(text)) != external) text = external
    }
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { new ->
            if (decimalPattern.matches(new)) {
                text = new
                onValueChange(Formatters.parseDecimal(new))
            }
        },
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) ({ Text(suffix) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        textStyle = NumberStyle.copy(textDirection = TextDirection.Ltr),
        modifier = modifier,
    )
}
