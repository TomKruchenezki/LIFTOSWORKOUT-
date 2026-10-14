package app.liftos.ui.photos

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.PhotoAngle
import app.liftos.domain.model.ProgressPhoto
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.DatePickerSheet
import app.liftos.ui.components.DecimalTextField
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.ltr
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotosScreen(container: AppContainer, onBack: () -> Unit, onCompare: (String, String) -> Unit) {
    val vm = appViewModel { PhotosViewModel(container) }
    val photos by vm.photos.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<ProgressPhoto?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar("תמונות התקדמות", onBack = onBack, actions = {
                if ((photos?.size ?: 0) >= 2) {
                    IconButton(onClick = { selectMode = !selectMode; selected = emptyList() }) {
                        Icon(Icons.Rounded.CompareArrows, contentDescription = "השוואת תמונות")
                    }
                }
            })
        },
        floatingActionButton = {
            if (!selectMode) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null) },
                    text = { Text("תמונה חדשה") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        val all = photos
        if (all == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val shown = all.filter { filter == null || it.angle.name == filter }
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (all.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, { Text("הכול") })
                    PhotoAngle.entries.forEach { a ->
                        FilterChip(filter == a.name, { filter = if (filter == a.name) null else a.name }, { Text(a.labelHe) })
                    }
                }
            }
            if (selectMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selected.size < 2) "בחר שתי תמונות להשוואה (${selected.size}/2)" else "נבחרו שתי תמונות",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        enabled = selected.size == 2,
                        onClick = { onCompare(selected[0], selected[1]); selectMode = false; selected = emptyList() },
                    ) { Text("השווה") }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PrivacyNote()
                }
                if (all.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = "עוד אין תמונות התקדמות",
                            subtitle = "צלם או בחר תמונה כדי לעקוב אחרי השינוי הוויזואלי. כדאי לצלם באותה תאורה, מרחק וזווית.",
                            icon = Icons.Rounded.PhotoCamera,
                        )
                    }
                } else if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("אין תמונות בזווית הזו.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(shown, key = { it.id }) { p ->
                    Thumb(
                        vm = vm, photo = p, marked = p.id in selected,
                        onClick = {
                            if (selectMode) {
                                selected = when {
                                    p.id in selected -> selected - p.id
                                    selected.size < 2 -> selected + p.id
                                    else -> listOf(selected[1], p.id)
                                }
                            } else open = p
                        },
                    )
                }
            }
        }
    }

    if (adding) AddPhotoSheet(vm, onDismiss = { adding = false })
    open?.let { p ->
        // The list may have changed (edit / delete): show the current version of the photo.
        val current = photos?.firstOrNull { it.id == p.id }
        if (current == null) open = null else PhotoDetailSheet(vm, current, onDismiss = { open = null })
    }
}

