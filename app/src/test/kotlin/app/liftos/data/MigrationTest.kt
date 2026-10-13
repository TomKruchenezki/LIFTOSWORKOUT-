package app.liftos.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.GoalEntity
import app.liftos.data.db.toDomain
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.EffortMode
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.GoalStatus
import app.liftos.domain.model.GoalType
import app.liftos.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A real upgrade: the version 1 database that LiftOS 0.1.0 created is rebuilt from its exact SQL, filled
 * with data, and opened by the current code. If any migration step lost or damaged data, or if the migrated
 * schema differed from what Room expects, this fails.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val name = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        createVersion1Database()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createVersion1Database() {
        val helper = object : SQLiteOpenHelper(context, name, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                V1Schema.statements.forEach(db::execSQL)
                V1Schema.sampleData.forEach(db::execSQL)
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun `upgrading from version 1 keeps every row and adds sensible defaults`() = runBlocking {
        val db = AppDatabase.build(context, name)
        try {
            // Existing data is intact.
            val session = db.sessionDao().getSession("s1")!!
            assertEquals("Upper A", session.title)
            assertEquals("הערה ישנה", session.notes)
            assertEquals(4, session.energyRating)
            assertEquals(3, session.moodRating)
            assertNull(session.difficultyRating)
            assertNull(session.sleepHours)
            assertEquals(2, db.sessionDao().getSets("se1").size)
            assertEquals(9, db.sessionDao().getSet("st2")!!.reps)

            val measurement = db.measurementDao().get("m1")!!
            assertEquals(84.5, measurement.weightKg!!, 0.0)
            assertEquals("", measurement.conditions)
            assertNull(measurement.shouldersCm)

            // A logged activity stays "done"; the new fields have neutral defaults.
            val event = db.activityDao().get("ev1")!!
            assertEquals(ActivityType.WALK, event.type)
            assertEquals(EventStatus.DONE, event.status)
            assertFalse(event.allDay)
            assertEquals("", event.recurrence)
            assertNull(event.plannedWorkoutId)

            // Settings survive, including the user's choices; reminders start at their defaults.
            val settings = db.settingsDao().get()!!.toDomain()
            assertEquals(ThemeMode.LIGHT, settings.themeMode)
            assertEquals(EffortMode.RPE, settings.effortMode)
            assertEquals(200, settings.defaultRestCompoundSec)
            assertEquals("plan", settings.activePlanId)
            assertFalse(settings.reminders.anyScheduled)

            // Plans and exercises are untouched.
            assertEquals(1, db.planDao().getPlans().size)
            assertEquals(1, db.planDao().getAllPlannedExercises().size)
            assertNotNull(db.exerciseDao().get("bench"))

            // No foreign key is broken by the migration.
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }

            // Analytics can read the old workout, and the new tables accept data.
            val rows = db.analyticsDao().getFacts()
            assertEquals(listOf("st1", "st2"), rows.map { it.setId })
            assertEquals("Barbell Bench Press", rows.first().exerciseNameEn)
            db.goalDao().upsert(
                GoalEntity("g1", "יעד", GoalType.BODY_WEIGHT, "weight", "ק״ג", 90.0, 80.0, null, "2025-10-01", null, GoalStatus.ACTIVE, null, "", 1, 1),
            )
            assertEquals(1, db.goalDao().observeAll().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `reopening the migrated database works and is stable`() = runBlocking {
        AppDatabase.build(context, name).also { it.sessionDao().getAllSessions(); it.close() }
        val again = AppDatabase.build(context, name)
        try {
            assertEquals(1, again.sessionDao().getAllSessions().size)
            assertEquals(2, again.sessionDao().getAllSets().size)
        } finally {
            again.close()
        }
    }
}
