package app.liftos.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.data.repo.FinishSummary
import app.liftos.data.repo.ToggleResult
import app.liftos.di.AppContainer
import app.liftos.domain.logic.PreviousPerformance
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.SetType
import app.liftos.domain.model.WorkoutDetail
import app.liftos.service.RestFinisher
import app.liftos.service.WorkoutService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WorkoutUi {
    data object Loading : WorkoutUi
    data object NotFound : WorkoutUi
    data class Ready(val detail: WorkoutDetail) : WorkoutUi
}

/** Which input of a set row has the keyboard (drives the quick +/- toolbar). */
enum class FieldKind { WEIGHT, REPS, EFFORT, DURATION }

data class FocusedField(val setId: String, val kind: FieldKind)

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
    fun setTitle(v: String) = launch { c.workouts.setTitle(sessionId, v) }
    fun setTimes(start: Long, end: Long?) = launch { c.workouts.setTimes(sessionId, start, end) }

    fun finish(onDone: (FinishSummary) -> Unit) {
        viewModelScope.launch {
            val summary = c.workouts.finishWorkout(sessionId)
            WorkoutService.stop(c.app)
            onDone(summary)
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
