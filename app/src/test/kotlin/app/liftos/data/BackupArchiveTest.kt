package app.liftos.data

import android.net.Uri
import app.liftos.data.db.toEntity
import app.liftos.data.repo.ImportPreview
import app.liftos.data.repo.MeasurementInput
import app.liftos.domain.backup.ImportMode
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalType
import app.liftos.domain.model.PhotoAngle
import app.liftos.domain.model.ProgressPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** ZIP backups with photos, goals and custom fields, and the "delete everything" path. */
@RunWith(RobolectricTestRunner::class)
class BackupArchiveTest {
    private lateinit var env: TestEnv
    private val photoBytes = "not-really-a-jpeg-but-bytes".toByteArray()

    @Before
    fun setUp() = runBlocking {
        env = TestEnv()
        env.seeder.seedIfNeeded()
    }

    @After
    fun tearDown() = env.close()

    private suspend fun addPhoto(e: TestEnv, id: String = "ph1"): ProgressPhoto {
        val photo = ProgressPhoto(
            id = id, takenAt = e.now, localDate = "2026-10-14", zoneId = "Asia/Jerusalem", angle = PhotoAngle.SIDE,
            fileName = "photo-$id.jpg", weightKg = 82.0, notes = "בדיקה",
        )
        e.db.photoDao().upsert(photo.toEntity())
        File(e.photos.directory, photo.fileName).writeBytes(photoBytes)
        return photo
    }

    private suspend fun populate(e: TestEnv) {
        val metricId = e.measurements.addMetric("היקף ברך", "ס״מ")!!
        e.measurements.add(MeasurementInput(weightKg = 82.0, customValues = mapOf(metricId to 38.5)))
        e.goals.create(Goal(id = "", title = "משקל 80", type = GoalType.BODY_WEIGHT, metricKey = "weight", startValue = 82.0, targetValue = 80.0, startDate = "2026-10-01"))
        addPhoto(e)
    }

    private fun zipFile(): File = File.createTempFile("backup", ".zip")

    @Test
    fun `a zip backup carries photos goals and custom fields into a fresh install`() = runBlocking {
        populate(env)
        val zip = zipFile()
        val written = zip.outputStream().use { env.backup.exportZip(it, includePhotos = true) }
        assertEquals(1, written)
        // All test environments share one files directory, so remove the original to prove the restore recreates it.
        File(env.photos.directory, "photo-ph1.jpg").delete()

        val fresh = TestEnv(idPrefix = "fresh")
        fresh.seeder.seedIfNeeded()
        val preview = fresh.backup.previewUri(Uri.fromFile(zip)) as ImportPreview.Ready
        assertTrue(preview.isArchive)
        assertEquals(1, preview.summary.photos)
        assertEquals(1, preview.summary.goals)
        assertEquals(1, preview.summary.customMetrics)
        assertEquals(setOf("photo-ph1.jpg"), preview.archivePhotos)
        assertEquals("everything in the file is new to a fresh install", 1, preview.mergeNew.photos)

        val result = fresh.backup.import(preview, ImportMode.REPLACE_ALL)
        assertEquals(1, result.photosRestored)
        assertEquals(photoBytes.toList(), File(fresh.photos.directory, "photo-ph1.jpg").readBytes().toList())
        assertEquals(1, fresh.photos.getAll().size)
        assertEquals(1, fresh.goals.getAll().size)
        assertEquals(1, fresh.measurements.observeMetrics().first().size)
        assertEquals(mapOf(fresh.measurements.observeMetrics().first().single().id to 38.5), fresh.measurements.observeAllOnce().single().customValues)
        fresh.close()
    }

    @Test
    fun `merging the same archive again reports everything as already existing`() = runBlocking {
        populate(env)
        val zip = zipFile()
        zip.outputStream().use { env.backup.exportZip(it, includePhotos = true) }
        val preview = env.backup.previewUri(Uri.fromFile(zip)) as ImportPreview.Ready
        assertEquals(0, preview.mergeNew.photos)
        assertEquals(0, preview.mergeNew.goals)
        assertEquals(0, preview.mergeNew.measurements)
        assertEquals(0, preview.mergeNew.customMetrics)
        assertEquals(1, preview.summary.photos)
    }

