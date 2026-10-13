package app.liftos.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.liftos.data.TimeSource
import app.liftos.data.ZoneSource
import app.liftos.data.db.AppDatabase
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
import app.liftos.domain.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ImportPreview {
    data class Failure(val message: String) : ImportPreview

    data class Ready(
        val file: BackupFile,
        val source: BackupSource,
        val parseWarnings: List<String>,
        val report: ValidationReport,
    ) : ImportPreview {
        val canImport: Boolean get() = report.isValid
        val warnings: List<String> get() = parseWarnings + report.warnings
        val summary: BackupSummary get() = report.summary
    }
}

data class ImportResult(
    val mode: ImportMode,
    val imported: BackupSummary,
    val skippedActiveSessions: Int,
    /** Path of the automatic safety copy of the data that existed before the import. */
    val safetyCopyName: String?,
)

class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val seeder: Seeder,
    private val prefs: DevicePrefs,
    private val time: TimeSource = TimeSource.Default,
    private val appVersion: String = "",
) {

    // ---- export ------------------------------------------------------------------------------

    /** Consistent snapshot of every table (single read transaction). */
    suspend fun snapshot(): BackupFile = db.withTransaction {
        BackupFile(
            exportedAt = time.nowMs(),
            appVersion = appVersion,
            data = BackupData(
                exercises = db.exerciseDao().getAll().map { it.toDomain() },
                plans = db.planDao().getPlans().map { it.toDomain() },
                plannedWorkouts = db.planDao().getAllWorkouts().map { it.toDomain() },
                plannedExercises = db.planDao().getAllPlannedExercises().map { it.toDomain() },
                sessions = db.sessionDao().getAllSessions().map { it.toDomain() },
                sessionExercises = db.sessionDao().getAllSessionExercises().map { it.toDomain() },
                sets = db.sessionDao().getAllSets().map { it.toDomain() },
                measurements = db.measurementDao().getAll().map { it.toDomain() },
                events = db.activityDao().getAll().map { it.toDomain() },
                settings = db.settingsDao().get()?.toDomain() ?: AppSettings(),
            ),
        )
    }

    suspend fun exportJson(): String = BackupCodec.encode(snapshot())

    suspend fun markExported() = prefs.setLastBackupAt(time.nowMs())

    // ---- file access -----------------------------------------------------------------------------

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("לא ניתן לפתוח את הקובץ")
        input.use { stream ->
            val bytes = stream.readNBytes(MAX_IMPORT_BYTES + 1)
            if (bytes.size > MAX_IMPORT_BYTES) error("הקובץ גדול מדי (מעל ${MAX_IMPORT_BYTES / 1_000_000}MB)")
            String(bytes, Charsets.UTF_8)
        }
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("לא ניתן לכתוב לקובץ")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    // ---- import ----------------------------------------------------------------------------------

    /** Parses and validates a backup without touching the database. */
    suspend fun preview(text: String): ImportPreview {
        val library = db.exerciseDao().getAll().map { it.toDomain() }
        val zone = ZoneSource.Device.zone()
        return when (val parsed = BackupCodec.parse(text, library, zone, time.nowMs())) {
            is ParseResult.Failure -> ImportPreview.Failure(parsed.message)
            is ParseResult.Success -> {
                val known = library.map { it.id }.toSet()
                ImportPreview.Ready(parsed.file, parsed.source, parsed.warnings, BackupValidator.validate(parsed.file, known))
            }
        }
    }

    /**
     * Applies a previewed backup. A safety copy of the current data is written first, and the
     * whole operation is one database transaction: it either completes or changes nothing.
     */
    suspend fun import(preview: ImportPreview.Ready, mode: ImportMode): ImportResult {
        require(preview.canImport) { "Backup failed validation" }
        val safety = writeSafetyCopy()
        var skippedActive = 0
        db.withTransaction {
            val data = preview.report.cleaned
            when (mode) {
                ImportMode.REPLACE_ALL -> replaceAll(data)
                ImportMode.MERGE -> skippedActive = merge(data)
            }
            fixDanglingSettings()
        }
        return ImportResult(mode, preview.summary, skippedActive, safety?.name)
    }

    private suspend fun replaceAll(data: BackupData) {
        // Children first, so no foreign key is ever violated.
        db.sessionDao().deleteAllSets()
        db.sessionDao().deleteAllSessionExercises()
        db.sessionDao().deleteAllSessions()
        db.planDao().deleteAllPlannedExercises()
        db.planDao().deleteAllWorkouts()
        db.planDao().deleteAllPlans()
        db.exerciseDao().deleteAll()
        db.measurementDao().deleteAll()
        db.activityDao().deleteAll()
        db.settingsDao().deleteAll()

        // Parents first. Built-ins are inserted before the backup's rows so every reference resolves.
        seeder.upsertBuiltIns()
        insert(data)
        val settings = data.settings ?: AppSettings()
        db.settingsDao().upsert(settings.toEntity())
    }

    /** Adds only what is missing. Returns how many active workouts were left out. */
    private suspend fun merge(data: BackupData): Int {
        val existing = ExistingIds(
            exercises = db.exerciseDao().allIds().toSet(),
            plans = db.planDao().planIds().toSet(),
            plannedWorkouts = db.planDao().workoutIds().toSet(),
            plannedExercises = db.planDao().plannedExerciseIds().toSet(),
            sessions = db.sessionDao().sessionIds().toSet(),
            sessionExercises = db.sessionDao().sessionExerciseIds().toSet(),
            sets = db.sessionDao().setIds().toSet(),
            measurements = db.measurementDao().ids().toSet(),
            events = db.activityDao().ids().toSet(),
        )
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
        return activeIds.size
    }

    private suspend fun insert(d: BackupData) {
        db.exerciseDao().upsertAll(d.exercises.map { it.toEntity() })
        db.planDao().upsertPlans(d.plans.map { it.toEntity() })
        db.planDao().upsertWorkouts(d.plannedWorkouts.map { it.toEntity() })
        db.planDao().upsertPlannedExercises(d.plannedExercises.map { it.toEntity() })
        db.sessionDao().upsertSessions(d.sessions.map { it.toEntity() })
        db.sessionDao().upsertSessionExercises(d.sessionExercises.map { it.toEntity() })
        d.sets.chunked(500).forEach { chunk -> db.sessionDao().upsertSets(chunk.map { it.toEntity() }) }
        db.measurementDao().upsertAll(d.measurements.map { it.toEntity() })
        db.activityDao().upsertAll(d.events.map { it.toEntity() })
    }

    private suspend fun fixDanglingSettings() {
        val dao = db.settingsDao()
        val s = dao.get() ?: AppSettings().toEntity().also { dao.upsert(it) }
        val plan = s.activePlanId
        if (plan != null && db.planDao().getPlan(plan) == null) dao.upsert(s.copy(activePlanId = null))
    }

    // ---- safety copies ---------------------------------------------------------------------------

    /** Writes the current data to app-private storage before anything destructive. Keeps the last few. */
    suspend fun writeSafetyCopy(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, SAFETY_DIR).apply { mkdirs() }
            val file = File(dir, "pre-import-${time.nowMs()}.json")
            file.writeText(exportJson(), Charsets.UTF_8)
            dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP_SAFETY_COPIES)?.forEach { it.delete() }
            file
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
        const val SAFETY_DIR = "backups"
        const val KEEP_SAFETY_COPIES = 5
    }
}
