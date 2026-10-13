package app.liftos.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.data.repo.HistoryItem
import app.liftos.di.AppContainer
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.PlanDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryEntry {
    val sortKey: Long
    val localDate: String

    data class Workout(val item: HistoryItem) : HistoryEntry {
        override val sortKey get() = item.session.startedAt
        override val localDate get() = item.session.localDate
    }

    data class Activity(val event: ActivityEvent) : HistoryEntry {
        override val sortKey get() = event.startsAt
        override val localDate get() = event.localDate
    }
}

data class HistoryState(
    val loading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
    val activePlan: PlanDetail? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val c: AppContainer) : ViewModel() {

    private val plan = c.settings.observe().map { it.activePlanId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.plans.observePlanDetail(id) }

    val state: StateFlow<HistoryState> = combine(c.workouts.observeHistory(), c.activities.observeAll(), plan) { w, a, p ->
        val entries = (w.map { HistoryEntry.Workout(it) } + a.map { HistoryEntry.Activity(it) }).sortedByDescending { it.sortKey }
        HistoryState(false, entries, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun createPast(plannedWorkoutId: String?, title: String, startedAt: Long, durationMin: Int, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(c.workouts.createPastWorkout(plannedWorkoutId, title, startedAt, durationMin)) }
    }

    fun deleteWorkout(id: String) = viewModelScope.launch { c.workouts.deleteSession(id) }
    fun saveEvent(e: ActivityEvent) = viewModelScope.launch { c.activities.save(e) }
    fun deleteEvent(id: String) = viewModelScope.launch { c.activities.delete(id) }
}
