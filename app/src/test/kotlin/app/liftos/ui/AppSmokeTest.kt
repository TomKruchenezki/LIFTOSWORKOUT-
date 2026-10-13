package app.liftos.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.liftos.LiftOsApp
import app.liftos.domain.model.AppSettings
import app.liftos.ui.nav.LiftOsRoot
import app.liftos.ui.theme.LiftOsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app (theme, navigation, Room, seeded default plan) on the JVM and drives the main
 * screens like a user would. This is what "the main screens work" means in CI.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class AppSmokeTest {
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

    private fun waitForDescription(text: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun home_shows_the_next_workout_with_a_start_button() {
        waitForText("התחל אימון")
        compose.onNodeWithText("התחל אימון").assertIsDisplayed()
        // The default plan's workouts are Upper A / Lower A / Upper B / Lower B - one of them is "next".
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Upper", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Lower", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Lower cards are below the fold of the small test screen: they must exist, not necessarily be on screen.
        compose.onNodeWithText("אימונים השבוע").assertExists()
        compose.onNodeWithText("עוד לא תועד משקל").assertExists()
    }

    @Test
    fun bottom_tabs_open_their_screens() {
        waitForText("התחל אימון")

        compose.onNodeWithText("תוכניות").performClick()
        waitForText("4 ימים בשבוע", substring = true)

        compose.onNodeWithText("תרגילים").performClick()
        waitForText("ספריית תרגילים")
        waitForText("104 תרגילים")

        compose.onNodeWithText("יומן").performClick()
        waitForText("עוד אין אימונים ביומן")

        compose.onNodeWithText("גוף").performClick()
        waitForText("עוד אין מדידות")
    }

    @Test
    fun library_search_filters_the_list() {
        waitForText("התחל אימון")
        compose.onNodeWithText("תרגילים").performClick()
        waitForText("104 תרגילים")
        compose.onNode(hasSetTextAction()).performTextInput("סקוואט")
        waitForText("(מסונן)", substring = true)
        // The result count shrank from the full library; the matching rows themselves are lazily composed.
        compose.onAllNodesWithText("104 תרגילים").assertCountEquals(0)
    }

    @Test
    fun a_workout_can_be_started_and_a_set_logged_which_starts_the_rest_timer() {
        waitForText("התחל אימון")
        compose.onNodeWithText("התחל אימון").performClick()

        // Workout screen: finish button, add-set buttons and the numeric fields are there.
        waitForText("סיים")
        waitForText("הוסף סט")
        compose.onAllNodesWithText("הוסף סט")[0].assertIsDisplayed()

        // Fields in the first set row: weight, reps, effort (in that order).
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("60")
        fields[1].performTextInput("8")

        waitForDescription("סמן כבוצע")
        compose.onAllNodesWithContentDescription("סמן כבוצע")[0].performClick()

        // Completing a working set starts the rest bar (it offers +15 / +30).
        waitForText("15", substring = true)
        compose.onNodeWithContentDescription("דלג על המנוחה").assertIsDisplayed()

        // And the typed values really reached the database (auto-save): weight, reps, completed, rest running.
        val container = app.container
        val activeId = runBlocking { container.workouts.observeActive().first() }!!.id
        compose.waitUntil(10_000) {
            runBlocking { container.workouts.getDetail(activeId) }!!.exercises[0].sets[0].isCompleted
        }
        val saved = runBlocking { container.workouts.getDetail(activeId) }!!
        val first = saved.exercises[0].sets[0]
        assertEquals(60.0, first.weightKg!!, 0.0)
        assertEquals(8, first.reps)
        assertNotNull(saved.session.rest)
    }

    @Test
    fun settings_open_from_home() {
        waitForText("התחל אימון")
        compose.onNodeWithContentDescription("הגדרות").performClick()
        waitForText("גיבוי וייצוא נתונים")
        compose.onNodeWithText("שמור גיבוי מלא לקובץ (JSON)").assertExists()
    }
}
