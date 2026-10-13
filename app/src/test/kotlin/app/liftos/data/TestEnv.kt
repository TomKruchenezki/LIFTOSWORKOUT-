package app.liftos.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.liftos.data.db.AppDatabase
import app.liftos.data.prefs.DevicePrefs
import app.liftos.data.repo.ActivityRepository
import app.liftos.data.repo.BackupRepository
import app.liftos.data.repo.ExerciseRepository
import app.liftos.data.repo.LibraryLoader
import app.liftos.data.repo.MeasurementRepository
import app.liftos.data.repo.PlanRepository
import app.liftos.data.repo.Seeder
import app.liftos.data.repo.SettingsRepository
import app.liftos.data.repo.WorkoutRepository
import app.liftos.domain.logic.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.File
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic ids, unique across the whole test run. */
class TestIds(private val prefix: String = "t") : IdGenerator {
    private val n = AtomicInteger()
    override fun newId(): String = "$prefix-${n.incrementAndGet()}"
}

/**
 * A complete set of repositories on a real Room database (in memory, or a named file so a test can
 * "kill the process" by closing the database and building a new environment on the same file).
 */
class TestEnv(
    val dbName: String? = null,
    startMs: Long = 1_760_000_000_000L,
    idPrefix: String = "t",
    private val prefsFile: File = File.createTempFile("prefs", ".preferences_pb").also { it.delete() },
) {
    val context: Application = ApplicationProvider.getApplicationContext()
    var now: Long = startMs
    val time = TimeSource { now }
    val zones = ZoneSource { ZoneId.of("Asia/Jerusalem") }
    val ids = TestIds(idPrefix)

    val db: AppDatabase = AppDatabase.build(context, dbName)
    val scope = CoroutineScope(Dispatchers.IO + Job())
    val prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })

    val settings = SettingsRepository(db)
    val exercises = ExerciseRepository(db, time, ids)
    val plans = PlanRepository(db, time, ids)
    val workouts = WorkoutRepository(db, time, zones, ids)
    val measurements = MeasurementRepository(db, time, zones, ids)
    val activities = ActivityRepository(db, time, zones, ids)
    val seeder = Seeder(db, LibraryLoader(context), prefs, time, ids)
    val backup = BackupRepository(context, db, seeder, prefs, time, "test")

    fun advance(seconds: Long) {
        now += seconds * 1000
    }

    fun close() {
        db.close()
    }
}
