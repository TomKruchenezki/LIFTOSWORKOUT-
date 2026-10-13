package app.liftos.data.repo

import androidx.room.withTransaction
import app.liftos.data.TimeSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toEntity
import app.liftos.data.prefs.DevicePrefs
import app.liftos.domain.library.DefaultPlan
import app.liftos.domain.library.Library
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.model.AppSettings

/**
 * Fills the database on first launch: the settings row, the built-in exercise library and the
 * owner's starting program. It is idempotent and safe to run on every start.
 *
 *  - The library is re-applied whenever its content hash changes (app update with new exercises).
 *    Only built-in rows are touched; user-created exercises are never modified.
 *  - The default plan is created exactly once (a flag in device prefs), so deleting it is permanent.
 */
class Seeder(
    private val db: AppDatabase,
    private val loader: LibraryLoader,
    private val prefs: DevicePrefs,
    private val time: TimeSource = TimeSource.Default,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    suspend fun seedIfNeeded() {
        val library = loader.load()
        val needsLibrary = prefs.libraryHash() != library.contentHash
        val needsPlan = !prefs.defaultPlanSeeded()

        db.withTransaction {
            db.settingsDao().insertIfAbsent(AppSettings().toEntity())
            if (needsLibrary) upsertBuiltIns(library)
            if (needsPlan && db.planDao().planCount() == 0) insertDefaultPlan()
        }
        if (needsLibrary) prefs.setLibraryHash(library.contentHash)
        if (needsPlan) prefs.setDefaultPlanSeeded(true)
    }

    /** Inserts/updates every built-in exercise. Called by seeding and after a full restore. */
    suspend fun upsertBuiltIns(library: Library = loader.load()) {
        val now = time.nowMs()
        val dao = db.exerciseDao()
        val existing = dao.getMany(library.exercises.map { it.id }).associateBy { it.id }
        dao.upsertAll(
            library.exercises.map { e ->
                e.copy(
                    isBuiltIn = true,
                    isArchived = existing[e.id]?.isArchived ?: false,
                    createdAt = existing[e.id]?.createdAt ?: now,
                    updatedAt = now,
                ).toEntity()
            },
        )
    }

    private suspend fun insertDefaultPlan() {
        val now = time.nowMs()
        val plan = DefaultPlan.create(now, ids)
        val dao = db.planDao()
        dao.upsertPlan(plan.plan.toEntity())
        plan.workouts.forEach { w ->
            dao.upsertWorkout(w.workout.toEntity())
            dao.upsertPlannedExercises(w.exercises.map { it.toEntity() })
        }
        val settings = db.settingsDao()
        val current = settings.get() ?: AppSettings().toEntity()
        settings.upsert(current.copy(activePlanId = plan.plan.id))
    }
}
