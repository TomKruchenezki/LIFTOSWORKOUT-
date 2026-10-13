package app.liftos.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.liftos.di.AppContainer
import app.liftos.domain.logic.Formatters
import app.liftos.domain.model.AppSettings
import app.liftos.service.WorkoutService
import app.liftos.ui.body.BodyScreen
import app.liftos.ui.history.HistoryScreen
import app.liftos.ui.home.HomeNav
import app.liftos.ui.home.HomeScreen
import app.liftos.ui.library.ExerciseDetailScreen
import app.liftos.ui.library.ExerciseEditScreen
import app.liftos.ui.library.LibraryScreen
import app.liftos.ui.plans.PlanDetailScreen
import app.liftos.ui.plans.PlanWorkoutScreen
import app.liftos.ui.plans.PlansScreen
import app.liftos.ui.settings.SettingsScreen
import app.liftos.ui.util.ltr
import app.liftos.ui.workout.SummaryScreen
import app.liftos.ui.workout.WorkoutScreen
import kotlinx.coroutines.delay

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "בית", Icons.Rounded.Home),
    Tab(Routes.PLANS, "תוכניות", Icons.Rounded.CalendarMonth),
    Tab(Routes.LIBRARY, "תרגילים", Icons.Rounded.FitnessCenter),
    Tab(Routes.HISTORY, "יומן", Icons.Rounded.History),
    Tab(Routes.BODY, "גוף", Icons.Rounded.MonitorWeight),
)

@Composable
fun LiftOsRoot(container: AppContainer, settings: AppSettings) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val active by container.workouts.observeActive().collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    // The foreground service exists exactly as long as a workout is active (also after process death).
    LaunchedEffect(active?.id) {
        if (active != null) WorkoutService.start(context)
    }

    val showTabs = route in Routes.topLevel
    val onWorkoutScreen = route == Routes.WORKOUT

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            Column {
                val a = active
                if (a != null && !onWorkoutScreen) {
                    ActiveBanner(title = a.title, startedAt = a.startedAt, onClick = { nav.navigate(Routes.workout(a.id)) })
                }
                if (showTabs) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { navigateTab(nav, tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container,
                    HomeNav(
                        openWorkout = { nav.navigate(Routes.workout(it)) },
                        openSummary = { nav.navigate(Routes.summary(it)) },
                        openPlan = { nav.navigate(Routes.plan(it)) },
                        openPlans = { navigateTab(nav, Routes.PLANS) },
                        openBody = { navigateTab(nav, Routes.BODY) },
                        openHistory = { navigateTab(nav, Routes.HISTORY) },
                        openSettings = { nav.navigate(Routes.SETTINGS) },
                    ),
                )
            }
            composable(Routes.PLANS) {
                PlansScreen(container, onOpenPlan = { nav.navigate(Routes.plan(it)) }, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.PLAN) { entry ->
                PlanDetailScreen(
                    container,
                    planId = entry.arguments?.getString("planId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenWorkout = { nav.navigate(Routes.planWorkout(it)) },
                )
            }
            composable(Routes.PLAN_WORKOUT) { entry ->
                PlanWorkoutScreen(
                    container,
                    workoutId = entry.arguments?.getString("workoutId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onStarted = { sessionId -> nav.navigate(Routes.workout(sessionId)) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    container,
                    onOpen = { nav.navigate(Routes.exercise(it)) },
                    onCreate = { nav.navigate(Routes.exerciseEdit(Routes.NEW)) },
                )
            }
            composable(Routes.EXERCISE) { entry ->
                ExerciseDetailScreen(
                    container,
                    exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.exerciseEdit(it)) },
                    onOpenOther = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.EXERCISE_EDIT) { entry ->
                ExerciseEditScreen(
                    container,
                    exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.popBackStack()
                        nav.navigate(Routes.exercise(id))
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    container,
                    onOpenSession = { nav.navigate(Routes.summary(it)) },
                    onEditSession = { nav.navigate(Routes.workout(it)) },
                )
            }
            composable(Routes.BODY) { BodyScreen(container) }
            composable(Routes.WORKOUT) { entry ->
                WorkoutScreen(
                    container,
                    settings,
                    sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                    onClose = { nav.popBackStack() },
                    onFinished = { id ->
                        nav.popBackStack()
                        nav.navigate(Routes.summary(id))
                    },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.SUMMARY) { entry ->
                SummaryScreen(
                    container,
                    sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.workout(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(container, settings, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun navigateTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Always-visible shortcut back to a running workout (shown above the tab bar). */
@Composable
private fun ActiveBanner(title: String, startedAt: Long, onClick: () -> Unit) {
    val elapsed by produceState(0L, startedAt) {
        while (true) {
            value = System.currentTimeMillis() - startedAt
            delay(1000)
        }
    }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Text("המשך אימון · $title", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(ltr(Formatters.duration(elapsed)), style = MaterialTheme.typography.titleSmall)
        }
    }
}