    @Test
    fun `a backup without photos leaves the existing photos alone on replace`() = runBlocking {
        populate(env)
        val json = env.backup.exportJson()
        val preview = env.backup.preview(json) as ImportPreview.Ready
        assertFalse(preview.file.includesPhotos)
        env.backup.import(preview, ImportMode.REPLACE_ALL)
        assertEquals(1, env.photos.getAll().size)
        assertTrue(File(env.photos.directory, "photo-ph1.jpg").exists())
    }

    @Test
    fun `replacing with a backup that includes photos moves the old photo files to the safety folder`() = runBlocking {
        populate(env)
        addPhoto(env, "old")
        val zip = zipFile()
        zip.outputStream().use { env.backup.exportZip(it, includePhotos = true) }
        val other = TestEnv(idPrefix = "other")
        other.seeder.seedIfNeeded()
        addPhoto(other, "local")
        val preview = other.backup.previewUri(Uri.fromFile(zip)) as ImportPreview.Ready
        other.backup.import(preview, ImportMode.REPLACE_ALL)

        val names = other.photos.directory.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(setOf("photo-ph1.jpg", "photo-old.jpg"), names)
        assertFalse("the local photo is no longer in the live folder", "photo-local.jpg" in names)
        val safety = File(other.context.filesDir, "backups").walkTopDown().filter { it.name == "photo-local.jpg" }.toList()
        assertEquals("the replaced photo is kept in the safety folder", 1, safety.size)
        other.close()
    }

    @Test
    fun `unsafe photo names inside an archive are ignored`() = runBlocking {
        populate(env)
        val zip = zipFile()
        val json = env.backup.exportJson()
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("backup.json")); out.write(json.toByteArray()); out.closeEntry()
            out.putNextEntry(ZipEntry("photos/../../evil.jpg")); out.write(photoBytes); out.closeEntry()
            out.putNextEntry(ZipEntry("photos/ok.jpg")); out.write(photoBytes); out.closeEntry()
        }
        val preview = env.backup.previewUri(Uri.fromFile(zip)) as ImportPreview.Ready
        assertEquals(setOf("ok.jpg"), preview.archivePhotos)
    }

    @Test
    fun `an archive without backup json or with garbage is refused`() = runBlocking {
        val empty = zipFile()
        ZipOutputStream(empty.outputStream()).use { out -> out.putNextEntry(ZipEntry("readme.txt")); out.write("x".toByteArray()); out.closeEntry() }
        assertTrue(env.backup.previewUri(Uri.fromFile(empty)) is ImportPreview.Failure)

        val garbage = zipFile()
        garbage.writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 1, 2, 3, 4, 5, 6))
        assertTrue(env.backup.previewUri(Uri.fromFile(garbage)) is ImportPreview.Failure)

        val notBackup = zipFile()
        notBackup.writeText("{\"hello\": 1}")
        assertTrue(env.backup.previewUri(Uri.fromFile(notBackup)) is ImportPreview.Failure)
    }

    @Test
    fun `delete everything removes personal data and photo files, then restores the starting state`() = runBlocking {
        populate(env)
        env.activities.save(app.liftos.domain.model.ActivityEvent("", app.liftos.domain.model.ActivityType.RUN, "ריצה", startsAt = env.now, localDate = "", zoneId = ""))
        env.backup.deleteEverything()

        assertTrue(env.photos.getAll().isEmpty())
        assertTrue(env.photos.directory.listFiles().orEmpty().isEmpty())
        assertTrue(env.goals.getAll().isEmpty())
        assertTrue(env.measurements.observeAllOnce().isEmpty())
        assertTrue(env.measurements.observeMetrics().first().isEmpty())
        assertTrue(env.activities.observeAll().first().isEmpty())
        // Like a fresh install: the library and the starting program are back.
        assertTrue(env.exercises.observeAll().first().size >= 100)
        assertEquals(1, env.plans.observePlans().first().size)
    }
}
