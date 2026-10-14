package app.liftos.di

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.liftos.data.TimeSource
import app.liftos.domain.model.AppSettings
import app.liftos.data.ZoneSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.prefs.DevicePrefs
import app.liftos.data.repo.ActivityRepository
import app.liftos.data.repo.AnalyticsData
import app.liftos.data.repo.AnalyticsRepository
import app.liftos.data.repo.BackupRepository
import app.liftos.data.repo.ExerciseRepository
import app.liftos.data.repo.GoalRepository
import app.liftos.data.repo.LibraryLoader
import app.liftos.data.repo.MeasurementRepository
import app.liftos.data.repo.PhotoRepository
import app.liftos.data.repo.PlanRepository
import app.liftos.data.repo.ProgressionRepository
import app.liftos.data.repo.Seeder
import app.liftos.data.repo.SettingsRepository
import app.liftos.data.repo.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Hand-written dependency container. For a single-module offline app this is simpler, faster to
 * build and easier to test than a DI framework; every dependency is constructor-injected, so
 * swapping this for Hilt later is mechanical.
 */
class AppContainer(
    val app: Application,
    databaseName: String? = AppDatabase.FILE_NAME,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val time: TimeSource = TimeSource.Default
    val zones: ZoneSource = ZoneSource.Device

    val database: AppDatabase by lazy { AppDatabase.build(app, databaseName) }

    val devicePrefs: DevicePrefs by lazy {
        DevicePrefs(PreferenceDataStoreFactory.create(scope = appScope) { app.preferencesDataStoreFile("device_prefs") })
    }

    val settings by lazy { SettingsRepository(database) }
    val exercises by lazy { ExerciseRepository(database, time) }
    val plans by lazy { PlanRepository(database, time) }
    val workouts by lazy { WorkoutRepository(database, time, zones) }
    val measurements by lazy { MeasurementRepository(database, time, zones) }
    val activities by lazy { ActivityRepository(database, time, zones) }
    val analytics by lazy { AnalyticsRepository(database) }
    val progression by lazy { ProgressionRepository(database, time) }
    val goals by lazy { GoalRepository(database, time) }
    val photos by lazy { PhotoRepository(app, database, time, zones) }
    val goalTracker by lazy { GoalTracker(this) }

    /**
     * Facts, sessions and records for all analytics screens. It is shared and only active while a
     * screen is looking at it (plus a short grace period), so logging sets during a workout does not
     * recompute the whole history.
     */
    val analyticsData: SharedFlow<AnalyticsData> by lazy {
        analytics.observeData().shareIn(appScope, SharingStarted.WhileSubscribed(30_000), replay = 1)
    }

    val seeder by lazy { Seeder(database, LibraryLoader(app), devicePrefs, time) }

    val backup by lazy {
        BackupRepository(app, database, seeder, devicePrefs, time, appVersion = versionName())
    }

    @Volatile
    private var latestSettings: AppSettings = AppSettings()

    /** Last known settings (kept fresh by [start]); lets non-suspend code read a preference. */
    fun settingsSnapshot(): AppSettings = latestSettings

    private val _ready = MutableStateFlow(false)

    /** True once the database exists and has been seeded. Screens wait for this. */
    val ready: StateFlow<Boolean> = _ready

    fun start() {
        appScope.launch {
            seeder.seedIfNeeded()
            _ready.value = true
        }
        appScope.launch {
            settings.observe().collect { latestSettings = it }
        }
        // Reminder alarms follow the settings (and are re-created at every app start).
        appScope.launch {
            settings.observe().map { it.reminders }.distinctUntilChanged().collect {
                runCatching { app.liftos.service.ReminderScheduler.rescheduleAll(app, this@AppContainer) }
            }
        }
    }

    private fun versionName(): String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }
}
