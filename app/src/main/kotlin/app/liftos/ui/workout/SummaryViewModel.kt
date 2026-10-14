package app.liftos.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.PersonalRecordEngine
import app.liftos.domain.analytics.PrEvent
import app.liftos.domain.export.TextReport
import app.liftos.domain.export.WorkoutReportContext
import app.liftos.domain.logic.SessionComparator
import app.liftos.domain.logic.SessionComparison
import app.liftos.domain.logic.SessionTotals
import app.liftos.domain.logic.VolumeCalculator
import app.liftos.domain.model.WorkoutDetail
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        val records: List<PrEvent>,
    ) : SummaryUi
}

class SummaryViewModel(private val c: AppContainer, private val sessionId: String) : ViewModel() {

    val ui: StateFlow<SummaryUi> = combine(c.workouts.observeDetail(sessionId), c.analyticsData) { detail, data -> detail to data }
        .map { (detail, data) ->
            if (detail == null) {
                SummaryUi.NotFound
            } else {
                val prev = c.workouts.previousSameType(sessionId)
                val comparison = prev?.let { SessionComparator.compare(detail, it) }
                val records = PersonalRecordEngine.headline(data.records.eventsFor(sessionId))
                SummaryUi.Ready(detail, VolumeCalculator.totals(detail), prev, comparison, records)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUi.Loading)

    /**
     * The same report the "analyze with AI" screen builds for this workout with its default options
     * (no body measurements, notes included): the quick-copy button on the summary must not differ from it.
     */
    suspend fun aiReportText(): String? {
        val detail = c.workouts.getDetail(sessionId) ?: return null
        val data = c.analyticsData.first()
        val previous = c.workouts.previousSameType(sessionId)
        val extras = WorkoutReportContext.build(detail, data.facts, data.sessions, data.records)
        return TextReport.aiReport(detail, previous, null, extras)
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            c.workouts.deleteSession(sessionId)
            onDone()
        }
    }
}
