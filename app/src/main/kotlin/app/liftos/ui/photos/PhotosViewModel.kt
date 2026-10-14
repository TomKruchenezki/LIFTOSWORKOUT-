package app.liftos.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.di.AppContainer
import app.liftos.domain.model.PhotoAngle
import app.liftos.domain.model.ProgressPhoto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class PhotosViewModel(val c: AppContainer) : ViewModel() {
    val repo get() = c.photos

    val photos: StateFlow<List<ProgressPhoto>?> = c.photos.observeAll().map<List<ProgressPhoto>, List<ProgressPhoto>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The latest logged body weight, offered as the default for a new photo (the user can clear it). */
    val latestWeight: StateFlow<Double?> = c.measurements.observeLatestWeight().map { it?.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun newCameraTarget(): Pair<File, Uri> = c.photos.newCameraTarget()

    fun addFromUri(uri: Uri, angle: PhotoAngle, takenAt: Long, weightKg: Double?, notes: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(c.photos.addFromUri(uri, angle, takenAt, weightKg, notes) != null) }
    }

    fun addFromFile(file: File, angle: PhotoAngle, takenAt: Long, weightKg: Double?, notes: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(c.photos.addFromFile(file, angle, takenAt, weightKg, notes) != null) }
    }

    fun update(id: String, angle: PhotoAngle, takenAt: Long, weightKg: Double?, notes: String) {
        viewModelScope.launch { c.photos.update(id, angle, takenAt, weightKg, notes) }
    }

    fun delete(id: String) {
        viewModelScope.launch { c.photos.delete(id) }
    }
}