@Composable
private fun PrivacyNote() {
    LiftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text(
            "התמונות נשמרות רק בזיכרון הפרטי של האפליקציה: לא בגלריה ולא ברשת. הן נכללות בגיבוי מלא מסוג ZIP בלבד, ואפשר למחוק אותן בכל עת.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Thumb(vm: PhotosViewModel, photo: ProgressPhoto, marked: Boolean, onClick: () -> Unit) {
    val date = TimeUtils.parseDate(photo.localDate)
    Box(Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        PhotoImage(
            vm.repo, photo, maxEdge = 420, modifier = Modifier.fillMaxSize(),
            contentDescription = "תמונה ${photo.angle.labelHe} ${date?.let { Formatters.date(it) } ?: photo.localDate}",
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(4.dp)) {
            Text(
                (date?.let { Formatters.date(it) } ?: photo.localDate) + " · " + photo.angle.labelHe,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (marked) {
            Icon(
                Icons.Rounded.CheckCircle, contentDescription = "נבחרה",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

// ---- adding -----------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddPhotoSheet(vm: PhotosViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val latestWeight by vm.latestWeight.collectAsStateWithLifecycle()
    var angle by rememberSaveable { mutableStateOf(PhotoAngle.FRONT.name) }
    var weight by remember { mutableStateOf<Double?>(null) }
    var weightTouched by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val effectiveWeight = if (weightTouched) weight else latestWeight

    fun finished(ok: Boolean) {
        busy = false
        if (ok) onDismiss() else Toast.makeText(context, "לא הצלחתי לקרוא את התמונה. נסה שוב.", Toast.LENGTH_LONG).show()
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath
        pendingPath = null
        if (ok && path != null) {
            busy = true
            vm.addFromFile(File(path), PhotoAngle.valueOf(angle), System.currentTimeMillis(), effectiveWeight, notes, ::finished)
        } else if (path != null) {
            File(path).delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            vm.addFromUri(uri, PhotoAngle.valueOf(angle), System.currentTimeMillis(), effectiveWeight, notes, ::finished)
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("תמונת התקדמות חדשה", style = MaterialTheme.typography.titleLarge)
            Text("זווית", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoAngle.entries.forEach { a -> FilterChip(angle == a.name, { angle = a.name }, { Text(a.labelHe) }) }
            }
            DecimalTextField("משקל באותו יום (לא חובה)", effectiveWeight, { weight = it; weightTouched = true }, Modifier.fillMaxWidth(), "ק״ג")
            if (!weightTouched && latestWeight != null) {
                Text("הוצע המשקל האחרון שהזנת. אפשר לשנות או למחוק.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("הערות") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("צלם עכשיו", modifier = Modifier.weight(1f), icon = Icons.Rounded.PhotoCamera, enabled = !busy, onClick = {
                    val (file, uri) = vm.newCameraTarget()
                    pendingPath = file.path
                    try {
                        camera.launch(uri)
                    } catch (e: Exception) {
                        pendingPath = null
                        Toast.makeText(context, "לא נמצאה אפליקציית מצלמה במכשיר.", Toast.LENGTH_LONG).show()
                    }
                })
                SoftButton("מהגלריה", modifier = Modifier.weight(1f), icon = Icons.Rounded.PhotoLibrary, enabled = !busy, onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            if (busy) Text("שומר את התמונה...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "התמונה מועתקת לאחסון הפרטי של האפליקציה ומוקטנת. מידע מיקום ומצלמה מוסר מהקובץ. העותק בגלריה (אם יש) לא נמחק.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- one photo ----------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PhotoDetailSheet(vm: PhotosViewModel, photo: ProgressPhoto, onDismiss: () -> Unit) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    val takenZdt = remember(photo.id) { java.time.Instant.ofEpochMilli(photo.takenAt).atZone(TimeUtils.zone(photo.zoneId)) }
    var angle by remember(photo.id) { mutableStateOf(photo.angle) }
    var weight by remember(photo.id) { mutableStateOf(photo.weightKg) }
    var notes by remember(photo.id) { mutableStateOf(photo.notes) }
    var date by remember(photo.id) { mutableStateOf<LocalDate>(takenZdt.toLocalDate()) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoImage(
                vm.repo, photo, maxEdge = 1600,
                modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
                contentDescription = "תמונת התקדמות ${photo.angle.labelHe}",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoAngle.entries.forEach { a -> FilterChip(angle == a, { angle = a }, { Text(a.labelHe) }) }
            }
            TextButton(onClick = { pickDate = true }) { Text("תאריך: ${Formatters.date(date)}") }
            DecimalTextField("משקל באותו יום", weight, { weight = it }, Modifier.fillMaxWidth(), "ק״ג")
            OutlinedTextField(notes, { notes = it }, label = { Text("הערות") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            BigButton("שמור שינויים", onClick = {
                val at = TimeUtils.toEpochMs(date, takenZdt.hour, takenZdt.minute, zone)
                vm.update(photo.id, angle, at, weight, notes)
                onDismiss()
            })
            SoftButton("מחק תמונה", onClick = { confirmDelete = true })
        }
    }
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }) { date = it }
    if (confirmDelete) {
        ConfirmDialog(
            title = "למחוק את התמונה?",
            text = "הקובץ יימחק מהאחסון הפרטי של האפליקציה ולא ניתן לשחזר אותו (אלא מגיבוי שכולל תמונות).",
            confirmText = "מחק",
            destructive = true,
            onConfirm = { vm.delete(photo.id); confirmDelete = false; onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}
