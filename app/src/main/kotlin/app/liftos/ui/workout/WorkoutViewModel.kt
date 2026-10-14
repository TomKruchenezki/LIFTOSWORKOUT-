package app.liftos.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.data.repo.FinishSummary
import app.liftos.data.repo.ToggleResult
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.ExerciseKey
import app.liftos.domain.analytics.PersonalRecordEngine
import app.liftos.domain.analytics.PrEvent
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.logic.PreviousPerformance
import app.liftos.domain.progression.DecisionType
import app.liftos.domain.progression.ProgressionEngine
import app.liftos.domain.progression.ProgressionSuggestion
import app.liftos.domain.progression.RepTarget
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.SetType
import app.liftos.domain.model.WorkoutDetail
import app.liftos.service.RestFinisher
import app.liftos.service.WorkoutService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface WorkoutUi {
    data object Loading : WorkoutUi
    data object NotFound : WorkoutUi
    data class Ready(val detail: WorkoutDetail) : WorkoutUi
}

/** Which input of a set row has the keyboard (drives the quick +/- toolbar). */
enum class FieldKind { WEIGHT, REPS, EFFORT, DURATION }

data class FocusedField(val setId: String, val kind: FieldKind)

/** A progression suggestion for one exercise of the workout, and whether the user already decided about it. */
data class ExerciseHint(val suggestion: ProgressionSuggestion, val visibility: ProgressionEngine.Visibility)

/** The parts of an exercise that decide its history (changes to notes or sets must not reload it). */
private data class ExerciseRef(
    val id: String,
    val key: String,
    val exerciseId: String?,
    val name: String,
    val repsMin: Int?,
    val repsMax: Int?,
    val plannedSets: Int,
)

private data class HistoryBundle(val refs: List<ExerciseRef>, val facts: List<SetFact>, val sessions: List<SessionFact>)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(private val c: AppContainer, val sessionId: String) : ViewModel() {

    val ui: StateFlow<WorkoutUi> = c.workouts.observeDetail(sessionId)
        .map { if (it == null) WorkoutUi.NotFound else WorkoutUi.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUi.Loading)

