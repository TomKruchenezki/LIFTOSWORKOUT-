package app.liftos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.liftos.domain.analytics.ChartPoint
import app.liftos.domain.analytics.DistributionSlice
import app.liftos.domain.logic.Formatters
import app.liftos.ui.util.ltr
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class ChartKind { LINE, BAR }

/** Shown instead of a chart when there is not enough data: a chart is never invented. */
@Composable
fun ChartEmpty(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * A chart over [ChartPoint]s. Tap or slide a finger to pick a point: the card above the chart shows the
 * exact value, the date and the workout it came from (with a button to open it).
 * [trend] draws an optional smoothed line (such as a moving average) behind the dots.
 */
@Composable
fun InteractiveChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    kind: ChartKind = ChartKind.LINE,
    trend: List<ChartPoint> = emptyList(),
    minPoints: Int = 2,
    emptyMessage: String = "אין מספיק נתונים להצגת גרף. נדרשים אימונים נוספים.",
    onOpenSession: ((String) -> Unit)? = null,
    description: String = "גרף",
) {
    val sorted = remember(points) { points.sortedBy { it.date } }
    if (sorted.size < minPoints) {
        ChartEmpty(
            if (sorted.size == 1 && minPoints > 1) "יש נקודה אחת בלבד (${sorted[0].title}). ${emptyMessage}" else emptyMessage,
            modifier,
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    var selected by remember(sorted) { mutableStateOf<Int?>(sorted.lastIndex) }
    val allValues = sorted.map { it.value } + trend.map { it.value }
    val rawMin = if (kind == ChartKind.BAR) 0.0 else allValues.min()
    val rawMax = allValues.max()
    val pad = if (kind == ChartKind.BAR) (rawMax * 0.08) else ((rawMax - rawMin) * 0.15).coerceAtLeast(0.5)
    val lo = if (kind == ChartKind.BAR) 0.0 else rawMin - pad
    val hi = (if (rawMax == rawMin) rawMax + 1.0 else rawMax) + pad
    val first = sorted.first().date
    val spanDays = ChronoUnit.DAYS.between(first, sorted.last().date).coerceAtLeast(1).toFloat()

    Column(modifier) {
        val sel = selected?.let { sorted.getOrNull(it) }
        Box(
            Modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (sel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sel.title, style = MaterialTheme.typography.titleMedium, color = colors.primary)
                        if (sel.detail.isNotBlank()) {
                            Text(sel.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    val id = sel.sessionId
                    if (id != null && onOpenSession != null) {
                        TextButton(onClick = { onOpenSession(id) }) { Text("פתח אימון") }
                    }
                }
            } else {
                Text("לחץ על הגרף כדי לראות ערך מדויק", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(top = 10.dp)
                .semantics { contentDescription = description }
                .pointerInput(sorted) {
                    fun pick(x: Float) {
                        val w = size.width.toFloat()
                        val xs = sorted.mapIndexed { i, p -> xOf(i, p, first, spanDays, w, sorted.size, kind) }
                        selected = xs.indices.minByOrNull { abs(xs[it] - x) }
                    }
                    detectTapGestures { pick(it.x) }
                }
                .pointerInput(sorted) {
                    detectHorizontalDragGestures(onDragStart = { }, onDragEnd = { }, onDragCancel = { }) { change, _ ->
                        val w = size.width.toFloat()
                        val xs = sorted.mapIndexed { i, p -> xOf(i, p, first, spanDays, w, sorted.size, kind) }
                        selected = xs.indices.minByOrNull { abs(xs[it] - change.position.x) }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            fun y(v: Double) = h - ((v - lo) / (hi - lo)).toFloat() * h

            for (i in 0..2) {
                val gy = h * i / 2f
                drawLine(colors.outlineVariant, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            when (kind) {
                ChartKind.LINE -> {
                    if (trend.size > 1) {
                        val path = Path()
                        trend.sortedBy { it.date }.forEachIndexed { i, p ->
                            val px = xOf(0, p, first, spanDays, w, 0, kind)
                            if (i == 0) path.moveTo(px, y(p.value)) else path.lineTo(px, y(p.value))
                        }
                        drawPath(path, colors.primary, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    } else if (trend.isEmpty()) {
                        val path = Path()
                        sorted.forEachIndexed { i, p ->
                            val px = xOf(i, p, first, spanDays, w, sorted.size, kind)
                            if (i == 0) path.moveTo(px, y(p.value)) else path.lineTo(px, y(p.value))
                        }
                        drawPath(path, colors.primary.copy(alpha = 0.55f), style = Stroke(width = 3f, cap = StrokeCap.Round))
                    }
                    sorted.forEachIndexed { i, p ->
                        val isSel = i == selected
                        drawCircle(
                            color = if (isSel) colors.primary else colors.onSurfaceVariant,
                            radius = if (isSel) 11f else 6f,
                            center = Offset(xOf(i, p, first, spanDays, w, sorted.size, kind), y(p.value)),
                        )
                    }
                }
                ChartKind.BAR -> {
                    val slot = w / sorted.size
                    val barW = (slot * 0.62f).coerceAtMost(48f)
                    sorted.forEachIndexed { i, p ->
                        val cx = slot * i + slot / 2f
                        val top = y(p.value)
                        val isSel = i == selected
                        drawRoundRect(
                            color = if (isSel) colors.primary else colors.primary.copy(alpha = 0.45f),
                            topLeft = Offset(cx - barW / 2f, top),
                            size = Size(barW, (h - top).coerceAtLeast(2f)),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ltr(Formatters.date(sorted.first().date)), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                ltr("${Formatters.number(lo + pad.coerceAtMost(hi - lo), 1)} – ${Formatters.number(hi - pad, 1)}"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Text(ltr(Formatters.date(sorted.last().date)), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun xOf(
    index: Int,
    p: ChartPoint,
    first: java.time.LocalDate,
    spanDays: Float,
    width: Float,
    count: Int,
    kind: ChartKind,
): Float = when (kind) {
    ChartKind.LINE -> {
        val inset = 10f
        inset + (ChronoUnit.DAYS.between(first, p.date) / spanDays) * (width - 2 * inset)
    }
    ChartKind.BAR -> {
        val slot = width / count.coerceAtLeast(1)
        slot * index + slot / 2f
    }
}

/** Horizontal bars for a distribution (for example sets per muscle group). */
@Composable
fun DistributionBars(slices: List<DistributionSlice>, valueSuffix: String, modifier: Modifier = Modifier) {
    if (slices.isEmpty()) {
        ChartEmpty("אין עדיין סטים בתקופה שנבחרה.", modifier)
        return
    }
    val colors = MaterialTheme.colorScheme
    val maxFraction = slices.maxOf { it.fraction }.coerceAtLeast(0.01)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { s ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        ltr("${Formatters.number(s.value, 1)} $valueSuffix · ${Formatters.number(s.fraction * 100, 0)}%"),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).background(colors.surfaceContainerHighest, RoundedCornerShape(5.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (s.fraction / maxFraction).toFloat().coerceIn(0.02f, 1f))
                            .height(10.dp)
                            .background(colors.primary, RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
    }
}
