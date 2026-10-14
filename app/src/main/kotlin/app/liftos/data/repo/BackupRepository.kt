package app.liftos.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.liftos.data.TimeSource
import app.liftos.data.ZoneSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.MeasurementCustomValueEntity
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.data.prefs.DevicePrefs
import app.liftos.domain.backup.BackupCodec
import app.liftos.domain.backup.BackupData
import app.liftos.domain.backup.BackupFile
import app.liftos.domain.backup.BackupMerge
import app.liftos.domain.backup.BackupSource
import app.liftos.domain.backup.BackupSummary
import app.liftos.domain.backup.BackupValidator
import app.liftos.domain.backup.ExistingIds
import app.liftos.domain.backup.ImportMode
import app.liftos.domain.backup.ParseResult
import app.liftos.domain.backup.ValidationReport
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.ProgressPhoto
import app.liftos.domain.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed interface ImportPreview {
    data class Failure(val message: String) : ImportPreview

    data class Ready(
        val file: BackupFile,
        val source: BackupSource,
        val parseWarnings: List<String>,
        val report: ValidationReport,
        /** Set when the backup is a ZIP archive: the photos are read from it again at import time. */
        val archiveUri: Uri? = null,
        /** File names of the photos that are present inside the archive. */
        val archivePhotos: Set<String> = emptySet(),
        /** What a MERGE import would add (the rest already exists on this device, matched by id). */
        val mergeNew: BackupSummary = report.summary,
    ) : ImportPreview {
        val canImport: Boolean get() = report.isValid
        val warnings: List<String> get() = parseWarnings + report.warnings
        val summary: BackupSummary get() = report.summary
        val isArchive: Boolean get() = archiveUri != null
    }
}

data class ImportResult(
    val mode: ImportMode,
    val imported: BackupSummary,
    val skippedActiveSessions: Int,
    /** Name of the automatic safety copy of the data that existed before the import. */
    val safetyCopyName: String?,
    val photosRestored: Int = 0,
)

