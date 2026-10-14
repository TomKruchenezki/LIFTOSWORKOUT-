package app.liftos.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.liftos.domain.model.AppSettings
import app.liftos.ui.settings.DeleteAllDialog
import app.liftos.ui.theme.LiftOsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The typed confirmation of "delete all my data": the button stays disabled until the word is typed exactly. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class DeleteAllDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun the_confirm_button_needs_the_exact_word() {
        var confirmed = 0
        var dismissed = 0
        // A text field in a dialog blinks its cursor forever, which would keep Compose from ever being idle in a
        // test: the animation clock is frozen and advanced by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LiftOsTheme(AppSettings().themeMode) { DeleteAllDialog(onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("למחוק את כל הנתונים?").assertExists()
        compose.onNodeWithText("מחק הכול").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput("מחק")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("מחק הכול").assertIsEnabled()

        compose.onNodeWithText("מחק הכול").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun a_wrong_word_keeps_the_button_disabled() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LiftOsTheme(AppSettings().themeMode) { DeleteAllDialog(onConfirm = {}, onDismiss = {}) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(hasSetTextAction()).performTextInput("מחק את")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("מחק הכול").assertIsNotEnabled()
    }
}
