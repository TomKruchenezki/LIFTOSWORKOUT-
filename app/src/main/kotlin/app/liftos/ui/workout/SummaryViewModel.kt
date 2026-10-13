package app.liftos.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.export.BodySnapshot
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.PersonalRecord
import app.liftos.domain.logic.PersonalRecords
import app.liftos.domain.logic.SessionComparator
import app.liftos.domain.logic.SessionComparison
import app.liftos.domain.logic.SessionTotals
import app.liftos.domain.logic.VolumeCalculator
import app.liftos.domain.model.WorkoutDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SummaryUi {
    data object Loading : SummaryUi
    data object NotFound : SummaryUi
    data class Ready(
        val detail: WorkoutDetail,
        val totals: SessionTotals,
        val previous: WorkoutDetail?,
        val comparison: SessionComparison?,
        val records: List<PersonalRecord>,
    ) : SummaryUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(private val c: AppContainer, private val sessionId: String) : ViewModel() {

    val ui: StateFlow<SummaryUi> = c.workouts.observeDetail(sessionId).flatMapLatest { detail ->
        flow {
            if (detail == null) {
                emit(SummaryUi.NotFound)
            } else {
                val prev = c.workouts.previousSameType(sessionId)
                val comparison = prev?.let { SessionComparator.compare(detail, it) }
                val records = PersonalRecords.detect(detail, c.workouts.recordBaselines(detail))
                emit(SummaryUi.Ready(detail, VolumeCalculator.totals(detail), prev, comparison, records))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUi.Loading)

    /** Body data is only read when the user explicitly ticks "attach body measurements". */
    suspend fun bodySnapshot(): BodySnapshot {
        val all = c.measurements.observeAllOnce()
        val latest = all.firstOrNull { it.weightKg != null } ?: all.firstOrNull()
        return BodySnapshot(latest, BodyTrends.weightChange(all, 7), BodyTrends.weightChange(all, 30))
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            c.workouts.deleteSession(sessionId)
            onDone()
        }
    }
}
