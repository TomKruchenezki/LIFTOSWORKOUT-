package app.liftos.ui

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.liftos.LiftOsApp
import app.liftos.data.repo.MeasurementInput
import app.liftos.domain.export.TextReport
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalType
import app.liftos.domain.model.PhotoAngle
import app.liftos.domain.model.ThemeMode
import app.liftos.ui.analytics.ChartChoice
import app.liftos.ui.nav.LiftOsRoot
import app.liftos.ui.theme.LiftOsTheme
import app.liftos.ui.workout.SummaryViewModel
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
import java.io.File
import java.time.LocalDate

/**
 * Opens every main screen of the real app on a database that holds five finished workouts, two weigh-ins, a goal and
 * two progress photos, and checks what the user would see. This is the "does every screen render with real data"
 * evidence for the acceptance audit; it runs on the Robolectric Android framework, not on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ScreenSweepTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: LiftOsApp
    private lateinit var firstExerciseName: String
    private var layoutDirection: LayoutDirection? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>() as LiftOsApp
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { app.container.seeder.seedIfNeeded() }
        seed()
    }

    private fun show(fontScale: Float? = null, mode: ThemeMode = AppSettings().themeMode) {
        compose.setContent {
            val content = @androidx.compose.runtime.Composable {
                LiftOsTheme(mode) {
                    // The theme sets the layout direction, so it can only be read from inside it.
                    layoutDirection = LocalLayoutDirection.current
                    LiftOsRoot(app.container, AppSettings())
                }
            }
            if (fontScale == null) content() else {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { content() }
            }
        }
    }

    private fun seed() = runBlocking {
        val c = app.container
        val plan = c.plans.getPlanDetail(c.plans.observePlans().first().first().id)!!
        val upperA = plan.workouts.first { it.workout.name == "Upper A" }.workout.id
        val now = System.currentTimeMillis()
        repeat(5) { i ->
            val daysAgo = (5 - i) * 6L
            val id = c.workouts.createPastWorkout(upperA, "Upper A", now - daysAgo * 86_400_000L, 55)
            val detail = c.workouts.getDetail(id)!!
            if (i == 0) firstExerciseName = detail.exercises[0].exercise.nameHe
            detail.exercises.flatMap { it.sets }.forEach { s ->
                c.workouts.setWeight(s.id, 40.0 + 2.5 * i)
                c.workouts.setReps(s.id, 8)
                c.workouts.toggleSetCompleted(s.id)
            }
        }
        c.measurements.add(MeasurementInput(weightKg = 83.0), now - 20 * 86_400_000L)
        c.measurements.add(MeasurementInput(weightKg = 82.0, waistCm = 90.0), now - 2 * 86_400_000L)
        c.goals.create(
            Goal(
                id = "", title = "ירידה ל-80", type = GoalType.BODY_WEIGHT, metricKey = "weight", unit = "ק״ג",
                startValue = 85.0, targetValue = 80.0, startDate = LocalDate.now().minusDays(30).toString(),
            ),
        )
        listOf(PhotoAngle.FRONT to 14L, PhotoAngle.FRONT to 1L).forEachIndexed { i, (angle, days) ->
            val f = File(app.cacheDir, "seed$i.jpg")
            val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            c.photos.addFromFile(f, angle, now - days * 86_400_000L, 82.0, "")
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) = compose.waitForText(text, substring, 10_000)

    private fun assertShown(text: String, substring: Boolean = false) {
        assertTrue("\"$text\" should be on screen | SCREEN: ${compose.screenDump()}", compose.containsText(text, substring))
    }

    private fun tapText(what: String, text: String, first: Boolean = false, scroll: Boolean = false) =
        compose.tap(what, scrollFirst = scroll) {
            if (first) compose.onAllNodesWithText(text).onFirst() else compose.onNodeWithText(text)
        }

    private fun back() = compose.onAllNodesWithContentDescription("חזרה").onLast().performClick()

    // ---- the app frame ----------------------------------------------------------------------------------

    @Test
    fun the_whole_app_is_laid_out_right_to_left() {
        show()
        waitForText("התחל אימון")
        assertEquals(LayoutDirection.Rtl, layoutDirection)
    }

    @Test
    fun the_light_theme_renders_the_home_screen_and_a_workout() {
        show(mode = ThemeMode.LIGHT)
        waitForText("התחל אימון")
        compose.onNodeWithText("התחל אימון").performClick()
        waitForText("סיים")
        waitForText("הוסף סט")
    }

    @Test
    fun home_and_a_new_workout_stay_usable_with_a_very_large_font() {
        show(fontScale = 2f)
        waitForText("התחל אימון")
        compose.onNodeWithText("התחל אימון").performClick()
        waitForText("סיים")
        waitForText("הוסף סט")
        assertShown("סיים")
    }

    @Test
    fun home_shows_the_data_of_the_week() {
        show()
        waitForText("התחל אימון")
        assertShown("אימונים השבוע")
        // The latest weigh-in appears instead of the empty-state text.
        assertTrue("the weigh-in empty state is gone", !compose.containsText("עוד לא תועד משקל"))
    }

    @Test
    fun the_main_buttons_are_big_enough_for_a_thumb() {
        show()
        waitForText("התחל אימון")
        compose.onNodeWithText("התחל אימון").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("התחל אימון").performClick()
        waitForText("סיים")
        waitForText("הוסף סט")
        compose.waitUntilOrDump("complete-set button", 10_000) {
            compose.onAllNodesWithContentDescription("סמן כבוצע").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("סמן כבוצע")[0].assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(40.dp)
        compose.onAllNodesWithText("הוסף סט")[0].assertHeightIsAtLeast(40.dp)
    }

    // ---- progress ---------------------------------------------------------------------------------------

    @Test
    fun the_progress_tab_shows_charts_muscles_insights_and_the_exercise_list() {
        show()
        waitForText("התחל אימון")
        tapText("progress tab", "התקדמות")
        waitForText("גרפים")
        listOf("תקופה", "סטים לפי קבוצת שרירים", "התקדמות לפי תרגיל").forEach { assertShown(it) }
        assertShown(firstExerciseName)
    }

    @Test
    fun every_chart_choice_can_be_selected() {
        show()
        waitForText("התחל אימון")
        tapText("progress tab", "התקדמות")
        waitForText("גרפים")
        ChartChoice.entries.forEach { choice ->
            tapText("chart choice ${choice.labelHe}", choice.labelHe, first = true, scroll = true)
            // The selected choice becomes the title of the chart card, so its label is now on screen twice.
            compose.waitUntilOrDump("chart ${choice.labelHe} selected", 5_000) {
                compose.onAllNodesWithText(choice.labelHe).fetchSemanticsNodes().size >= 2
            }
        }
    }

    @Test
    fun the_shortcuts_open_records_goals_and_recovery() {
        show()
        waitForText("התחל אימון")
        tapText("progress tab", "התקדמות")
        waitForText("גרפים")

        tapText("records shortcut", "שיאים אישיים", first = true)
        waitForText("שיאים אחרונים")
        assertShown("כל השיאים לפי תרגיל")
        back()

        waitForText("גרפים")
        tapText("goals shortcut", "יעדים (1)")
        waitForText("ירידה ל-80")
        back()

        waitForText("גרפים")
        tapText("recovery shortcut", "התאוששות", first = true)
        waitForText("עומס שבועי (7 ימים מתגלגלים)")
    }

    @Test
    fun the_exercise_screen_shows_average_working_weight_and_the_volume_of_each_set() {
        show()
        waitForText("התחל אימון")
        tapText("progress tab", "התקדמות")
        waitForText("התקדמות לפי תרגיל")
        compose.onNode(hasSetTextAction()).performTextInput(firstExerciseName.take(3))
        compose.waitUntilOrDump("exercise row is listed", 10_000) { compose.containsText(firstExerciseName) }
        tapText("exercise row", firstExerciseName, scroll = true)
        waitForText("היסטוריית הסטים")
        assertShown("מדרגת משקל להצעות")
        assertShown("משקל עבודה ממוצע", substring = true)
        assertShown("נפח לכל סט:", substring = true)
        assertShown("חזרות בכל משקל")
        // Five workouts with 3 sets at the top of the range each time: the engine has enough to make a suggestion.
        assertShown("הצעה לאימון הבא")
    }

    // ---- journal ----------------------------------------------------------------------------------------

    @Test
    fun all_five_journal_views_open_and_the_history_lists_the_workouts() {
        show()
        waitForText("התחל אימון")
        tapText("journal tab", "יומן")
        waitForText("יומן אימונים")
        listOf("שבוע", "יום", "רשימה", "חודש").forEach { view ->
            tapText("journal view $view", view, first = true)
            waitForText("יומן אימונים")
        }
        tapText("history view", "היסטוריה")
        waitForText("Upper A", substring = true)
        assertTrue(compose.onAllNodesWithText("Upper A", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun a_workout_from_the_history_opens_its_summary_and_the_ai_screen() {
        show()
        waitForText("התחל אימון")
        tapText("journal tab", "יומן")
        waitForText("יומן אימונים")
        tapText("history view", "היסטוריה")
        waitForText("Upper A", substring = true)
        compose.tap("first workout row") { compose.onAllNodesWithText("Upper A", substring = true).onFirst() }
        waitForText("סיכום אימון")
        waitForText("נתח את האימון עם AI")
        assertShown("העתק דוח לניתוח ב-ChatGPT")

        tapText("analyze with AI", "נתח את האימון עם AI", scroll = true)
        waitForText("תצוגה מקדימה של הדוח")
        assertShown("פתח ChatGPT")
        assertShown("פתח Claude")
    }

    @Test
    fun the_quick_copy_report_of_a_workout_has_its_records_its_trends_and_the_instruction() {
        val newest = runBlocking { app.container.workouts.observeHistory().first() }.maxByOrNull { it.session.startedAt }!!
        val text = runBlocking { SummaryViewModel(app.container, newest.session.id).aiReportText() }!!
        assertTrue(text.contains("## שיאים אישיים שנקבעו באימון הזה"))
        assertTrue(text.contains("## מגמות רלוונטיות"))
        assertTrue("it names the first exercise", text.contains(firstExerciseName))
        assertTrue("the instruction closes the report", text.trimEnd().endsWith(TextReport.AI_INSTRUCTION.trimEnd()))
        assertTrue("body data is not included unless asked", !text.contains("מדדי גוף"))
    }

    // ---- resuming and editing ----------------------------------------------------------------------------

    @Test
    fun a_running_workout_is_offered_for_continuing_on_home() {
        val id = runBlocking {
            val c = app.container
            val plan = c.plans.getPlanDetail(c.plans.observePlans().first().first().id)!!
            c.workouts.startFromPlan(plan.workouts.first().workout.id)
        }
        show()
        waitForText("המשך אימון")
        tapText("continue workout", "המשך אימון", first = true)
        waitForText("סיים")
        assertTrue(runBlocking { app.container.workouts.observeActive().first() }?.id == id)
    }

    @Test
    fun a_finished_workout_can_be_opened_for_editing_from_its_summary() {
        show()
        waitForText("התחל אימון")
        tapText("journal tab", "יומן")
        waitForText("יומן אימונים")
        tapText("history view", "היסטוריה")
        waitForText("Upper A", substring = true)
        compose.tap("first workout row") { compose.onAllNodesWithText("Upper A", substring = true).onFirst() }
        waitForText("סיכום אימון")
        compose.tap("edit workout button") { compose.onNodeWithContentDescription("ערוך אימון") }
        // The edit screen is a lazy list; the button that closes edit mode is its last row.
        compose.scrollListTo("סיום עריכה")
    }

    @Test
    fun the_plan_shortcut_on_home_opens_the_plan() {
        show()
        waitForText("התחל אימון")
        compose.swipeUntilDisplayed("4 ימים בשבוע - פלג עליון / תחתון")
        tapText("plan chip", "4 ימים בשבוע - פלג עליון / תחתון", first = true)
        waitForText("סידור השבוע")
    }

    // ---- body -------------------------------------------------------------------------------------------

    @Test
    fun the_body_tab_lists_the_measurements_and_opens_the_photos() {
        show()
        waitForText("התחל אימון")
        tapText("body tab", "גוף")
        waitForText("מגמות")
        assertShown("82", substring = true)
        scrollBodyListTo("כל המדידות")
        scrollBodyListTo("תמונות התקדמות")
        tapTextContaining("photos button", "תמונות התקדמות")
        waitForText("תמונות התקדמות")
        compose.waitUntilOrDump("both photos are listed", 10_000) {
            compose.onAllNodesWithContentDescription("תמונה חזית", substring = true).fetchSemanticsNodes().size >= 2
        }
        compose.onNodeWithContentDescription("השוואת תמונות").assertExists()
    }

    /** The body screen is a lazy list: items below the fold do not exist until the list is scrolled to them. */
    private fun scrollBodyListTo(text: String) = compose.scrollListTo(text)

    private fun tapTextContaining(what: String, text: String) =
        compose.tap(what) { compose.onAllNodesWithText(text, substring = true).onFirst() }
}
