package app.liftos.data

import android.database.sqlite.SQLiteDatabase
import app.liftos.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Data safety across app restarts and database versions. Until a second schema version exists these
 * tests guard the rules that make future upgrades safe: data persists on disk, schema files are
 * committed, every version step has a migration, and an unexpected version never erases data.
 */
@RunWith(RobolectricTestRunner::class)
class DurabilityTest {

    @Test
    fun `data written before a restart is still there after it`() = runBlocking {
        val name = "durable.db"
        val first = TestEnv(dbName = name, idPrefix = "p1")
        first.seeder.seedIfNeeded()
        first.measurements.add(app.liftos.data.repo.MeasurementInput(weightKg = 81.5, notes = "שמור"))
        first.close()

        val second = TestEnv(dbName = name, idPrefix = "p2")
        val m = second.measurements.observeAll().first().single()
        assertEquals(81.5, m.weightKg!!, 0.0)
        assertEquals("שמור", m.notes)
        assertTrue(second.exercises.getAll().size >= 100)
        second.close()
    }

    @Test
    fun `there is a migration for every version step`() {
        val from = AppDatabase.MIGRATIONS.map { it.startVersion }.sorted()
        val to = AppDatabase.MIGRATIONS.map { it.endVersion }.sorted()
        // Contiguous chain 1 -> 2 -> ... -> VERSION (empty while VERSION == 1).
        assertEquals((1 until AppDatabase.VERSION).toList(), from)
        assertEquals((2..AppDatabase.VERSION).toList(), to)
    }

    @Test
    fun `the exported schema for the current version is committed`() {
        val candidates = listOf(File("schemas"), File("app/schemas"))
        val root = candidates.firstOrNull { it.isDirectory }
        requireNotNull(root) { "schemas directory not found - run the build once and commit app/schemas" }
        val file = File(root, "app.liftos.data.db.AppDatabase/${AppDatabase.VERSION}.json")
        assertTrue("missing ${file.path}", file.exists())
        assertTrue(file.readText().contains("\"version\": ${AppDatabase.VERSION}"))
    }

    @Test
    fun `an unexpected newer database version fails safely and does not erase data`() = runBlocking {
        val name = "newer.db"
        val env = TestEnv(dbName = name, idPrefix = "n1")
        env.seeder.seedIfNeeded()
        env.measurements.add(app.liftos.data.repo.MeasurementInput(weightKg = 90.0))
        env.close()

        val file = env.context.getDatabasePath(name)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = AppDatabase.VERSION + 5 }

        val opened = runCatching {
            val db = AppDatabase.build(env.context, name)
            db.measurementDao().getAll() // forces the open
        }
        assertTrue("opening a newer-version database must fail, not silently reset it", opened.isFailure)

        // The rows are untouched on disk.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT COUNT(*) FROM body_measurements", null).use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun `foreign keys are enforced by the database`() = runBlocking {
        val env = TestEnv()
        val result = runCatching {
            env.db.sessionDao().upsertSets(
                listOf(
                    app.liftos.data.db.ExerciseSetEntity(
                        id = "orphan", sessionExerciseId = "nope", orderIndex = 0, setType = app.liftos.domain.model.SetType.WORKING,
                        side = null, weightKg = 1.0, reps = 1, durationSec = null, rir = null, rpe = null,
                        isCompleted = false, isFailure = false, completedAt = null, notes = "",
                    ),
                ),
            )
        }
        assertTrue("orphan rows must be rejected", result.isFailure)
        env.close()
    }
}
