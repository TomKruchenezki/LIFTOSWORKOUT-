package app.liftos.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ProgressPhoto
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.ltr
import java.time.temporal.ChronoUnit

private enum class CompareMode(val labelHe: String) {
    SIDE_BY_SIDE("זה לצד זה"),
    SLIDER("סליידר"),
    TOGGLE("לפני / אחרי"),
}

/** Two progress photos, the older one is "before". Photos are only ever read from the private folder. */
@Composable
fun PhotoCompareScreen(container: AppContainer, idA: String, idB: String, onBack: () -> Unit) {
    val vm = appViewModel { PhotosViewModel(container) }
    val photos by vm.photos.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(CompareMode.SIDE_BY_SIDE) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { ScreenTopBar("השוואת תמונות", onBack = onBack) },
    ) { padding ->
        val all = photos
        if (all == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val pair = listOfNotNull(all.firstOrNull { it.id == idA }, all.firstOrNull { it.id == idB }).sortedBy { it.takenAt }
        if (pair.size < 2) {
            Column(Modifier.padding(padding)) {
                EmptyState("אחת התמונות כבר לא קיימת", "חזור לרשימה ובחר שתי תמונות להשוואה.")
            }
            return@Scaffold
        }
        val (before, after) = pair
        ScrollPage(Modifier.padding(padding)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompareMode.entries.forEach { m -> FilterChip(mode == m, { mode = m }, { Text(m.labelHe) }) }
            }
            when (mode) {
                CompareMode.SIDE_BY_SIDE -> SideBySide(vm, before, after)
                CompareMode.SLIDER -> Slider(vm, before, after)
                CompareMode.TOGGLE -> Toggle(vm, before, after)
            }
            Facts(before, after)
        }
    }
}

private fun caption(p: ProgressPhoto): String =
    (TimeUtils.parseDate(p.localDate)?.let { Formatters.date(it) } ?: p.localDate) + " · " + p.angle.labelHe

@Composable
private fun SideBySide(vm: PhotosViewModel, before: ProgressPhoto, after: ProgressPhoto) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("לפני" to before, "אחרי" to after).forEach { (label, p) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                PhotoImage(
                    vm.repo, p, maxEdge = 900,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                    contentDescription = "$label: ${caption(p)}",
                )
                Text(caption(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Drag the divider: the part on its left shows "before", the part on its right shows "after" (always left-to-right). */
@Composable
private fun Slider(vm: PhotosViewModel, before: ProgressPhoto, after: ProgressPhoto) {
    var fraction by remember { mutableStateOf(0.5f) }
    val dividerColor = MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("שמאל: לפני", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("ימין: אחרי", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, delta ->
                            change.consume()
                            fraction = (fraction + delta / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        }
                    },
            ) {
                PhotoImage(vm.repo, after, maxEdge = 1200, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, contentDescription = "אחרי: ${caption(after)}")
                PhotoImage(
                    vm.repo, before, maxEdge = 1200,
                    modifier = Modifier.fillMaxSize().drawWithContent {
                        val x = size.width * fraction
                        clipRect(left = 0f, top = 0f, right = x, bottom = size.height) {
                            this@drawWithContent.drawContent()
                        }
                        drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                    },
                    contentScale = ContentScale.Fit,
                    contentDescription = "לפני: ${caption(before)}",
                )
            }
        }
    }
    Text("גרור על התמונה כדי להזיז את קו ההשוואה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Toggle(vm: PhotosViewModel, before: ProgressPhoto, after: ProgressPhoto) {
    var showAfter by remember { mutableStateOf(false) }
    val shown = if (showAfter) after else before
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (if (showAfter) "אחרי" else "לפני") + " · " + caption(shown),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        )
        PhotoImage(
            vm.repo, shown, maxEdge = 1200,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).clickable { showAfter = !showAfter },
            contentScale = ContentScale.Fit,
            contentDescription = (if (showAfter) "אחרי" else "לפני") + ": " + caption(shown),
        )
        Text("לחץ על התמונה כדי לעבור בין לפני לאחרי.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Facts(before: ProgressPhoto, after: ProgressPhoto) {
    val days = ChronoUnit.DAYS.between(
        TimeUtils.parseDate(before.localDate) ?: return, TimeUtils.parseDate(after.localDate) ?: return,
    )
    LiftCard(Modifier.fillMaxWidth()) {
        Text("בין התמונות עברו ${ltr(days.toString())} ימים.", style = MaterialTheme.typography.bodyMedium)
        val w1 = before.weightKg
        val w2 = after.weightKg
        if (w1 != null && w2 != null) {
            val d = w2 - w1
            val sign = if (d > 0) "+" else if (d < 0) "−" else ""
            Text(
                "משקל: ${ltr(Formatters.number(w1, 1))} ← ${ltr(Formatters.number(w2, 1))} ק״ג (${ltr(sign + Formatters.number(kotlin.math.abs(d), 1))} ק״ג)",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("לא הוזן משקל בשתי התמונות, לכן אין השוואת משקל.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "הבדלי תאורה, זווית, מרחק ושעה ביום משפיעים על המראה. אין כאן ניתוח אוטומטי של הגוף – ההשוואה היא ויזואלית בלבד.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
