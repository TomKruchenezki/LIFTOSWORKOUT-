package app.liftos.ui.photos

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.liftos.data.repo.PhotoRepository
import app.liftos.domain.model.ProgressPhoto

/** Decoded thumbnails, so scrolling the grid does not decode the files again. Bitmaps are never recycled here. */
private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** A private progress photo (read from the app's own storage), decoded off the main thread. */
@Composable
fun PhotoImage(
    repo: PhotoRepository,
    photo: ProgressPhoto,
    maxEdge: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val key = "${photo.id}:${photo.fileName}:$maxEdge"
    val bitmap by produceState<Bitmap?>(initialValue = cache.get(key), key) {
        if (value == null) value = repo.loadBitmap(photo, maxEdge)?.also { cache.put(key, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bitmap?.let { b ->
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
