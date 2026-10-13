package app.liftos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.Formatters
import java.time.temporal.ChronoUnit

/**
 * Line chart of dated values with an optional trend line (moving average). Tap the chart to read
 * a value. Raw points are drawn as dots so daily noise stays visible next to the smoothed trend.
 */
@Composable
fun DatedLineChart(
    points: List<BodyTrends.Point>,
    trend: List<BodyTrends.Point>,
    unit: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val sorted = points.sortedBy { it.date }
    val colors = MaterialTheme.colorScheme
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    val minV = minOf(sorted.minOf { it.value }, trend.minOfOrNull { it.value } ?: Double.MAX_VALUE)
    val maxV = maxOf(sorted.maxOf { it.value }, trend.maxOfOrNull { it.value } ?: Double.MIN_VALUE)
    val pad = ((maxV - minV) * 0.15).coerceAtLeast(0.5)
    val lo = minV - pad
    val hi = maxV + pad
    val first = sorted.first().date
    val spanDays = ChronoUnit.DAYS.between(first, sorted.last().date).coerceAtLeast(1).toFloat()

    Column(modifier) {
        val sel = selected?.let { sorted.getOrNull(it) }
        Text(
            text = if (sel != null) "${Formatters.number(sel.value, 1)} $unit · ${Formatters.date(sel.date)}" else "לחץ על הגרף לצפייה בערך",
            style = MaterialTheme.typography.labelMedium,
            color = if (sel != null) colors.primary else colors.onSurfaceVariant,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = 8.dp)
                .pointerInput(sorted) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val xs = sorted.map { p -> ChronoUnit.DAYS.between(first, p.date) / spanDays * w }
                        selected = xs.indices.minByOrNull { kotlin.math.abs(xs[it] - tap.x) }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            fun x(d: java.time.LocalDate) = (ChronoUnit.DAYS.between(first, d) / spanDays) * w
            fun y(v: Double) = (h - ((v - lo) / (hi - lo)).toFloat() * h)

            // Grid: three horizontal guide lines.
            for (i in 0..2) {
                val gy = h * i / 2f
                drawLine(colors.outlineVariant, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            if (trend.size > 1) {
                val path = Path()
                trend.sortedBy { it.date }.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(x(p.date), y(p.value)) else path.lineTo(x(p.date), y(p.value))
                }
                drawPath(path, colors.primary, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
            sorted.forEachIndexed { i, p ->
                val isSel = i == selected
                drawCircle(
                    color = if (isSel) colors.primary else colors.onSurfaceVariant,
                    radius = if (isSel) 11f else 6f,
                    center = Offset(x(p.date), y(p.value)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(Formatters.number(lo + pad, 1), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(Formatters.number(hi - pad, 1), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}
