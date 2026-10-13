package app.liftos.ui.workout

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.data.repo.ToggleResult
import app.liftos.di.AppContainer
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.VolumeCalculator
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.SetType
import app.liftos.domain.model.WorkoutDetail
import app.liftos.service.Notifications
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.ConfirmDialog
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ReorderEntry
import app.liftos.ui.components.ReorderSheet
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.TextInputDialog
import app.liftos.ui.components.appViewModel
import app.liftos.ui.library.ExercisePickerSheet
import app.liftos.ui.util.ltr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface PickerMode {
    data object Add : PickerMode
    data class Replace(val sessionExerciseId: String, val suggested: List<String>) : PickerMode
}

/**
 * The live workout screen. The same screen edits a finished workout (history correction):
 * rest timer, "finish" and the foreground service only apply while the session is active.
 */
@Composable
fun WorkoutScreen(
    container: AppContainer,
    settings: AppSettings,
    sessionId: String,
    onClose: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val vm = appViewModel(key = "workout-$sessionId") { WorkoutViewModel(container, sessionId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val previous by vm.previous.collectAsStateWithLifecycle()
    when (val state = ui) {
        WorkoutUi.Loading -> LoadingBox()
        WorkoutUi.NotFound -> LaunchedEffect(Unit) { onClose() }
        is WorkoutUi.Ready -> WorkoutContent(container, vm, state.detail, previous, settings, onClose, onFinished, onOpenExercise)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutContent(
    container: AppContainer,
    vm: WorkoutViewModel,
    detail: WorkoutDetail,
    previous: Map<String, app.liftos.domain.logic.PreviousPerformance>,
    settings: AppSettings,
    onClose: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val session = detail.session
    val isActive = session.isActive
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager: FocusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var focused by remember { mutableStateOf<FocusedField?>(null) }
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var showFinish by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showReorder by remember { mutableStateOf(false) }
    var skipTarget by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var askNotifications by remember { mutableStateOf(false) }

    // Keep the screen awake during a live workout (setting).
    DisposableEffect(isActive, settings.keepScreenOn) {
        view.keepScreenOn = isActive && settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // One-time rationale for the notification permission (Android 13+), used for "rest is over" alerts.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(isActive) {
        if (isActive && Build.VERSION.SDK_INT >= 33 && !Notifications.canPostNotifications(context) &&
            !container.devicePrefs.notificationPermissionAsked()
        ) {
            askNotifications = true
        }
    }

    val totals = VolumeCalculator.totals(detail)
    val completedSets = totals.workingSets + totals.warmupSets
    val incompleteSets = detail.exercises.sumOf { ex -> ex.sets.count { !it.isCompleted } }

    val currentDetail = androidx.compose.runtime.rememberUpdatedState(detail)
    val actions = remember(vm) {
        ExerciseActions(
            setWeight = vm::setWeight,
            setReps = vm::setReps,
            setDuration = vm::setDuration,
            setRir = vm::setRir,
            setRpe = vm::setRpe,
            toggle = { id ->
                vm.toggleSet(id) { result ->
                    when (result) {
                        is ToggleResult.Completed -> if (container.settingsSnapshot().vibrateOnSetComplete) {
                            view.performHapticFeedback(
                                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
                            )
                        }
                        ToggleResult.Rejected -> scope.launch { snackbar.showSnackbar("הזן חזרות (או שניות) לפני סימון הסט") }
                        ToggleResult.Uncompleted -> Unit
                    }
                }
            },
            addSet = { id, type -> vm.addSet(id, type) },
            deleteSet = vm::deleteSet,
            changeType = vm::changeSetType,
            setFailure = vm::setFailure,
            setSetNotes = vm::setSetNotes,
            setExerciseNotes = vm::setExerciseNotes,
            replace = { id ->
                val ex = currentDetail.value.exercises.firstOrNull { it.exercise.id == id }?.exercise
                scope.launch {
                    val alts = ex?.exerciseId?.let { container.exercises.get(it)?.alternativeIds }.orEmpty()
                    picker = PickerMode.Replace(id, alts)
                }
            },
            remove = vm::removeExercise,
            skip = { id -> skipTarget = id },
            unskip = vm::unskip,
            openInfo = onOpenExercise,
            onFocus = { field, has -> if (has) focused = field else if (focused == field) focused = null },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        ElapsedText(session.startedAt, session.endedAt, isActive)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "חזרה") }
                },
                actions = {
                    if (isActive) {
                        FilledTonalButton(onClick = { focusManager.clearFocus(); showFinish = true }) { Text("סיים") }
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "עוד") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("הוסף תרגיל") }, onClick = { menu = false; picker = PickerMode.Add })
                            DropdownMenuItem(text = { Text("שנה סדר תרגילים") }, onClick = { menu = false; showReorder = true })
                            if (isActive) {
                                DropdownMenuItem(text = { Text("בטל את האימון", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; showDiscard = true })
                            } else {
                                DropdownMenuItem(text = { Text("מחק את האימון", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; showDelete = true })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                val rest = session.rest
                if (isActive && rest != null) {
                    RestBar(
                        rest = rest,
                        onPause = vm::pauseRest,
                        onResume = vm::resumeRest,
                        onAdd = vm::addRest,
                        onRestart = { vm.startRest((rest.totalMs / 1000).toInt(), rest.label) },
                        onSkip = vm::skipRest,
                        onExpired = vm::finishRestFromUi,
                    )
                }
                focused?.let { f ->
                    FieldToolbar(field = f, onAdjust = { vm.adjust(f, it) }, onDone = { focusManager.clearFocus() })
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (detail.exercises.isEmpty()) {
                item {
                    Text(
                        "האימון עדיין ריק. הוסף תרגיל כדי להתחיל לתעד.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(detail.exercises, key = { it.exercise.id }) { item ->
                ExerciseCard(
                    item = item,
                    previous = item.exercise.exerciseId?.let { previous[it] },
                    effortMode = settings.effortMode,
                    actions = actions,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("הוסף תרגיל", onClick = { picker = PickerMode.Add }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                    SoftButton("שנה סדר תרגילים", onClick = { showReorder = true }, icon = Icons.Rounded.SwapHoriz, modifier = Modifier.fillMaxWidth())
                    SessionDetailsCard(
                        session = session,
                        editableTimes = !isActive,
                        onNotes = vm::setNotes,
                        onRatings = vm::setRatings,
                        onTitle = vm::setTitle,
                        onTimes = vm::setTimes,
                    )
                    if (isActive) {
                        BigButton("סיים אימון", onClick = { focusManager.clearFocus(); showFinish = true })
                    } else {
                        BigButton("סיום עריכה", onClick = onClose)
                    }
                }
            }
        }
    }

    // ---- dialogs and sheets -----------------------------------------------------------------------------------

    picker?.let { mode ->
        ExercisePickerSheet(
            container = container,
            title = if (mode is PickerMode.Replace) "החלפת תרגיל" else "הוספת תרגיל לאימון",
            suggestedIds = (mode as? PickerMode.Replace)?.suggested.orEmpty(),
            onPick = { ex ->
                when (mode) {
                    PickerMode.Add -> vm.addExercise(ex)
                    is PickerMode.Replace -> vm.replaceExercise(mode.sessionExerciseId, ex)
                }
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    if (showReorder) {
        ReorderSheet(
            title = "סדר התרגילים",
            entries = detail.exercises.map { ReorderEntry(it.exercise.id, it.exercise.nameHe, it.exercise.nameEn) },
            onReordered = vm::reorder,
            onDismiss = { showReorder = false },
        )
    }

    skipTarget?.let { id ->
        TextInputDialog(
            title = "דילוג על התרגיל",
            label = "סיבה (לא חובה)",
            initial = "",
            confirmText = "דלג",
            onConfirm = { vm.skip(id, it); skipTarget = null },
            onDismiss = { skipTarget = null },
        )
    }

    if (showFinish) {
        FinishDialog(
            completedSets = completedSets,
            incompleteSets = incompleteSets,
            volumeKg = totals.volumeKg,
            onFinish = {
                showFinish = false
                vm.finish { onFinished(session.id) }
            },
            onDiscard = { showFinish = false; showDiscard = true },
            onDismiss = { showFinish = false },
        )
    }
    if (showDiscard) {
        // The screen closes itself when the workout disappears from the database (see WorkoutUi.NotFound).
        ConfirmDialog(
            title = "לבטל את האימון?",
            text = "כל מה שנרשם באימון הזה יימחק ולא יישמר בהיסטוריה.",
            confirmText = "בטל אימון",
            destructive = true,
            dismissText = "חזור",
            onConfirm = { showDiscard = false; vm.discard { } },
            onDismiss = { showDiscard = false },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "למחוק את האימון?",
            text = "האימון, כל התרגילים והסטים שלו יימחקו לצמיתות. מומלץ לוודא שיש גיבוי עדכני.",
            confirmText = "מחק",
            destructive = true,
            onConfirm = { showDelete = false; vm.deleteCompleted { } },
            onDismiss = { showDelete = false },
        )
    }
    if (askNotifications) {
        AlertDialog(
            onDismissRequest = { askNotifications = false },
            title = { Text("התראת סיום מנוחה") },
            text = {
                Text("כדי לקבל התראה (ורטט) כשזמן המנוחה מסתיים גם כשהמסך כבוי או כשאתה באפליקציה אחרת, LiftOS צריכה הרשאה להצגת התראות. ההרשאה משמשת רק לטיימר האימון.")
            },
            confirmButton = {
                TextButton(onClick = {
                    askNotifications = false
                    scope.launch { container.devicePrefs.setNotificationPermissionAsked(true) }
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("אפשר") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askNotifications = false
                    scope.launch { container.devicePrefs.setNotificationPermissionAsked(true) }
                }) { Text("לא עכשיו") }
            },
        )
    }
}

@Composable
private fun ElapsedText(startedAt: Long, endedAt: Long?, active: Boolean) {
    val elapsed by produceState(0L, startedAt, endedAt, active) {
        while (true) {
            value = ((if (active) System.currentTimeMillis() else endedAt ?: startedAt) - startedAt).coerceAtLeast(0)
            if (!active) break
            delay(1000)
        }
    }
    Text(
        (if (active) "זמן אימון " else "משך ") + ltr(Formatters.duration(elapsed)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FinishDialog(
    completedSets: Int,
    incompleteSets: Int,
    volumeKg: Double,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (completedSets == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("לא סומן אף סט") },
            text = { Text("לא תועד אף סט באימון הזה. אפשר להמשיך להתאמן או לבטל את האימון.") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("בטל אימון", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("המשך להתאמן") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("לסיים את האימון?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("הושלמו $completedSets סטים · נפח ${ltr(Formatters.thousands(volumeKg))} ק״ג-חזרות")
                if (incompleteSets > 0) {
                    Text(
                        "$incompleteSets סטים לא סומנו כבוצעו והם יימחקו מהאימון.",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onFinish) { Text("סיים ושמור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("המשך להתאמן") } },
    )
}
