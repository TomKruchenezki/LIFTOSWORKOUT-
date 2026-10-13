package app.liftos.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.liftos.di.AppContainer
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.ui.components.BigButton
import app.liftos.ui.components.EmptyState
import app.liftos.ui.components.LiftCard
import app.liftos.ui.components.LoadingBox
import app.liftos.ui.components.ScreenTopBar
import app.liftos.ui.components.ScrollPage
import app.liftos.ui.components.SectionTitle
import app.liftos.ui.components.SoftButton
import app.liftos.ui.components.StatTile
import app.liftos.ui.components.appViewModel
import app.liftos.ui.util.dateLine
import app.liftos.ui.util.ltr
import app.liftos.ui.util.nextLabel
import app.liftos.ui.util.signedKg
import app.liftos.ui.util.kg
import kotlinx.coroutines.delay

class HomeNav(
    val openWorkout: (String) -> Unit,
    val openSummary: (String) -> Unit,
    val openPlan: (String) -> Unit,
    val openPlans: () -> Unit,
    val openBody: () -> Unit,
    val openHistory: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun HomeScreen(container: AppContainer, nav: HomeNav) {
    val vm = appViewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var pickOther by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            ScreenTopBar(title = "LiftOS", actions = {
                IconButton(onClick = nav.openSettings) { Icon(Icons.Rounded.Settings, contentDescription = "הגדרות") }
            })
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        ScrollPage(Modifier.padding(padding)) {
            val active = state.active
            if (active != null) {
                ActiveCard(active.title, active.startedAt, onContinue = { nav.openWorkout(active.id) })
            } else {
                NextCard(
                    state = state,
                    onStart = { id -> vm.startWorkout(id, nav.openWorkout) },
                    onPickOther = { pickOther = true },
                    onOpenPlans = nav.openPlans,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = if (state.weekTarget > 0) ltr("${state.weekCount}/${state.weekTarget}") else state.weekCount.toString(),
                    label = "אימונים השבוע",
                    modifier = Modifier.weight(1f),
                    accent = state.weekTarget > 0 && state.weekCount >= state.weekTarget,
                )
                StatTile(value = state.monthCount.toString(), label = "אימונים החודש", modifier = Modifier.weight(1f))
            }

            WeightCard(state, onClick = nav.openBody)
            state.lastWorkout?.let { last ->
                LiftCard(onClick = { nav.openSummary(last.session.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("אימון אחרון", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(last.session.title, style = MaterialTheme.typography.titleLarge)
                    val date = TimeUtils.parseDate(last.session.localDate)
                    val dur = last.session.durationMs?.let { " · ${Formatters.minutesHe(it)}" }.orEmpty()
                    Text(
                        (date?.let { dateLine(it) } ?: last.session.localDate) + dur,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "נפח ${ltr(Formatters.thousands(last.volumeKg))} ק״ג-חזרות · ${last.workingSets} סטי עבודה",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.activePlan?.plan?.goal?.takeIf { it.isNotBlank() }?.let { goal ->
                LiftCard(modifier = Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.primaryContainer) {
                    Text("מטרה נוכחית", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(goal, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            SectionTitle("גישה מהירה לתוכניות", trailing = {
                TextButton(onClick = nav.openPlans) { Text("כל התוכניות") }
            })
            if (state.plans.isEmpty()) {
                Text("עוד אין תוכניות. אפשר ליצור תוכנית חדשה בלשונית תוכניות.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(0.dp)) {
                    items(state.plans, key = { it.id }) { plan ->
                        AssistChip(
                            onClick = { nav.openPlan(plan.id) },
                            label = { Text(plan.name) },
                            leadingIcon = { Icon(Icons.Rounded.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (state.active == null) {
                SoftButton("אימון חופשי (ללא תוכנית)", onClick = { vm.startBlank(nav.openWorkout) }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (pickOther) {
        val workouts = state.activePlan?.workouts.orEmpty()
        AlertDialog(
            onDismissRequest = { pickOther = false },
            title = { Text("בחר אימון להתחלה") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    workouts.forEach { w ->
                        TextButton(
                            onClick = {
                                pickOther = false
                                vm.startWorkout(w.workout.id, nav.openWorkout)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${w.workout.name} · ${w.exercises.size} תרגילים", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickOther = false }) { Text("סגור") } },
        )
    }
}

@Composable
private fun ActiveCard(title: String, startedAt: Long, onContinue: () -> Unit) {
    val elapsed by produceState(0L, startedAt) {
        while (true) {
            value = System.currentTimeMillis() - startedAt
            delay(1000)
        }
    }
    LiftCard(modifier = Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.primaryContainer) {
        Text("אימון פעיל", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            ltr(Formatters.duration(elapsed)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        BigButton("המשך אימון", onClick = onContinue, icon = Icons.Rounded.PlayArrow)
    }
}

@Composable
private fun NextCard(
    state: HomeState,
    onStart: (String) -> Unit,
    onPickOther: () -> Unit,
    onOpenPlans: () -> Unit,
) {
    val next = state.next
    val workout = next?.workout
    if (workout == null) {
        LiftCard(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                title = "אין תוכנית פעילה",
                subtitle = "צור תוכנית אימונים או בחר תוכנית קיימת כדי לראות כאן את האימון הבא.",
                icon = Icons.Rounded.FitnessCenter,
                action = { SoftButton("לתוכניות", onClick = onOpenPlans) },
            )
        }
        return
    }
    LiftCard(modifier = Modifier.fillMaxWidth()) {
        Text("האימון הבא · ${nextLabel(next, state.today)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(workout.name, style = MaterialTheme.typography.headlineMedium)
        if (workout.description.isNotBlank()) {
            Text(workout.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${state.nextExerciseCount} תרגילים · ${state.nextSetCount} סטים",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        BigButton("התחל אימון", onClick = { onStart(workout.id) }, icon = Icons.Rounded.PlayArrow)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onPickOther) { Text("בחר אימון אחר") }
        }
    }
}

@Composable
private fun WeightCard(state: HomeState, onClick: () -> Unit) {
    LiftCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("משקל גוף", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val latest = state.latestWeight
        if (latest?.weightKg == null) {
            Text("עוד לא תועד משקל", style = MaterialTheme.typography.titleMedium)
            Text("לחץ כדי להוסיף מדידה", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@LiftCard
        }
        Text(kg(latest.weightKg), style = MaterialTheme.typography.headlineMedium)
        val date = TimeUtils.parseDate(latest.localDate)
        Text(date?.let { dateLine(it) } ?: latest.localDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val c7 = state.weightChange7
        val c30 = state.weightChange30
        if (c7 != null || c30 != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                c7?.let { Text("7 ימים: ${signedKg(it.deltaKg)}", style = MaterialTheme.typography.bodyMedium) }
                c30?.let { Text("30 ימים: ${signedKg(it.deltaKg)}", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