class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val seeder: Seeder,
    private val prefs: DevicePrefs,
    private val time: TimeSource = TimeSource.Default,
    private val appVersion: String = "",
) {
    private val photosDir: File get() = File(context.filesDir, PhotoRepository.DIR).apply { mkdirs() }

    // ---- export ------------------------------------------------------------------------------

    /** Consistent snapshot of every table (single read transaction). */
    suspend fun snapshot(includePhotos: Boolean = false): BackupFile = db.withTransaction {
        val values = db.customMetricDao().getValues().groupBy { it.measurementId }
        BackupFile(
            exportedAt = time.nowMs(),
            appVersion = appVersion,
            includesPhotos = includePhotos,
            data = BackupData(
                exercises = db.exerciseDao().getAll().map { it.toDomain() },
                plans = db.planDao().getPlans().map { it.toDomain() },
                plannedWorkouts = db.planDao().getAllWorkouts().map { it.toDomain() },
                plannedExercises = db.planDao().getAllPlannedExercises().map { it.toDomain() },
                sessions = db.sessionDao().getAllSessions().map { it.toDomain() },
                sessionExercises = db.sessionDao().getAllSessionExercises().map { it.toDomain() },
                sets = db.sessionDao().getAllSets().map { it.toDomain() },
                measurements = db.measurementDao().getAll()
                    .map { e -> e.toDomain(values[e.id].orEmpty().associate { it.metricId to it.value }) },
                events = db.activityDao().getAll().map { it.toDomain() },
                settings = db.settingsDao().get()?.toDomain() ?: AppSettings(),
                customMetrics = db.customMetricDao().getAll().map { it.toDomain() },
                plannedOverrides = db.calendarDao().getOverrides().map { it.toDomain() },
                goals = db.goalDao().getAll().map { it.toDomain() },
                goalChanges = db.goalDao().getAllChanges().map { it.toDomain() },
                photos = if (includePhotos) db.photoDao().getAll().map { it.toDomain() } else emptyList(),
                exercisePrefs = db.progressionDao().getPrefs().map { it.toDomain() },
                progressionDecisions = db.progressionDao().getDecisions().map { it.toDomain() },
            ),
        )
    }

    suspend fun exportJson(): String = BackupCodec.encode(snapshot(includePhotos = false))

    /**
     * Writes a ZIP: `backup.json` (everything, including photo metadata) plus `photos/<file>` for every
     * photo that exists on disk. Returns how many photo files were written.
     */
    suspend fun exportZip(out: OutputStream, includePhotos: Boolean): Int = withContext(Dispatchers.IO) {
        val file = snapshot(includePhotos)
        var written = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(BackupCodec.encode(file).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (includePhotos) {
                for (p in file.data.photos) {
                    val f = File(photosDir, p.fileName)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry("$PHOTO_PREFIX${p.fileName}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    written++
                }
            }
        }
        written
    }

    suspend fun markExported() = prefs.setLastBackupAt(time.nowMs())

    // ---- file access -----------------------------------------------------------------------------

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("לא ניתן לפתוח את הקובץ")
        input.use { String(readLimited(it), Charsets.UTF_8) }
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("לא ניתן לכתוב לקובץ")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun writeZip(uri: Uri, includePhotos: Boolean): Int = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("לא ניתן לכתוב לקובץ")
        exportZip(out, includePhotos)
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val bytes = stream.readNBytes(MAX_IMPORT_BYTES + 1)
        if (bytes.size > MAX_IMPORT_BYTES) error("הקובץ גדול מדי (מעל ${MAX_IMPORT_BYTES / 1_000_000}MB)")
        return bytes
    }

    // ---- import ----------------------------------------------------------------------------------

    /** Parses and validates a backup file (JSON or ZIP) without touching the database. */
    suspend fun previewUri(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        try {
            val head = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(4) } ?: return@withContext ImportPreview.Failure("לא ניתן לפתוח את הקובץ")
            if (head.size == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2].toInt() == 3 && head[3].toInt() == 4) {
                previewArchive(uri)
            } else {
                preview(readText(uri))
            }
        } catch (e: Exception) {
            ImportPreview.Failure(e.message ?: "הקובץ לא נקרא")
        }
    }

    private suspend fun previewArchive(uri: Uri): ImportPreview {
        var json: String? = null
        val photos = mutableSetOf<String>()
        val input = context.contentResolver.openInputStream(uri) ?: return ImportPreview.Failure("לא ניתן לפתוח את הקובץ")
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    if (name == JSON_ENTRY) {
                        json = String(readLimited(zip), Charsets.UTF_8)
                    } else if (name.startsWith(PHOTO_PREFIX) && isSafeFileName(name.removePrefix(PHOTO_PREFIX))) {
                        photos += name.removePrefix(PHOTO_PREFIX)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val text = json ?: return ImportPreview.Failure("בארכיון אין קובץ $JSON_ENTRY. זה לא גיבוי של LiftOS.")
        return when (val base = preview(text)) {
            is ImportPreview.Failure -> base
            is ImportPreview.Ready -> {
                val missing = base.report.cleaned.photos.count { it.fileName !in photos }
                val extra = if (missing > 0) listOf("$missing תמונות מופיעות ברשימה אך חסרות בארכיון; הן לא ישוחזרו.") else emptyList()
                base.copy(
                    parseWarnings = base.parseWarnings + extra,
                    archiveUri = uri,
                    archivePhotos = photos,
                )
            }
        }
    }

    /** Parses and validates a backup given as text. */
    suspend fun preview(text: String): ImportPreview {
        val library = db.exerciseDao().getAll().map { it.toDomain() }
        val zone = ZoneSource.Device.zone()
        return when (val parsed = BackupCodec.parse(text, library, zone, time.nowMs())) {
            is ParseResult.Failure -> ImportPreview.Failure(parsed.message)
            is ParseResult.Success -> {
                val known = library.map { it.id }.toSet()
                val report = BackupValidator.validate(parsed.file, known)
                val mergeNew = if (report.isValid) BackupSummary.of(BackupMerge.delta(report.cleaned, existingIds())) else report.summary
                ImportPreview.Ready(parsed.file, parsed.source, parsed.warnings, report, mergeNew = mergeNew)
            }
        }
    }

    /**
     * Applies a previewed backup. A safety copy of the current data is written first, and the
     * whole database operation is one transaction: it either completes or changes nothing.
     */
    suspend fun import(preview: ImportPreview.Ready, mode: ImportMode): ImportResult {
        require(preview.canImport) { "Backup failed validation" }
        val safety = writeSafetyCopy()
        var skippedActive = 0
        var photosToRestore: List<ProgressPhoto> = emptyList()
        db.withTransaction {
            val data = preview.report.cleaned
            when (mode) {
                ImportMode.REPLACE_ALL -> {
                    replaceAll(data, replacePhotos = preview.file.includesPhotos)
                    photosToRestore = data.photos
                }
                ImportMode.MERGE -> {
                    val (active, newPhotos) = merge(data)
                    skippedActive = active
                    photosToRestore = newPhotos
                }
            }
            fixDanglingSettings()
        }
        // Only after the database change succeeded: the photos being replaced are moved aside, not deleted.
        if (mode == ImportMode.REPLACE_ALL && preview.file.includesPhotos) archiveOldPhotos(safety)
        val restored = if (preview.archiveUri != null) restorePhotoFiles(preview.archiveUri, photosToRestore) else 0
        return ImportResult(mode, preview.summary, skippedActive, safety?.name, restored)
    }

    private suspend fun replaceAll(data: BackupData, replacePhotos: Boolean) {
        // Children first, so no foreign key is ever violated.
        db.goalDao().deleteAllChanges()
        db.goalDao().deleteAll()
        db.customMetricDao().deleteAllValues()
        db.sessionDao().deleteAllSets()
        db.sessionDao().deleteAllSessionExercises()
        db.sessionDao().deleteAllSessions()
        db.planDao().deleteAllPlannedExercises()
        db.planDao().deleteAllWorkouts()
        db.planDao().deleteAllPlans()
        db.exerciseDao().deleteAll()
        db.measurementDao().deleteAll()
        db.customMetricDao().deleteAll()
        db.activityDao().deleteAll()
        db.calendarDao().deleteAllOverrides()
        db.progressionDao().deleteAllDecisions()
        db.progressionDao().deleteAllPrefs()
        if (replacePhotos) db.photoDao().deleteAll()
        db.settingsDao().deleteAll()

        // Parents first. Built-ins are inserted before the backup's rows so every reference resolves.
        seeder.upsertBuiltIns()
        insert(data)
        val settings = data.settings ?: AppSettings()
        db.settingsDao().upsert(settings.toEntity())
    }

    /** Adds only what is missing. Returns (active workouts left out, photos that were added). */
    /** The ids of everything already stored, used to tell new items from duplicates. */
    private suspend fun existingIds() = ExistingIds(
        exercises = db.exerciseDao().allIds().toSet(),
        plans = db.planDao().planIds().toSet(),
        plannedWorkouts = db.planDao().workoutIds().toSet(),
        plannedExercises = db.planDao().plannedExerciseIds().toSet(),
        sessions = db.sessionDao().sessionIds().toSet(),
        sessionExercises = db.sessionDao().sessionExerciseIds().toSet(),
        sets = db.sessionDao().setIds().toSet(),
        measurements = db.measurementDao().ids().toSet(),
        events = db.activityDao().ids().toSet(),
        customMetrics = db.customMetricDao().ids().toSet(),
        plannedOverrides = db.calendarDao().overrideIds().toSet(),
        goals = db.goalDao().ids().toSet(),
        goalChanges = db.goalDao().changeIds().toSet(),
        photos = db.photoDao().ids().toSet(),
        exercisePrefs = db.progressionDao().prefKeys().toSet(),
        progressionDecisions = db.progressionDao().decisionIds().toSet(),
    )

    private suspend fun merge(data: BackupData): Pair<Int, List<ProgressPhoto>> {
        val existing = existingIds()
        val delta = BackupMerge.delta(data, existing)
        // An in-progress workout from another device would clash with the local one; skip it.
        val activeIds = delta.sessions.filter { it.status == SessionStatus.ACTIVE }.map { it.id }.toSet()
        val exerciseIdsOfActive = delta.sessionExercises.filter { it.sessionId in activeIds }.map { it.id }.toSet()
        val filtered = delta.copy(
            sessions = delta.sessions.filterNot { it.id in activeIds },
            sessionExercises = delta.sessionExercises.filterNot { it.sessionId in activeIds },
            sets = delta.sets.filterNot { it.sessionExerciseId in exerciseIdsOfActive },
        )
        insert(filtered)
        return activeIds.size to delta.photos
    }

    private suspend fun insert(d: BackupData) {
        db.exerciseDao().upsertAll(d.exercises.map { it.toEntity() })
        db.planDao().upsertPlans(d.plans.map { it.toEntity() })
        db.planDao().upsertWorkouts(d.plannedWorkouts.map { it.toEntity() })
        db.planDao().upsertPlannedExercises(d.plannedExercises.map { it.toEntity() })
        db.sessionDao().upsertSessions(d.sessions.map { it.toEntity() })
        db.sessionDao().upsertSessionExercises(d.sessionExercises.map { it.toEntity() })
        d.sets.chunked(500).forEach { chunk -> db.sessionDao().upsertSets(chunk.map { it.toEntity() }) }
        db.customMetricDao().upsertAll(d.customMetrics.map { it.toEntity() })
        db.measurementDao().upsertAll(d.measurements.map { it.toEntity() })
        val metricIds = db.customMetricDao().ids().toSet()
        val values = d.measurements.flatMap { m ->
            m.customValues.filterKeys { it in metricIds }.map { (metricId, v) -> MeasurementCustomValueEntity(m.id, metricId, v) }
        }
        if (values.isNotEmpty()) db.customMetricDao().upsertValues(values)
        db.activityDao().upsertAll(d.events.map { it.toEntity() })
        db.calendarDao().upsertOverrides(d.plannedOverrides.map { it.toEntity() })
        db.goalDao().upsertAll(d.goals.map { it.toEntity() })
        db.goalDao().upsertChanges(d.goalChanges.map { it.toEntity() })
        db.photoDao().upsertAll(d.photos.map { it.toEntity() })
        db.progressionDao().upsertPrefs(d.exercisePrefs.map { it.toEntity() })
        db.progressionDao().upsertDecisions(d.progressionDecisions.map { it.toEntity() })
    }

    private suspend fun fixDanglingSettings() {
        val dao = db.settingsDao()
        val s = dao.get() ?: AppSettings().toEntity().also { dao.upsert(it) }
        val plan = s.activePlanId
        if (plan != null && db.planDao().getPlan(plan) == null) dao.upsert(s.copy(activePlanId = null))
    }

    /** Copies the wanted photo files out of the archive (second pass). Names are validated, never trusted. */
    private suspend fun restorePhotoFiles(uri: Uri, wanted: List<ProgressPhoto>): Int = withContext(Dispatchers.IO) {
        val names = wanted.map { it.fileName }.filter(::isSafeFileName).toSet()
        if (names.isEmpty()) return@withContext 0
        var restored = 0
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext 0
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.removePrefix(PHOTO_PREFIX)
                if (!entry.isDirectory && entry.name.startsWith(PHOTO_PREFIX) && name in names) {
                    val target = File(photosDir, name)
                    val tmp = File(photosDir, "$name.part")
                    tmp.outputStream().use { out -> zip.copyTo(out) }
                    if (target.exists()) target.delete()
                    if (tmp.renameTo(target)) restored++ else tmp.delete()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        restored
    }

    private fun isSafeFileName(name: String) =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && !name.contains("..") && name.length < 200

    // ---- safety copies ---------------------------------------------------------------------------

    /** Writes the current data (without photo files) to app-private storage before anything destructive. Keeps the last few. */
    suspend fun writeSafetyCopy(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, SAFETY_DIR).apply { mkdirs() }
            val file = File(dir, "pre-import-${time.nowMs()}.json")
            file.writeText(exportJson(), Charsets.UTF_8)
            dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.name }?.drop(KEEP_SAFETY_COPIES)?.forEach { it.delete() }
            file
        } catch (_: Exception) {
            null
        }
    }

    /** Moves the current photo files next to the safety copy, so a restore never makes photos vanish for good. */
    private suspend fun archiveOldPhotos(safetyCopy: File?) = withContext(Dispatchers.IO) {
        try {
            val files = photosDir.listFiles().orEmpty().filter { it.isFile }
            if (files.isEmpty()) return@withContext
            val dir = File(context.filesDir, SAFETY_DIR).apply { mkdirs() }
            val target = File(dir, (safetyCopy?.nameWithoutExtension ?: "pre-import-${time.nowMs()}") + "-photos").apply { mkdirs() }
            files.forEach { it.renameTo(File(target, it.name)) }
            dir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.drop(KEEP_SAFETY_PHOTO_FOLDERS)
                ?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
            // Keeping the files where they are is always safe.
        }
    }

    // ---- delete everything -----------------------------------------------------------------------

    /**
     * Removes all user data from the app: workouts, plans, body data, goals, events, photos, safety copies
     * and cached exports. The exercise library and the starting program are re-created, as on a fresh install.
     */
    suspend fun deleteEverything() {
        db.withTransaction {
            db.goalDao().deleteAllChanges()
            db.goalDao().deleteAll()
            db.customMetricDao().deleteAllValues()
            db.sessionDao().deleteAllSets()
            db.sessionDao().deleteAllSessionExercises()
            db.sessionDao().deleteAllSessions()
            db.planDao().deleteAllPlannedExercises()
            db.planDao().deleteAllWorkouts()
            db.planDao().deleteAllPlans()
            db.exerciseDao().deleteAll()
            db.measurementDao().deleteAll()
            db.customMetricDao().deleteAll()
            db.activityDao().deleteAll()
            db.calendarDao().deleteAllOverrides()
            db.progressionDao().deleteAllDecisions()
            db.progressionDao().deleteAllPrefs()
            db.photoDao().deleteAll()
            db.settingsDao().deleteAll()
        }
        withContext(Dispatchers.IO) {
            photosDir.deleteRecursively()
            File(context.filesDir, SAFETY_DIR).deleteRecursively()
            File(context.cacheDir, "exports").deleteRecursively()
            File(context.cacheDir, PhotoRepository.CAMERA_DIR).deleteRecursively()
        }
        prefs.setLibraryHash(null)
        prefs.setDefaultPlanSeeded(false)
        prefs.clearBackupTime()
        seeder.seedIfNeeded()
    }

    companion object {
        const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
        const val SAFETY_DIR = "backups"
        const val KEEP_SAFETY_COPIES = 5
        const val KEEP_SAFETY_PHOTO_FOLDERS = 2
        const val JSON_ENTRY = "backup.json"
        const val PHOTO_PREFIX = "photos/"
    }
}
