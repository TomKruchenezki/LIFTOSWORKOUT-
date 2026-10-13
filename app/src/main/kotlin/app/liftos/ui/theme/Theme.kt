package app.liftos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import app.liftos.domain.model.ThemeMode

/** Brand colours: deep charcoal surfaces with an elegant lime accent. */
object Brand {
    val Lime = Color(0xFFB6F23C)
    val LimeDark = Color(0xFF4C7A00)
    val Charcoal = Color(0xFF0E100F)
    val Amber = Color(0xFFFFC857)
}

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Brand.Lime,
    onPrimary = Color(0xFF121E00),
    primaryContainer = Color(0xFF2B3D0C),
    onPrimaryContainer = Color(0xFFD6FF7A),
    secondary = Color(0xFF9CB37E),
    onSecondary = Color(0xFF182309),
    secondaryContainer = Color(0xFF243019),
    onSecondaryContainer = Color(0xFFD2E8B4),
    tertiary = Brand.Amber,
    onTertiary = Color(0xFF2A1D00),
    tertiaryContainer = Color(0xFF3F2E00),
    onTertiaryContainer = Color(0xFFFFE2A0),
    background = Brand.Charcoal,
    onBackground = Color(0xFFE9EDE8),
    surface = Brand.Charcoal,
    onSurface = Color(0xFFE9EDE8),
    surfaceVariant = Color(0xFF1E2321),
    onSurfaceVariant = Color(0xFF9BA59E),
    surfaceContainerLowest = Color(0xFF0A0C0B),
    surfaceContainerLow = Color(0xFF141716),
    surfaceContainer = Color(0xFF181C1A),
    surfaceContainerHigh = Color(0xFF1E2321),
    surfaceContainerHighest = Color(0xFF252B28),
    outline = Color(0xFF3A433E),
    outlineVariant = Color(0xFF272E2A),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF330000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD6D6),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Brand.LimeDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDF07E),
    onPrimaryContainer = Color(0xFF152200),
    secondary = Color(0xFF55633F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E8BC),
    onSecondaryContainer = Color(0xFF131F03),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDF9B),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF5F7F2),
    onBackground = Color(0xFF151A14),
    surface = Color(0xFFF5F7F2),
    onSurface = Color(0xFF151A14),
    surfaceVariant = Color(0xFFE3E9DC),
    onSurfaceVariant = Color(0xFF4A5547),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3EA),
    surfaceContainer = Color(0xFFEAEFE3),
    surfaceContainerHigh = Color(0xFFE4EADC),
    surfaceContainerHighest = Color(0xFFDDE4D4),
    outline = Color(0xFF798374),
    outlineVariant = Color(0xFFC9D1C3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall,
)

val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** Large tabular numerals for timers and set tables. */
val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFeatureSettings = "tnum")

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/** Hebrew UI: the whole app is laid out right-to-left. Numbers and Latin text keep their own direction. */
@Composable
fun LiftOsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
