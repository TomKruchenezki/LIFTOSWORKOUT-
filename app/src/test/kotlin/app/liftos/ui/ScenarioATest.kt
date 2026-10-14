package app.liftos.ui

import android.Manifest
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.liftos.LiftOsApp
import app.liftos.domain.export.TextReport
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.SessionStatus
import app.liftos.ui.nav.LiftOsRoot
import app.liftos.ui.theme.LiftOsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Acceptance scenario A ("a workout at the gym") as one continuous journey through the real UI, step by step as the
 * owner listed them. Steps that are system-level (really switching to another app) or sit behind a bottom-sheet with a
 * text field (replace exercise picker, which cannot go idle in Robolectric) are done at the nearest level that works
 * and are labelled in FINAL_REQUIREMENTS_AUDIT.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ScenarioATest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: LiftOsApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>() as LiftOsApp
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            val c = app.container
            c.seeder.seedIfNeeded()
            // An earlier Upper A (40 x 8 everywhere) so that "previous workout" has something to show.
            val plan = c.plans.getPlanDetail(c.plans.observePlans().first().first().id)!!
            val upperA = plan.workouts.first { it.workout.name == "Upper A" }.workout.id
            val id = c.workouts.createPastWorkout(upperA, "Upper A", System.currentTimeMillis() - 7 * 86_400_000L, 55)
            c.workouts.getDetail(id)!!.exercises.flatMap { it.sets }.forEach { s ->
                c.workouts.setWeight(s.id, 40.0); c.workouts.setReps(s.id, 8); c.workouts.toggleSetCompleted(s.id)
            }
        }
        compose.setContent { LiftOsTheme(AppSettings().themeMode) { LiftOsRoot(app.container, AppSettings()) } }
    }

    private fun waitForText(text: String, substring: Boolean = false) = compose.waitForText(text, substring, 10_000)

    private fun tapText(what: String, text: String, @Suppress("UNUSED_PARAMETER") first: Boolean = false, scroll: Boolean = false) =
        compose.tap(what, scrollFirst = scroll) {
            compose.onAllNodesWithText(text).onFirst()
        }

    /** Home -> the plan's chip -> the plan -> its Upper A workout. */
    private fun openUpperAFromHome() {
        compose.swipeUntilDisplayed("4 ימים בשבוע - פלג עליון / תחתון")
        tapText("plan chip", "4 ימים בשבוע - פלג עליון / תחתון")
        waitForText("סידור השבוע")
        waitForText("ימי אימון")
        compose.tap("Upper A in the plan") { compose.onAllNodesWithText("Upper A").onLast() }
    }

    private fun goHome() {
        repeat(5) {
            if (compose.containsText("התחל אימון")) return
            back()
            compose.waitForIdle()
        }
        waitForText("התחל אימון")
    }

    private fun back() = compose.onAllNodesWithContentDescription("חזרה").onLast().performClick()

    private fun active() = runBlocking { app.container.workouts.observeActive().first() }
    private fun activeDetail() = runBlocking { app.container.workouts.getDetail(active()!!.id)!! }

    @Test
    fun a_full_workout_from_opening_the_app_to_the_next_session() {
        // A1 open LiftOS
        waitForText("התחל אימון")

        // A2 choose Upper A (home -> plan -> workout)
        openUpperAFromHome()

        // A3 start the workout
        compose.scrollListTo("התחל אימון זה")
        tapText("start this workout", "התחל אימון זה")
        waitForText("סיים")
        assertNotNull("a workout is running", active())

        // A4 the previous workout is visible next to the sets
        waitForText("קודם (", substring = true)
        waitForText("40×8", substring = true)

        // A5-A7 weight, reps and RIR of the first set (the fields are pre-filled from last time, so replace them)
        compose.waitUntilOrDump("set fields", 10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 3 }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("60")
        fields[1].performTextReplacement("8")
        fields[2].performTextReplacement("2")

        // A8 mark the set done
        compose.waitUntilOrDump("complete button", 10_000) {
            compose.onAllNodesWithContentDescription("סמן כבוצע").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("סמן כבוצע")[0].performClick()

        // A9 the rest timer starts
        compose.waitUntilOrDump("rest bar", 10_000) {
            compose.onAllNodesWithContentDescription("דלג על המנוחה").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntilOrDump("first set saved", 10_000) { activeDetail().exercises[0].sets[0].isCompleted }
        val first = activeDetail().exercises[0].sets[0]
        assertEquals(60.0, first.weightKg!!, 0.0)
        assertEquals(8, first.reps)
        assertEquals(2, first.rir)
        assertNotNull("rest timer is running", activeDetail().session.rest)

        // A10-A11 leave the app and come back (activity lifecycle only; a real switch to another app is not testable here)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForText("סיים")
        compose.waitUntilOrDump("rest bar after returning", 10_000) {
            compose.onAllNodesWithContentDescription("דלג על המנוחה").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("nothing was lost", 60.0, activeDetail().exercises[0].sets[0].weightKg!!, 0.0)

        // A12 add a set to the first exercise
        val setsBefore = activeDetail().exercises[0].sets.size
        tapText("add set", "הוסף סט", first = true, scroll = true)
        compose.waitUntilOrDump("set added", 10_000) { activeDetail().exercises[0].sets.size == setsBefore + 1 }

        // A13 change an exercise - done through the repository: the picker is a bottom sheet with a text field (see class doc)
        val slot = activeDetail().exercises[1].exercise
        val replacement = runBlocking { app.container.exercises.get("machine-chest-press")!! }
        runBlocking { app.container.workouts.replaceExercise(slot.id, replacement) }
        waitForText(replacement.nameHe, substring = true)

        // A15 notes for the whole workout (repository; the debounced notes field is not typed into by this test)
        runBlocking { app.container.workouts.setSessionNotes(active()!!.id, "הרגשה טובה, כתף ימין מעט רגישה") }

        // A14 + A16 finish and save
        val sessionId = active()!!.id
        compose.onAllNodesWithText("סיים")[0].performClick()
        waitForText("לסיים את האימון?")
        tapText("finish and save", "סיים ושמור")
        waitForText("סיכום אימון")
        compose.waitUntilOrDump("saved as completed", 10_000) {
            runBlocking { app.container.workouts.getDetail(sessionId) }!!.session.status == SessionStatus.COMPLETED
        }
        assertNull(active())
        waitForText("הרגשה טובה", substring = true)

        // A19 copy the report for ChatGPT (done here on the summary, where the button lives)
        tapText("copy report for ChatGPT", "העתק דוח לניתוח ב-ChatGPT", scroll = true)
        waitForText("הדוח הועתק ללוח", substring = true)
        val clip = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip.contains("הרגשה טובה"))
        assertTrue("the previous workout is in the report", clip.contains("האימון הקודם"))
        assertTrue(clip.trimEnd().endsWith(TextReport.AI_INSTRUCTION.trimEnd()))

        // A17 the workout is in the journal (history view). The workout was started from the plan screen, so "back" first
        // leads through the plan screens.
        goHome()
        tapText("journal tab", "יומן")
        waitForText("יומן אימונים")
        tapText("history view", "היסטוריה")
        compose.waitUntilOrDump("two Upper A rows", 10_000) {
            compose.onAllNodesWithText("Upper A", substring = true).fetchSemanticsNodes().size >= 2
        }

        // A18 analytics follow: the data holds both workouts and the progress tab has content
        val data = runBlocking { app.container.analyticsData.first() }
        assertEquals(2, data.sessions.size)
        tapText("progress tab", "התקדמות")
        waitForText("התקדמות לפי תרגיל")
        assertFalse(compose.containsText("עוד אין נתוני אימונים"))

        // A20 next time: the same workout starts with last time's numbers, not completed
        tapText("home tab", "בית")
        waitForText("התחל אימון")
        openUpperAFromHome()
        compose.scrollListTo("התחל אימון זה")
        tapText("start this workout", "התחל אימון זה")
        waitForText("קודם (", substring = true)
        waitForText("60×8", substring = true)
        val next = activeDetail().exercises[0].sets[0]
        assertEquals("last time's weight is loaded", 60.0, next.weightKg!!, 0.0)
        assertEquals(8, next.reps)
        assertFalse("but nothing is pre-ticked", next.isCompleted)
    }
}
