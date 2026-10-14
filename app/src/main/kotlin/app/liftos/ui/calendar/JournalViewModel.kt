package app.liftos.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.calendar.CalendarBuilder
import app.liftos.domain.calendar.CalendarItem
import app.liftos.domain.calendar.ScheduledWorkout
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.OverrideAction
import app.liftos.domain.model.PlanDetail
import app.liftos.service.WorkoutService
import app.liftos.ui.history.HistoryEntry
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
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class JournalView(val labelHe: String) {
    MONTH("חודש"),
    WEEK("שבוע"),
    DAY("יום"),
    LIST("רשימה"),
    HISTORY("היסטוריה"),
}

data class JournalControls(
    val view: JournalView = JournalView.MONTH,
    /** The month / week / day being shown. */
    val anchor: LocalDate = LocalDate.now(),
    /** The day highlighted in the month and week views. */
    val selected: LocalDate = LocalDate.now(),
)

data class JournalUi(
    val loading: Boolean = true,
    val controls: JournalControls = JournalControls(),
    val today: LocalDate = LocalDate.now(),
    val range: DateRange = DateRange.weekOf(LocalDate.now()),
    val items: List<CalendarItem> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val plan: PlanDetail? = null,
    val events: List<ActivityEvent> = emptyList(),
    val recentSessions: List<SessionFact> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(private val c: AppContainer) : ViewModel() {
    private val controls = MutableStateFlow(JournalControls(anchor = LocalDate.now(c.zones.zone()), selected = LocalDate.now(c.zones.zone())))

    private val plan: Flow<PlanDetail?> = c.settings.observe().map { it.activePlanId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.plans.observePlanDetail(id) }

    private data class Sources(
        val sessions: List<SessionFact>,
        val events: List<ActivityEvent>,
        val overrides: List<app.liftos.domain.model.PlannedOverride>,
        val plan: PlanDetail?,
        val history: List<app.liftos.data.repo.HistoryItem>,
    )

    private val sources: Flow<Sources> = combine(
        c.analytics.observeSessions(), c.activities.observeAll(), c.activities.observeOverrides(), plan, c.workouts.observeHistory(),
    ) { s, e, o, p, h -> Sources(s, e, o, p, h) }

    val ui: StateFlow<JournalUi> = combine(sources, controls) { src, ctl ->
        val zone = c.zones.zone()
        val today = LocalDate.now(zone)
        val range = rangeFor(ctl, today)
        val schedule = src.plan?.workouts.orEmpty().mapNotNull { w ->
            w.workout.weekday?.let { ScheduledWorkout(w.workout.id, w.workout.name, it) }
        }
        val scheduleStart = src.plan?.plan?.createdAt?.takeIf { it > 0 }?.let { TimeUtils.localDate(it, zone) }
        val items = if (ctl.view == JournalView.HISTORY) emptyList() else
            CalendarBuilder.build(range, today, schedule, scheduleStart, src.sessions, src.events, src.overrides)
        val history = (src.history.map { HistoryEntry.Workout(it) } + src.events.map { HistoryEntry.Activity(it) })
            .sortedByDescending { it.sortKey }
        JournalUi(
            loading = false, controls = ctl, today = today, range = range, items = items, history = history,
            plan = src.plan, events = src.events, recentSessions = src.sessions.sortedByDescending { it.startedAt }.take(60),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUi())

    private fun rangeFor(ctl: JournalControls, today: LocalDate): DateRange = when (ctl.view) {
        JournalView.MONTH -> {
            val start = TimeUtils.weekStart(ctl.anchor.withDayOfMonth(1))
            DateRange(start, start.plusDays(41))
        }
        JournalView.WEEK -> DateRange.weekOf(ctl.anchor)
        JournalView.DAY -> DateRange(ctl.anchor, ctl.anchor)
        JournalView.LIST -> DateRange(today.minusDays(14), today.plusDays(90))
        JournalView.HISTORY -> DateRange(today, today)
    }

    // ---- navigation inside the calendar -------------------------------------------------------------------

    fun setView(v: JournalView) { controls.value = controls.value.copy(view = v) }

    fun select(date: LocalDate) { controls.value = controls.value.copy(selected = date, anchor = if (controls.value.view == JournalView.MONTH) controls.value.anchor else date) }

    fun openDay(date: LocalDate) { controls.value = controls.value.copy(view = JournalView.DAY, anchor = date, selected = date) }

    fun today() {
        val t = LocalDate.now(c.zones.zone())
        controls.value = controls.value.copy(anchor = t, selected = t)
    }

    fun step(direction: Int) {
        val ctl = controls.value
        val next = when (ctl.view) {
            JournalView.MONTH -> ctl.anchor.plusMonths(direction.toLong())
            JournalView.WEEK -> ctl.anchor.plusWeeks(direction.toLong())
            JournalView.DAY -> ctl.anchor.plusDays(direction.toLong())
            else -> ctl.anchor
        }
        controls.value = ctl.copy(anchor = next, selected = if (ctl.view == JournalView.MONTH) next.withDayOfMonth(1) else next)
    }

    // ---- planned workouts ------------------------------------------------------------------------------------

    fun startWorkout(plannedWorkoutId: String, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val id = c.workouts.startFromPlan(plannedWorkoutId)
            WorkoutService.start(c.app)
            onStarted(id)
        }
    }

    fun moveWorkout(workoutId: String, original: LocalDate, newDate: LocalDate) {
        viewModelScope.launch { c.activities.moveWorkout(workoutId, original, newDate) }
    }

    fun markWorkout(workoutId: String, original: LocalDate, action: OverrideAction) {
        viewModelScope.launch { c.activities.markWorkout(workoutId, original, action) }
    }

    fun clearOverride(workoutId: String, original: LocalDate) {
        viewModelScope.launch { c.activities.clearOverride(workoutId, original) }
    }

    // ---- events --------------------------------------------------------------------------------------------------

    fun saveEvent(e: ActivityEvent) { viewModelScope.launch { c.activities.save(e) } }

    fun deleteEvent(id: String) { viewModelScope.launch { c.activities.delete(id) } }

    fun deleteOccurrence(id: String, date: LocalDate) { viewModelScope.launch { c.activities.deleteOccurrence(id, date) } }

    fun moveOccurrence(id: String, date: LocalDate, newStartsAt: Long) {
        viewModelScope.launch { c.activities.moveOccurrence(id, date, newStartsAt) }
    }

    fun setEventStatus(id: String, date: LocalDate, status: EventStatus) {
        viewModelScope.launch { c.activities.setOccurrenceStatus(id, date, status) }
    }

    fun linkEvent(id: String, date: LocalDate, sessionId: String?) {
        viewModelScope.launch { c.activities.linkOccurrence(id, date, sessionId) }
    }

    // ---- history list ---------------------------------------------------------------------------------------------

    fun createPast(plannedWorkoutId: String?, title: String, startedAt: Long, durationMin: Int, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(c.workouts.createPastWorkout(plannedWorkoutId, title, startedAt, durationMin)) }
    }

    fun deleteWorkout(id: String) { viewModelScope.launch { c.workouts.deleteSession(id) } }
}
