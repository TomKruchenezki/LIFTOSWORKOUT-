package app.liftos.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.data.repo.MeasurementInput
import app.liftos.di.AppContainer
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.CustomMetric
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BodyViewModel(private val c: AppContainer) : ViewModel() {
    val measurements: StateFlow<List<BodyMeasurement>?> =
        c.measurements.observeAll().map<List<BodyMeasurement>, List<BodyMeasurement>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val metrics: StateFlow<List<CustomMetric>> = c.measurements.observeMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photoCount: StateFlow<Int> = c.photos.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun save(id: String?, input: MeasurementInput, measuredAt: Long) {
        viewModelScope.launch {
            if (id == null) c.measurements.add(input, measuredAt) else c.measurements.update(id, input, measuredAt)
            c.goalTracker.check()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { c.measurements.delete(id) }
    }

    fun addMetric(name: String, unit: String) {
        viewModelScope.launch { c.measurements.addMetric(name, unit) }
    }

    fun renameMetric(id: String, name: String, unit: String) {
        viewModelScope.launch { c.measurements.renameMetric(id, name, unit) }
    }

    /** [onResult] gets true when the metric was deleted, false when it was archived because it has values. */
    fun removeMetric(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(c.measurements.removeMetric(id)) }
    }

    fun restoreMetric(id: String) {
        viewModelScope.launch { c.measurements.restoreMetric(id) }
    }
}
