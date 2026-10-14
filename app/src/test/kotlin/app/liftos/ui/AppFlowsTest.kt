package app.liftos.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.liftos.LiftOsApp
import app.liftos.data.repo.MeasurementInput
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.SessionStatus
import app.liftos.ui.nav.LiftOsRoot
import app.liftos.ui.theme.LiftOsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Longer user journeys through the real UI: they catch crashes in screens that only open from inside the app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class AppFlowsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: LiftOsApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>() as LiftOsApp
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { app.container.seeder.seedIfNeeded() }
        compose.setContent {
            LiftOsTheme(AppSettings().themeMode) { LiftOsRoot(app.container, AppSettings()) }
        }
    }

    private fun waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) =
        compose.waitForText(text, substring, timeoutMs)

    private fun gone(text: String, substring: Boolean = false) = !compose.containsText(text, substring)

    @Test
    fun plan_screens_open_down_to_the_exercise_editor() {
        waitForText("התחל אימון")
        compose.onNodeWithText("תוכניות").performClick()
        waitForText("4 ימים בשבוע", substring = true)
        compose.onNodeWithText("4 ימים בשבוע - פלג עליון / תחתון").performClick()

        waitForText("סידור השבוע")
        waitForText("ימי אימון")
        compose.onAllNodesWithText("Upper A").onLast().performClick()

        // The workout editor lists the day's exercises with the drag handles.
        waitForText("לחיצת חזה במוט")
        compose.onAllNodesWithContentDescription("גרור לשינוי סדר").onLast().assertExists()

        // Tap the first exercise: the editor sheet opens with the plan values.
        compose.onNodeWithText("לחיצת חזה במוט").performClick()
        waitForText("מספר סטים")
        waitForText("זמן מנוחה (שניות)")
        compose.onNodeWithText("שמור").performClick()
        compose.waitUntilOrDump("editor sheet closes", 5_000) { gone("מספר סטים") }
    }

    @Test
    fun finishing_a_workout_opens_the_summary_and_lists_it_in_the_journal() {
        waitForText("התחל אימון")
        compose.onNodeWithText("התחל אימון").performClick()
        waitForText("סיים")

        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("60")
        fields[1].performTextInput("8")
        compose.waitUntilOrDump("complete-set button", 5_000) { compose.onAllNodesWithContentDescription("סמן כבוצע").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("סמן כבוצע")[0].performClick()
        compose.waitUntilOrDump("set saved as completed", 5_000) {
            runBlocking { app.container.workouts.observeActive().first() }
                ?.let { runBlocking { app.container.workouts.getDetail(it.id) }!!.exercises[0].sets[0].isCompleted } == true
        }

        // Finish: confirmation dialog, then the summary with the volume 60 x 8 = 480.
        compose.onAllNodesWithText("סיים")[0].performClick()
        waitForText("לסיים את האימון?")
        compose.onNodeWithText("סיים ושמור").performClick()
        waitForText("סיכום אימון")
        waitForText("480", substring = true)
        waitForText("נתח את האימון עם AI")

        // The AI screen shows exactly the report that would be copied: built from the real workout.
        compose.tap("analyze with AI button") { compose.onNodeWithText("נתח את האימון עם AI") }
        waitForText("תצוגה מקדימה של הדוח")
        waitForText("דוח אימון מתוך LiftOS", substring = true)
        waitForText("האפליקציה לא שולחת שום מידע", substring = true)
        compose.onAllNodesWithContentDescription("חזרה").onLast().performClick()
        waitForText("סיכום אימון")

        val history = runBlocking { app.container.workouts.observeHistory().first() }
        assertEquals(1, history.size)
        assertEquals(SessionStatus.COMPLETED, history[0].session.status)
        assertEquals(480.0, history[0].volumeKg, 0.0)
        assertEquals(1, history[0].workingSets)
        assertTrue(runBlocking { app.container.workouts.observeActive().first() } == null)

        // Back to the tabs: the journal shows the new workout.
        compose.onAllNodesWithContentDescription("חזרה").onLast().performClick()
        waitForText("התחל אימון")
        compose.onNodeWithText("יומן").performClick()
        waitForText("יומן אימונים")
        // The month view marks the day; the history view lists the workout with its volume.
        compose.tap("history segment") { compose.onNodeWithText("היסטוריה") }
        compose.waitUntilOrDump("volume shown in the journal") { !gone("נפח", substring = true) }
    }

    @Test
    fun library_row_opens_the_exercise_page_with_honest_video_search() {
        waitForText("התחל אימון")
        compose.onNodeWithText("תוכניות").performClick()
        compose.onNodeWithContentDescription("ספריית תרגילים").performClick()
        waitForText("104 תרגילים")
        compose.onNode(hasSetTextAction()).performTextInput("פלאנק")
        waitForText("(מסונן)", substring = true)
        compose.tapUntilShown("library row", "אופן ביצוע") { compose.onAllNodes(hasText("פלאנק") and hasClickAction() and hasSetTextAction().not())[0] }
        waitForText("חפש הדגמה ביוטיוב")
        waitForText("זהו קישור לחיפוש ביוטיוב, לא סרטון מאומת", substring = true)
        waitForText("טעויות נפוצות")
        waitForText("דגשי בטיחות")
    }

    @Test
    fun a_body_measurement_can_be_added_from_the_body_tab() {
        waitForText("התחל אימון")
        compose.onNodeWithText("גוף").performClick()
        waitForText("עוד אין מדידות")
        // The extended FAB exposes its label only in the unmerged semantics tree.
        compose.tap("new measurement button") { compose.onNodeWithText("מדידה חדשה", useUnmergedTree = true) }
        waitForText("אפשר למלא רק את מה שמדדת הפעם.")
        compose.onNodeWithText("משקל").performTextInput("82.5")
        compose.tap("save measurement", scrollFirst = true) { compose.onNodeWithText("שמור מדידה") }
        compose.waitUntilOrDump("measurement saved") {
            runBlocking { app.container.measurements.observeAllOnce() }.any { it.weightKg == 82.5 }
        }
        waitForText("כל המדידות")
    }

    @Test
    fun an_event_added_in_the_journal_is_saved_and_shown() {
        waitForText("התחל אימון")
        compose.onNodeWithText("יומן").performClick()
        waitForText("יומן אימונים")
        compose.onNodeWithContentDescription("הוסף").performClick()
        compose.tap("new event menu item") { compose.onNodeWithText("אירוע או פעילות חדשים") }
        waitForText("אירוע חדש")
        compose.tap("running chip") { compose.onNodeWithText("ריצה") }
        compose.tap("save event", scrollFirst = true) { compose.onNodeWithText("הוסף ליומן") }
        compose.waitUntilOrDump("event saved") {
            runBlocking { app.container.activities.observeAll().first() }.any { it.type == ActivityType.RUN }
        }
        // The sheet closes and the new event is listed on the selected day (today).
        compose.waitUntilOrDump("event shown on the day") { !compose.containsText("אירוע חדש") && compose.containsText("ריצה") }
    }

    @Test
    fun delete_all_data_needs_a_typed_confirmation_and_resets_the_app() {
        runBlocking { app.container.measurements.add(MeasurementInput(weightKg = 80.0)) }
        waitForText("התחל אימון")
        compose.onNodeWithContentDescription("הגדרות").performClick()
        waitForText("מחיקת נתונים")
        compose.tap("delete all button", scrollFirst = true) { compose.onNodeWithText("מחק את כל הנתונים שלי") }
        // The confirmation field keeps animating its cursor, which would stop Compose from ever being idle in a test:
        // freeze the animation clock and advance it by hand while the dialog is open.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("למחוק את כל הנתונים?").assertExists()
        compose.onNodeWithText("מחק הכול").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("מחק")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("מחק הכול").assertIsEnabled()
        compose.onNodeWithText("מחק הכול").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitUntilOrDump("measurements deleted") { runBlocking { app.container.measurements.observeAllOnce() }.isEmpty() }
        waitForText("כל הנתונים נמחקו", substring = true)
        // Like a fresh install: the library and the starting program are back.
        compose.waitUntilOrDump("starting program restored") { runBlocking { app.container.plans.observePlans().first() }.size == 1 }
    }
}
