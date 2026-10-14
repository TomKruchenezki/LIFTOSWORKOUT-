package app.liftos.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.goals.GoalContext
import app.liftos.domain.goals.GoalEngine
import app.liftos.domain.goals.GoalProgress
import app.liftos.domain.model.CustomMetric
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalChange
import app.liftos.domain.model.GoalStatus
import app.liftos.domain.model.GoalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalRow(val goal: Goal, val progress: GoalProgress)

data class GoalsUi(
    val loading: Boolean = true,
    val rows: List<GoalRow> = emptyList(),
    val customMetrics: List<CustomMetric> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val context: GoalContext = GoalContext(LocalDate.now()),
)

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModel(private val c: AppContainer) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)

    val ui: StateFlow<GoalsUi> = combine(
        c.goals.observeAll(), c.analyticsData, c.measurements.observeAll(), c.measurements.observeMetrics(), c.exercises.observeAll(),
    ) { goals, data, measurements, metrics, exercises ->
        val ctx = GoalContext(LocalDate.now(c.zones.zone()), measurements, data.facts, data.sessions)
        GoalsUi(
            loading = false,
            rows = goals.map { GoalRow(it, GoalEngine.progress(it, ctx)) },
            customMetrics = metrics.filter { !it.isArchived },
            exercises = exercises.filter { !it.isArchived },
            context = ctx,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUi())

    /** History of the goal opened in the detail sheet. */
    val changes: StateFlow<List<GoalChange>> = selected.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.goals.observeChanges(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(id: String?) { selected.value = id }

    fun save(goal: Goal, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            if (goal.id.isBlank()) c.goals.create(goal) else c.goals.update(goal)
            c.goalTracker.check()
            onSaved()
        }
    }

    fun setStatus(id: String, status: GoalStatus) {
        viewModelScope.launch { c.goals.setStatus(id, status) }
    }

    fun updateCurrent(id: String, value: Double?) {
        viewModelScope.launch {
            c.goals.updateCurrentValue(id, value)
            c.goalTracker.check()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { c.goals.delete(id) }
    }

    fun suggestedStart(type: GoalType, metricKey: String, ctx: GoalContext): Double? = GoalEngine.suggestedStart(type, metricKey, ctx)
}
