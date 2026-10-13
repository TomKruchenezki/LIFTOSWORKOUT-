package app.liftos.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import app.liftos.data.TimeSource
import app.liftos.data.ZoneSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.PhotoAngle
import app.liftos.domain.model.ProgressPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Progress photos live in the app's private storage (`files/photos`), never in the phone gallery and
 * never on the network. Every photo is re-encoded as a downsized JPEG, which also removes location and
 * camera metadata from the file.
 */
class PhotoRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val zones: ZoneSource = ZoneSource.Device,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.photoDao()

    val directory: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun observeAll(): Flow<List<ProgressPhoto>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun getAll(): List<ProgressPhoto> = dao.getAll().map { it.toDomain() }

    fun fileOf(photo: ProgressPhoto): File = File(directory, photo.fileName)

    // ---- camera hand-off ---------------------------------------------------------------------------

    /** A temporary file + FileProvider uri for the camera app to write into. */
    fun newCameraTarget(): Pair<File, Uri> {
        val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        dir.listFiles()?.filter { time.nowMs() - it.lastModified() > 3_600_000L }?.forEach { it.delete() }
        val file = File(dir, "capture-${time.nowMs()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    // ---- adding ---------------------------------------------------------------------------------------

    suspend fun addFromUri(uri: Uri, angle: PhotoAngle, takenAt: Long, weightKg: Double?, notes: String): String? =
        withContext(Dispatchers.IO) {
            val bitmap = decode { context.contentResolver.openInputStream(uri) } ?: return@withContext null
            save(bitmap, angle, takenAt, weightKg, notes)
        }

    suspend fun addFromFile(file: File, angle: PhotoAngle, takenAt: Long, weightKg: Double?, notes: String): String? =
        withContext(Dispatchers.IO) {
            val bitmap = decode { file.inputStream() } ?: return@withContext null
            val id = save(bitmap, angle, takenAt, weightKg, notes)
            file.delete()
            id
        }

    private suspend fun save(bitmap: Bitmap, angle: PhotoAngle, takenAt: Long, weightKg: Double?, notes: String): String {
        val id = ids.newId()
        val name = "photo-$id.jpg"
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        val zone = zones.zone()
        val now = time.nowMs()
        dao.upsert(
            ProgressPhoto(
                id = id, takenAt = takenAt, localDate = TimeUtils.localDateString(takenAt, zone), zoneId = zone.id,
                angle = angle, fileName = name, weightKg = weightKg, notes = notes.trim(), createdAt = now, updatedAt = now,
            ).toEntity(),
        )
        return id
    }

    /** Decodes with a sample size that keeps memory low, applies the EXIF rotation, caps the long edge. */
    private fun decode(open: () -> java.io.InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = try {
            val orientation = open()?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
        val scale = MAX_EDGE.toFloat() / max(raw.width, raw.height)
        val matrix = Matrix().apply {
            if (rotation != 0) postRotate(rotation.toFloat())
            if (scale < 1f) postScale(scale, scale)
        }
        return if (matrix.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    // ---- editing / deleting -----------------------------------------------------------------------

    suspend fun update(id: String, angle: PhotoAngle, takenAt: Long, weightKg: Double?, notes: String) {
        val p = dao.get(id)?.toDomain() ?: return
        val zone = zones.zone()
        dao.upsert(
            p.copy(
                angle = angle, takenAt = takenAt, localDate = TimeUtils.localDateString(takenAt, zone), zoneId = zone.id,
                weightKg = weightKg, notes = notes.trim(), updatedAt = time.nowMs(),
            ).toEntity(),
        )
    }

    suspend fun delete(id: String) {
        val p = dao.get(id)?.toDomain() ?: return
        dao.delete(id)
        withContext(Dispatchers.IO) { File(directory, p.fileName).delete() }
    }

    /** Removes every photo file and row (used by "delete all my data"). */
    suspend fun deleteAll() {
        dao.deleteAll()
        withContext(Dispatchers.IO) { directory.listFiles()?.forEach { it.delete() } }
    }

    /** A small bitmap for lists and the comparison screen. */
    suspend fun loadBitmap(photo: ProgressPhoto, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileOf(photo)
        if (!file.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val DIR = "photos"
        const val CAMERA_DIR = "camera"
        const val MAX_EDGE = 2048
        const val JPEG_QUALITY = 88
    }
}
