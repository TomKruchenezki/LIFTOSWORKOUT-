package app.liftos.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.ChartPoint
import app.liftos.domain.analytics.ChartSeries
import app.liftos.domain.analytics.ExerciseAnalytics
import app.liftos.domain.analytics.ExerciseOverview
import app.liftos.domain.analytics.PrEvent
import app.liftos.domain.analytics.RecordEntry
import app.liftos.domain.analytics.Period
import app.liftos.domain.analytics.PeriodPreset
import app.liftos.domain.analytics.TrendMetric
import app.liftos.domain.analytics.WeightRepsRow
import app.liftos.domain.progression.DecisionType
import app.liftos.domain.progression.ProgressionEngine
import app.liftos.domain.progression.ProgressionSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ExerciseChart(val labelHe: String) {
    PRIMARY("ביצוע מוביל"),
    TOP_WEIGHT("משקל מקסימלי"),
    VOLUME("נפח"),
    REPS_AT_WEIGHT("חזרות במשקל קבוע"),
}

data class ExerciseControls(
    val period: Period = Period(PeriodPreset.DAYS_90),
    val chart: ExerciseChart = ExerciseChart.PRIMARY,
    val weightKg: Double? = null,
)

data class ExerciseAnalyticsUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val overview: ExerciseOverview? = null,
    val controls: ExerciseControls = ExerciseControls(),
    val chartTitle: String = "",
    val chartPoints: List<ChartPoint> = emptyList(),
    val repeatedWeights: List<WeightRepsRow> = emptyList(),
    val suggestion: ProgressionSuggestion? = null,
    val visibility: ProgressionEngine.Visibility = ProgressionEngine.Visibility.SHOW,
    val incrementKg: Double? = null,
    val records: List<RecordEntry> = emptyList(),
    val recentEvents: List<PrEvent> = emptyList(),
)

class ExerciseAnalyticsViewModel(private val c: AppContainer, private val key: String) : ViewModel() {
    private val controls = MutableStateFlow(ExerciseControls())

    val ui: StateFlow<ExerciseAnalyticsUi> = combine(
        c.analyticsData, c.progression.observeDecisions(), c.progression.observePrefs(), controls,
    ) { data, decisions, prefs, ctl ->
        val today = LocalDate.now(c.zones.zone())
        val overview = ExerciseAnalytics.overview(data.facts, key, today)
        if (overview == null) {
            ExerciseAnalyticsUi(loading = false, missing = true, today = today, controls = ctl)
        } else {
            val range = ctl.period.resolve(today, overview.sessions.firstOrNull()?.date)
            val increment = prefs[key]?.incrementKg
            val suggestion = ProgressionEngine.suggest(key, overview.name, data.facts, data.sessions, today, incrementOverrideKg = increment)
            val repeated = overview.weightTable.filter { it.sessions >= 2 && it.weightKg > 0.0 }
            val weight = ctl.weightKg?.takeIf { w -> repeated.any { it.weightKg == w } } ?: repeated.maxByOrNull { it.sessions }?.weightKg
            val (title, points) = when (ctl.chart) {
                ExerciseChart.PRIMARY -> overview.metric.labelHe to ChartSeries.exerciseMetric(overview.sessions, overview.metric, range)
                ExerciseChart.TOP_WEIGHT -> TrendMetric.TOP_WEIGHT.labelHe to ChartSeries.exerciseMetric(overview.sessions, TrendMetric.TOP_WEIGHT, range)
                ExerciseChart.VOLUME -> "נפח התרגיל באימון" to ChartSeries.exerciseVolume(overview.sessions, range)
                ExerciseChart.REPS_AT_WEIGHT ->
                    "חזרות ב-${weight?.let { app.liftos.domain.logic.Formatters.number(it, 2) } ?: "-"} ק״ג" to
                        (weight?.let { ChartSeries.repsAtWeight(data.facts, key, it, range) } ?: emptyList())
            }
            ExerciseAnalyticsUi(
                loading = false,
                today = today,
                overview = overview,
                controls = ctl.copy(weightKg = weight),
                chartTitle = title,
                chartPoints = points,
                repeatedWeights = repeated,
                suggestion = suggestion,
                visibility = ProgressionEngine.visibility(suggestion, decisions),
                incrementKg = increment,
                records = data.records.currentFor(key),
                recentEvents = data.records.events.filter { it.exerciseKey == key }.sortedByDescending { it.startedAt }.take(5),
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseAnalyticsUi())

    fun setPeriod(p: Period) { controls.value = controls.value.copy(period = p) }

    fun setChart(chart: ExerciseChart) { controls.value = controls.value.copy(chart = chart) }

    fun setWeight(kg: Double?) { controls.value = controls.value.copy(weightKg = kg) }

    fun ignore(suggestion: ProgressionSuggestion) {
        viewModelScope.launch { c.progression.record(suggestion, DecisionType.IGNORED) }
    }

    fun setIncrement(kg: Double?) {
        viewModelScope.launch { c.progression.setIncrement(key, kg) }
    }
}
