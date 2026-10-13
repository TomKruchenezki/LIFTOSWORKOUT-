package app.liftos.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.liftos.LiftOsApp
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
        waitForText("העתק דוח לניתוח ב-ChatGPT")

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
        compose.waitUntilOrDump("volume shown in the journal") { !gone("נפח", substring = true) }
    }

    @Test
    fun library_row_opens_the_exercise_page_with_honest_video_search() {
        waitForText("התחל אימון")
        compose.onNodeWithText("תרגילים").performClick()
        waitForText("104 תרגילים")
        compose.onNode(hasSetTextAction()).performTextInput("פלאנק")
        waitForText("(מסונן)", substring = true)
        compose.onAllNodes(hasText("פלאנק") and hasClickAction())[0].performClick()
        waitForText("אופן ביצוע")
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
        compose.onNodeWithText("מדידה חדשה").performClick()
        waitForText("אפשר למלא רק את מה שמדדת הפעם.")
        compose.onNodeWithText("משקל").performTextInput("82.5")
        compose.onNodeWithText("שמור מדידה").performClick()
        compose.waitUntilOrDump("measurement saved") {
            runBlocking { app.container.measurements.observeAllOnce() }.any { it.weightKg == 82.5 }
        }
        waitForText("כל המדידות")
    }
}
