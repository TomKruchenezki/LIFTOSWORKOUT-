package app.liftos.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.data.repo.AnalyticsData
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.ChartPoint
import app.liftos.domain.analytics.ChartSeries
import app.liftos.domain.analytics.DashboardAnalytics
import app.liftos.domain.analytics.DashboardCards
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.ExerciseAnalytics
import app.liftos.domain.analytics.ExerciseListRow
import app.liftos.domain.analytics.Granularity
import app.liftos.domain.analytics.MuscleLoad
import app.liftos.domain.analytics.Period
import app.liftos.domain.analytics.PeriodComparison
import app.liftos.domain.analytics.PeriodPreset
import app.liftos.domain.analytics.VolumeAnalytics
import app.liftos.domain.insights.Insight
import app.liftos.domain.insights.InsightEngine
import app.liftos.domain.model.PlanDetail
import app.liftos.domain.recovery.RecoveryAnalytics
import app.liftos.domain.recovery.RecoveryReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** The charts offered on the analytics tab. */
enum class ChartChoice(val labelHe: String, val barLike: Boolean) {
    WEEKLY_VOLUME("נפח שבועי", true),
    MONTHLY_VOLUME("נפח חודשי", true),
    WEEKLY_SETS("סטי עבודה בשבוע", true),
    WEEKLY_WORKOUTS("אימונים בשבוע", true),
    WORKOUT_VOLUME("נפח לכל אימון", false),
    WORKOUT_DURATION("משך אימון", false),
}

data class AnalyticsControls(
    val period: Period = Period(PeriodPreset.DAYS_90),
    val chart: ChartChoice = ChartChoice.WEEKLY_VOLUME,
    val includeIndirect: Boolean = false,
    val exerciseQuery: String = "",
)

data class AnalyticsUi(
    val loading: Boolean = true,
    val hasData: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val controls: AnalyticsControls = AnalyticsControls(),
    val range: DateRange = DateRange.lastDays(90, LocalDate.now()),
    val cards: DashboardCards? = null,
    val comparison: PeriodComparison? = null,
    val chartPoints: List<ChartPoint> = emptyList(),
    val muscles: List<MuscleLoad> = emptyList(),
    val exercises: List<ExerciseListRow> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val recovery: RecoveryReport? = null,
    val activeGoals: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(private val c: AppContainer) : ViewModel() {
    private val controls = MutableStateFlow(AnalyticsControls())

    private val targetPerWeek: Flow<Int> = c.settings.observe().flatMapLatest { s ->
        if (s.weeklyGoalWorkouts > 0) {
            flowOf(s.weeklyGoalWorkouts)
        } else {
            val id = s.activePlanId
            if (id == null) flowOf(0) else c.plans.observePlanDetail(id).map { d -> d?.let(::planTarget) ?: 0 }
        }
    }.distinctUntilChanged()

    private fun planTarget(d: PlanDetail) = d.plan.targetPerWeek(d.workouts.size)

    val ui: StateFlow<AnalyticsUi> =
        combine(c.analyticsData, targetPerWeek, controls, c.goals.observeAll()) { data, target, ctl, goals ->
            compute(data, target, ctl, goals.count { it.status == app.liftos.domain.model.GoalStatus.ACTIVE })
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUi())

    private fun compute(data: AnalyticsData, target: Int, ctl: AnalyticsControls, activeGoals: Int): AnalyticsUi {
        val today = LocalDate.now(c.zones.zone())
        val range = ctl.period.resolve(today, data.earliest)
        val recovery = RecoveryAnalytics.report(data.facts, data.sessions, today)
        val cards = DashboardAnalytics.cards(data.facts, data.sessions, data.records, today, target)
        val comparison = VolumeAnalytics.compare(data.facts, data.sessions, range)
        val muscles = VolumeAnalytics.muscleLoads(data.facts, range)
        val q = ctl.exerciseQuery.trim()
        val exercises = ExerciseAnalytics.list(data.facts).filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
        return AnalyticsUi(
            loading = false,
            hasData = !data.isEmpty,
            today = today,
            controls = ctl,
            range = range,
            cards = cards,
            comparison = comparison,
            chartPoints = chartPoints(data, ctl.chart, range),
            muscles = muscles,
            exercises = exercises,
            insights = InsightEngine.generate(data.facts, data.sessions, data.records, today, target, recovery),
            recovery = recovery,
            activeGoals = activeGoals,
        )
    }

    private fun chartPoints(data: AnalyticsData, choice: ChartChoice, range: DateRange): List<ChartPoint> = when (choice) {
        ChartChoice.WEEKLY_VOLUME -> bucketPoints(data, range, Granularity.WEEK, ChartSeries.BucketMetric.VOLUME)
        ChartChoice.MONTHLY_VOLUME -> bucketPoints(data, range, Granularity.MONTH, ChartSeries.BucketMetric.VOLUME)
        ChartChoice.WEEKLY_SETS -> bucketPoints(data, range, Granularity.WEEK, ChartSeries.BucketMetric.SETS)
        ChartChoice.WEEKLY_WORKOUTS -> bucketPoints(data, range, Granularity.WEEK, ChartSeries.BucketMetric.WORKOUTS)
        ChartChoice.WORKOUT_VOLUME -> ChartSeries.workoutVolume(data.facts, data.sessions, range)
        ChartChoice.WORKOUT_DURATION -> ChartSeries.workoutDurations(data.sessions, range)
    }

    private fun bucketPoints(data: AnalyticsData, range: DateRange, g: Granularity, metric: ChartSeries.BucketMetric): List<ChartPoint> {
        // Very short ranges still get at least a bucket per week/month so the chart is meaningful.
        val buckets = VolumeAnalytics.buckets(data.facts, data.sessions, range, g)
        return ChartSeries.bucketSeries(buckets, metric, g)
    }

    fun setPeriod(p: Period) = controls.modify { it.copy(period = p) }

    fun setChart(choice: ChartChoice) = controls.modify { it.copy(chart = choice) }

    fun setIncludeIndirect(value: Boolean) = controls.modify { it.copy(includeIndirect = value) }

    fun setExerciseQuery(q: String) = controls.modify { it.copy(exerciseQuery = q) }

    private inline fun MutableStateFlow<AnalyticsControls>.modify(block: (AnalyticsControls) -> AnalyticsControls) {
        value = block(value)
    }
}