    /** What was done last time per exercise; recomputed when the set of exercises changes. */
    val previous: StateFlow<Map<String, PreviousPerformance>> = c.workouts.observeDetail(sessionId)
        .map { d -> d?.exercises?.mapNotNull { it.exercise.exerciseId }?.toSet().orEmpty() }
        .distinctUntilChanged()
        .mapLatest { c.workouts.previousPerformance(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun detail(): WorkoutDetail? = (ui.value as? WorkoutUi.Ready)?.detail

    // ---- history based hints: progression suggestions and live personal records -------------------------------

    private val refs = c.workouts.observeDetail(sessionId)
        .map { d ->
            d?.exercises.orEmpty().map {
                val e = it.exercise
                ExerciseRef(e.id, ExerciseKey.of(e.exerciseId, e.nameEn, e.nameHe), e.exerciseId, e.nameHe, e.repsMin, e.repsMax, e.plannedSets)
            }
        }
        .distinctUntilChanged()

    /** Earlier workouts of the exercises in this one; loaded once per set of exercises, not on every keystroke. */
    private val history: Flow<HistoryBundle> = refs.mapLatest { list ->
        val keys = list.map { it.key }.toSet()
        val facts = c.analytics.historyFor(list.mapNotNull { it.exerciseId }.distinct(), sessionId).filter { it.key in keys }
        val lastIds = facts.groupBy { it.key }.values.mapNotNull { rows -> rows.maxByOrNull { it.startedAt }?.sessionId }
        HistoryBundle(list, facts, c.analytics.sessionFacts(lastIds))
    }.flowOn(Dispatchers.Default)

    val hints: StateFlow<Map<String, ExerciseHint>> = combine(
        history, c.progression.observeDecisions(), c.progression.observePrefs(),
    ) { bundle, decisions, prefs ->
        val today = LocalDate.now(c.zones.zone())
        bundle.refs.associate { ref ->
            val target = if (ref.repsMin != null && ref.repsMax != null && ref.repsMin in 1..ref.repsMax) {
                RepTarget(ref.repsMin, ref.repsMax, ref.plannedSets.coerceAtLeast(1))
            } else null
            val s = ProgressionEngine.suggest(ref.key, ref.name, bundle.facts, bundle.sessions, today, target, prefs[ref.key]?.incrementKg)
            ref.id to ExerciseHint(s, ProgressionEngine.visibility(s, decisions))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Records this workout has broken so far (completed sets count immediately). */
    val records: StateFlow<List<PrEvent>> = combine(history, c.analytics.observeFactsOfSession(sessionId)) { bundle, own ->
        PersonalRecordEngine.eventsForSession(bundle.facts, own, sessionId)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _newRecords = MutableSharedFlow<PrEvent>(extraBufferCapacity = 8)

    /** Emits a record at the moment it is first set during this screen's lifetime. */
    val newRecords: SharedFlow<PrEvent> = _newRecords

    init {
        viewModelScope.launch {
            var known: Set<String>? = null
            records.collect { list ->
                val current = list.map { it.exerciseKey + "|" + it.kind.name }.toSet()
                val before = known
                if (before != null) {
                    list.filter { (it.exerciseKey + "|" + it.kind.name) !in before }
                        .filter { it.kind != app.liftos.domain.analytics.RecordKind.REPS_AT_WEIGHT && it.kind != app.liftos.domain.analytics.RecordKind.BEST_VOLUME }
                        .minByOrNull { it.kind.priority }?.let { _newRecords.tryEmit(it) }
                }
                known = current
            }
        }
    }

    /** The user accepted a suggestion (or typed a weight): put it on the sets that are not done yet. */
    fun applyWeight(sessionExerciseId: String, suggestion: ProgressionSuggestion, weightKg: Double, manual: Boolean) {
        viewModelScope.launch {
            c.workouts.applyWeightToOpenSets(sessionExerciseId, weightKg)
            c.progression.record(suggestion, if (manual) DecisionType.MANUAL else DecisionType.APPLIED, weightKg)
        }
    }

    fun ignoreHint(suggestion: ProgressionSuggestion) {
        viewModelScope.launch { c.progression.record(suggestion, DecisionType.IGNORED) }
    }

    // ---- sets ---------------------------------------------------------------------------------------

    fun setWeight(id: String, v: Double?) = launch { c.workouts.setWeight(id, v) }
    fun setReps(id: String, v: Int?) = launch { c.workouts.setReps(id, v) }
    fun setDuration(id: String, v: Int?) = launch { c.workouts.setDuration(id, v) }
    fun setRir(id: String, v: Int?) = launch { c.workouts.setRir(id, v) }
    fun setRpe(id: String, v: Double?) = launch { c.workouts.setRpe(id, v) }
    fun setSetNotes(id: String, v: String) = launch { c.workouts.setSetNotes(id, v) }
    fun setFailure(id: String, v: Boolean) = launch { c.workouts.setFailure(id, v) }
    fun changeSetType(id: String, t: SetType) = launch { c.workouts.changeSetType(id, t) }
    fun addSet(seId: String, type: SetType = SetType.WORKING) = launch { c.workouts.addSet(seId, type) }
    fun deleteSet(id: String) = launch { c.workouts.deleteSet(id) }

    fun toggleSet(id: String, onResult: (ToggleResult) -> Unit) {
        viewModelScope.launch { onResult(c.workouts.toggleSetCompleted(id)) }
    }

    /** Quick +/- from the keyboard toolbar; reads the current value so repeated taps accumulate. */
    fun adjust(field: FocusedField, delta: Double) {
        val set = detail()?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == field.setId } ?: return
        when (field.kind) {
            FieldKind.WEIGHT -> setWeight(set.id, ((set.weightKg ?: 0.0) + delta).coerceAtLeast(0.0))
            FieldKind.REPS -> setReps(set.id, ((set.reps ?: 0) + delta.toInt()).coerceAtLeast(0))
            FieldKind.DURATION -> setDuration(set.id, ((set.durationSec ?: 0) + delta.toInt()).coerceAtLeast(0))
            FieldKind.EFFORT -> {
                if (c.settingsSnapshot().effortMode == app.liftos.domain.model.EffortMode.RPE) {
                    setRpe(set.id, ((set.rpe ?: 0.0) + delta).coerceIn(0.0, 10.0))
                } else {
                    setRir(set.id, ((set.rir ?: 0) + delta.toInt()).coerceIn(0, 10))
                }
            }
        }
    }

    // ---- exercises ----------------------------------------------------------------------------------

    fun addExercise(e: Exercise) = launch { c.workouts.addExercise(sessionId, e) }
    fun replaceExercise(seId: String, e: Exercise) = launch { c.workouts.replaceExercise(seId, e) }
    fun removeExercise(seId: String) = launch { c.workouts.removeExercise(seId) }
    fun reorder(ids: List<String>) = launch { c.workouts.reorderExercises(sessionId, ids) }
    fun setExerciseNotes(seId: String, v: String) = launch { c.workouts.setExerciseNotes(seId, v) }
    fun skip(seId: String, reason: String) = launch { c.workouts.setSkipped(seId, true, reason) }
    fun unskip(seId: String) = launch { c.workouts.setSkipped(seId, false) }

    // ---- session ------------------------------------------------------------------------------------

    fun setNotes(v: String) = launch { c.workouts.setSessionNotes(sessionId, v) }
    fun setRatings(energy: Int?, mood: Int?) = launch { c.workouts.setRatings(sessionId, energy, mood) }
    fun setRecovery(difficulty: Int?, quality: Int?, soreness: Int?, fatigue: Int?, sleepHours: Double?) =
        launch { c.workouts.setRecovery(sessionId, difficulty, quality, soreness, fatigue, sleepHours) }
    fun setTitle(v: String) = launch { c.workouts.setTitle(sessionId, v) }
    fun setTimes(start: Long, end: Long?) = launch { c.workouts.setTimes(sessionId, start, end) }

    fun finish(onDone: (FinishSummary) -> Unit) {
        viewModelScope.launch {
            val summary = c.workouts.finishWorkout(sessionId)
            WorkoutService.stop(c.app)
            onDone(summary)
            c.goalTracker.check()
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            c.workouts.discardWorkout(sessionId)
            WorkoutService.stop(c.app)
            onDone()
        }
    }

    fun deleteCompleted(onDone: () -> Unit) {
        viewModelScope.launch {
            c.workouts.deleteSession(sessionId)
            onDone()
        }
    }

    // ---- rest timer -----------------------------------------------------------------------------------

    fun startRest(seconds: Int, label: String) = launch { c.workouts.startRest(sessionId, seconds, label) }
    fun pauseRest() = launch { c.workouts.pauseRest(sessionId) }
    fun resumeRest() = launch { c.workouts.resumeRest(sessionId) }
    fun addRest(seconds: Int) = launch { c.workouts.addRestSeconds(sessionId, seconds) }
    fun skipRest() = launch { c.workouts.skipRest(sessionId) }

    /** Fallback finisher for when the app is in front but the service did not end the rest (e.g. no permission). */
    fun finishRestFromUi(endsAt: Long) {
        viewModelScope.launch { RestFinisher.finish(c.app, c, sessionId, endsAt) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
