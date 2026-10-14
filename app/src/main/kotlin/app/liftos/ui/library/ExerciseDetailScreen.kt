package app.liftos.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.library.YouTube
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.TrackingType
import app.liftos.ui.components.InfoChip
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.util.ShareHelper
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    container: AppContainer,
    exerciseId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenOther: (String) -> Unit,
) {
    val exercise by container.exercises.observe(exerciseId).collectAsStateWithLifecycle(initialValue = null)
    val all by container.exercises.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(all) { if (all.isNotEmpty()) loaded = true }

    val ex: Exercise? = exercise
    if (ex == null) {
        if (loaded) LaunchedEffect(Unit) { onBack() } else LoadingBox()
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar(ex.nameHe, onBack = onBack, actions = {
                if (!ex.isBuiltIn) {
                    IconButton(onClick = { onEdit(ex.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "ערוך") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "מחק") }
                }
            })
        },
    ) { padding ->
        ScrollPage(Modifier.padding(padding)) {
            LiftCard(Modifier.fillMaxWidth()) {
                Text(ex.nameHe, style = MaterialTheme.typography.headlineMedium)
                Text(ex.nameEn, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(ex.primaryMuscle.labelHe)
                    ex.secondaryMuscles.forEach { InfoChip(it.labelHe) }
                    InfoChip(ex.equipment.labelHe)
                    InfoChip(ex.type.labelHe)
                    InfoChip(ex.difficulty.labelHe)
                    if (ex.isUnilateral) InfoChip("חד-צדדי")
                    if (ex.trackingType == TrackingType.DURATION) InfoChip("נמדד בשניות")
                }
                if (ex.isArchived) Text("התרגיל בארכיון ולא מוצג בבחירת תרגילים.", color = MaterialTheme.colorScheme.tertiary)
            }

            if (ex.description.isNotBlank()) Text(ex.description, style = MaterialTheme.typography.bodyLarge)

            // Video: a direct link only if the user supplied one; otherwise an honest YouTube *search*.
            val direct = ex.videoUrl?.takeIf { YouTube.isValidWebUrl(it) }
            if (direct != null) {
                BigButton("צפה בסרטון הדגמה", onClick = { ShareHelper.openLink(context, direct) }, icon = Icons.Rounded.PlayArrow)
            } else {
                SoftButton("חפש הדגמה ביוטיוב", onClick = { ShareHelper.openLink(context, YouTube.searchUrl(ex.nameEn.ifBlank { ex.nameHe })) }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())
                Text(
                    "זהו קישור לחיפוש ביוטיוב, לא סרטון מאומת. תרגיל אישי אפשר לשייך לסרטון ספציפי בעריכה.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ex.instructions.isNotEmpty()) {
                SectionTitle("אופן ביצוע")
                LiftCard(Modifier.fillMaxWidth()) {
                    ex.instructions.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                            Text(step, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (ex.mistakes.isNotEmpty()) {
                SectionTitle("טעויות נפוצות")
                LiftCard(Modifier.fillMaxWidth()) { ex.mistakes.forEach { Bullet(it) } }
            }
            if (ex.safety.isNotEmpty()) {
                SectionTitle("דגשי בטיחות")
                LiftCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.tertiaryContainer) {
                    ex.safety.forEach { Bullet(it, MaterialTheme.colorScheme.onTertiaryContainer) }
                }
            }
            val alternatives = remember(ex, all) { all.filter { it.id in ex.alternativeIds } }
            if (alternatives.isNotEmpty()) {
                SectionTitle("תרגילים חלופיים")
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    alternatives.forEach { alt -> AssistChip(onClick = { onOpenOther(alt.id) }, label = { Text(alt.nameHe) }) }
                }
            }
            if (ex.isBuiltIn) {
                SoftButton("צור עותק אישי לעריכה", onClick = {
                    scope.launch { container.exercises.duplicateAsCustom(ex.id)?.let(onEdit) }
                }, modifier = Modifier.fillMaxWidth())
            } else if (ex.isArchived) {
                SoftButton("שחזר מהארכיון", onClick = { scope.launch { container.exercises.restore(ex.id) } }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                "המידע בספרייה כללי ואינו תחליף לייעוץ מקצועי. עצור אם מופיע כאב חד.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "למחוק את התרגיל?",
            text = "אם התרגיל משמש בתוכנית או בהיסטוריה הוא יועבר לארכיון ולא יימחק, כדי שהנתונים הקיימים לא ייפגעו.",
            confirmText = "מחק",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                // When it was really deleted the exercise flow turns null and this screen closes itself.
                scope.launch { container.exercises.delete(ex.id) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Bullet(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = color)
        Text(text, Modifier.weight(1f), color = color)
    }
}
