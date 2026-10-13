package app.liftos.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.data.repo.HistoryItem
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.CompletedRef
import app.liftos.domain.logic.NextWorkout
import app.liftos.domain.logic.NextWorkoutResolver
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.PlanDetail
import app.liftos.domain.model.WorkoutPlan
import app.liftos.domain.model.WorkoutSession
import app.liftos.service.WorkoutService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val activePlan: PlanDetail? = null,
    val active: WorkoutSession? = null,
    val next: NextWorkout? = null,
    val nextExerciseCount: Int = 0,
    val nextSetCount: Int = 0,
    val weekCount: Int = 0,
    val weekTarget: Int = 0,
    val monthCount: Int = 0,
    val latestWeight: BodyMeasurement? = null,
    val weightChange7: BodyTrends.WeightChange? = null,
    val weightChange30: BodyTrends.WeightChange? = null,
    val lastWorkout: HistoryItem? = null,
    val plans: List<WorkoutPlan> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    /** Re-evaluated once a minute so "today" and the week counters roll over at midnight. */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(c.zones.zone()))
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val planDetail: Flow<PlanDetail?> = c.settings.observe()
        .map { it.activePlanId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.plans.observePlanDetail(id) }

    private val counts: Flow<Triple<LocalDate, Int, Int>> = today.flatMapLatest { d ->
        val week = TimeUtils.weekRange(d)
        val month = TimeUtils.monthRange(d)
        combine(
            c.workouts.observeCountBetween(week.start.toString(), week.endInclusive.toString()),
            c.workouts.observeCountBetween(month.start.toString(), month.endInclusive.toString()),
        ) { w, m -> Triple(d, w, m) }
    }

    private val refs: Flow<List<CompletedRef>> = today.flatMapLatest { d ->
        c.workouts.observeCompletedRefs(d.minusDays(60).toString()).map { rows ->
            rows.mapNotNull { r -> TimeUtils.parseDate(r.localDate)?.let { CompletedRef(r.plannedWorkoutId, it) } }
        }
    }

    private val part1 = combine(c.settings.observe(), planDetail, c.workouts.observeActive(), c.plans.observePlans()) { s, p, a, plans ->
        listOf(s, p, a, plans)
    }

    private val part2 = combine(counts, refs, c.measurements.observeLatestWeight(), c.measurements.observeAll()) { cnt, r, w, all ->
        listOf(cnt, r, w, all)
    }

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<HomeState> = combine(part1, part2, c.workouts.observeHistory()) { a, b, history ->
        val settings = a[0] as AppSettings
        val plan = a[1] as PlanDetail?
        val active = a[2] as WorkoutSession?
        val plans = a[3] as List<WorkoutPlan>
        val cnt = b[0] as Triple<LocalDate, Int, Int>
        val refs = b[1] as List<CompletedRef>
        val latest = b[2] as BodyMeasurement?
        val all = b[3] as List<BodyMeasurement>

        val next = plan?.let { NextWorkoutResolver.resolve(it.workouts.map { w -> w.workout }, refs, cnt.first) }
        val nextDetail = next?.workout?.let { nw -> plan.workouts.firstOrNull { it.workout.id == nw.id } }
        HomeState(
            loading = false,
            settings = settings,
            activePlan = plan,
            active = active,
            next = next,
            nextExerciseCount = nextDetail?.exercises?.size ?: 0,
            nextSetCount = nextDetail?.exercises?.sumOf { it.targetSets } ?: 0,
            weekCount = cnt.second,
            weekTarget = if (settings.weeklyGoalWorkouts > 0) settings.weeklyGoalWorkouts else (plan?.plan?.targetPerWeek(plan.workouts.size) ?: 0),
            monthCount = cnt.third,
            latestWeight = latest,
            weightChange7 = BodyTrends.weightChange(all, 7),
            weightChange30 = BodyTrends.weightChange(all, 30),
            lastWorkout = history.firstOrNull(),
            plans = plans.filter { !it.isTemplate },
            today = cnt.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun startWorkout(plannedWorkoutId: String, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val id = c.workouts.startFromPlan(plannedWorkoutId)
            WorkoutService.start(c.app)
            onStarted(id)
        }
    }

    fun startBlank(onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val id = c.workouts.startBlank()
            WorkoutService.start(c.app)
            onStarted(id)
        }
    }
}
