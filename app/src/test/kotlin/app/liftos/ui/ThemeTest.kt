package app.liftos.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.ThemeMode
import app.liftos.ui.theme.LiftOsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The look the owner asked for, as numbers: dark charcoal by default, lime accent, rounded cards, right-to-left. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private class Look(val colors: ColorScheme, val shapes: Shapes, val direction: LayoutDirection)

    private fun look(mode: ThemeMode): Look {
        var seen: Look? = null
        compose.setContent {
            LiftOsTheme(mode) { seen = Look(MaterialTheme.colorScheme, MaterialTheme.shapes, LocalLayoutDirection.current) }
        }
        compose.waitForIdle()
        return seen!!
    }

    @Test
    fun the_default_theme_is_dark_charcoal_with_a_lime_accent() {
        assertEquals(ThemeMode.DARK, AppSettings().themeMode)
        val l = look(AppSettings().themeMode)
        assertTrue("background is near black (luminance ${l.colors.background.luminance()})", l.colors.background.luminance() < 0.02)
        assertTrue("surface cards are dark too", l.colors.surfaceContainer.luminance() < 0.05)
        val p = l.colors.primary
        assertTrue("accent is green-dominant (lime)", p.green > p.red && p.green > p.blue && p.green > 0.8f)
        assertTrue("accent stands out from the background", p.luminance() > 0.5)
        assertTrue("text on the dark background is light", l.colors.onBackground.luminance() > 0.5)
    }

    @Test
    fun cards_and_surfaces_use_rounded_corners() {
        val l = look(ThemeMode.DARK)
        assertEquals(RoundedCornerShape(20.dp), l.shapes.large)
        assertEquals(RoundedCornerShape(16.dp), l.shapes.medium)
    }

    @Test
    fun the_whole_theme_is_right_to_left() {
        assertEquals(LayoutDirection.Rtl, look(ThemeMode.DARK).direction)
    }

    @Test
    fun the_light_theme_is_really_light() {
        val l = look(ThemeMode.LIGHT)
        assertTrue("light background (luminance ${l.colors.background.luminance()})", l.colors.background.luminance() > 0.8)
        assertTrue("dark text", l.colors.onBackground.luminance() < 0.2)
    }
}
