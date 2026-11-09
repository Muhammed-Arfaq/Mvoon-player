package app.mvoon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mvoon.core.settings.AppearancePreferences
import app.mvoon.core.settings.ThemeMode

/**
 * The Mvoon palette — moonlit blues: deep navy surfaces, an azure accent for whatever is
 * selected or playing, sky and violet for highlights, and coral kept for favourites, errors and
 * the volume boost. The logo, launcher icon, splash and generated artwork use the same colours.
 */
object Brand {
    /** Accent on dark surfaces (player, navy cards). */
    val Azure = Color(0xFF5B8CFF)
    /** Accent on light surfaces (stronger, so white text on it stays legible). */
    val Cobalt = Color(0xFF2F5BEA)
    val Sky = Color(0xFF8FB8FF)
    val Ice = Color(0xFFD9E6FF)
    val Violet = Color(0xFF8B7CFF)
    val Coral = Color(0xFFFF7A6B)
    val Mint = Color(0xFF5ED3B3)
    val Navy = Color(0xFF152452)
    val Midnight = Color(0xFF0A1020)
    val Slate = Color(0xFF1A2338)
    val Mist = Color(0xFFEEF2F9)

    /** Text and icons drawn on azure. */
    val OnAzure = Color.White

    val colors = listOf(Sky, Azure, Violet)

    fun horizontal(alpha: Float = 1f): Brush = Brush.horizontalGradient(colors.map { it.copy(alpha = alpha) })
    fun diagonal(alpha: Float = 1f): Brush = Brush.linearGradient(colors.map { it.copy(alpha = alpha) })
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6E95FF),
    onPrimary = Color(0xFF0A1433),
    primaryContainer = Color(0xFF1E3270),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFE6ECF8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF23304D),
    onSecondaryContainer = Color(0xFFE6ECF8),
    tertiary = Color(0xFFFF8A7A),
    onTertiary = Color(0xFF3E0B00),
    tertiaryContainer = Color(0xFF5C2A22),
    onTertiaryContainer = Color(0xFFFFDBD4),
    background = Color(0xFF0A0F1C),
    onBackground = Color(0xFFE8EDF7),
    surface = Color(0xFF0E1424),
    onSurface = Color(0xFFE8EDF7),
    surfaceVariant = Color(0xFF1D263B),
    onSurfaceVariant = Color(0xFF94A0B8),
    surfaceContainerLowest = Color(0xFF070B15),
    surfaceContainerLow = Color(0xFF111829),
    surfaceContainer = Color(0xFF161E31),
    surfaceContainerHigh = Color(0xFF1D263B),
    surfaceContainerHighest = Color(0xFF253047),
    outline = Color(0xFF364159),
    outlineVariant = Color(0xFF232D43),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE8EDF7),
    inverseOnSurface = Color(0xFF0F172A),
    inversePrimary = Brand.Cobalt,
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Brand.Cobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF0B1E5C),
    secondary = Color(0xFF172554),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E7F5),
    onSecondaryContainer = Color(0xFF172554),
    tertiary = Color(0xFFD9483A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF3B0A00),
    background = Brand.Mist,
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF5F7FC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE0E6F2),
    onSurfaceVariant = Color(0xFF5B6478),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFE),
    surfaceContainer = Color(0xFFE8EDF7),
    surfaceContainerHigh = Color(0xFFE0E6F2),
    surfaceContainerHighest = Color(0xFFD7DEEC),
    outline = Color(0xFFCBD3E2),
    outlineVariant = Color(0xFFE0E6F0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF172554),
    inverseOnSurface = Brand.Mist,
    inversePrimary = Brand.Sky,
    scrim = Color(0xFF000000),
)

@Immutable
data class MvoonExtras(
    val isDark: Boolean,
    /** Soft glow behind hero elements (artwork, logo). */
    val glow: Color,
    /** Hairline separators and card strokes. */
    val hairline: Color,
    /** Raised cards that sit on the background (white on mist, slate on midnight). */
    val card: Color,
    /** The "inverted" feature card: deep navy in light mode, a lifted navy in dark mode. */
    val contrastCard: Color,
    val onContrastCard: Color,
    /** Accent used on [contrastCard] (titles, icons). */
    val contrastAccent: Color,
    /** Header block behind the home dashboard. */
    val hero: Color,
    val onHero: Color,
    /** Bottom navigation bar. */
    val navBar: Color,
)

val LocalMvoonExtras = staticCompositionLocalOf {
    MvoonExtras(
        isDark = true, glow = Brand.Azure.copy(alpha = 0.3f), hairline = Color(0x1AFFFFFF), card = Color(0xFF161E31),
        contrastCard = Color(0xFF1F2E5E), onContrastCard = Color.White, contrastAccent = Brand.Sky,
        hero = Color(0xFF1A2A5C), onHero = Color.White, navBar = Color(0xFF111829),
    )
}

val MvoonShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun MvoonTheme(
    appearance: AppearancePreferences = AppearancePreferences(),
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = forceDark || when (appearance.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val dynamic = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme: ColorScheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && appearance.pureBlack) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    }
    val extras = MvoonExtras(
        isDark = dark,
        glow = Brand.Azure.copy(alpha = if (dark) 0.28f else 0.3f),
        hairline = if (dark) Color(0x1AFFFFFF) else Color(0x140F172A),
        card = if (dark) scheme.surfaceContainer else Color.White,
        contrastCard = if (dynamic) scheme.inverseSurface else if (dark) Color(0xFF1F2E5E) else Color(0xFF172554),
        onContrastCard = if (dynamic) scheme.inverseOnSurface else Color.White,
        contrastAccent = if (dynamic) scheme.inversePrimary else Brand.Sky,
        hero = if (dynamic) scheme.primaryContainer else if (dark) Color(0xFF1A2A5C) else Brand.Navy,
        onHero = if (dynamic) scheme.onPrimaryContainer else Color.White,
        navBar = if (dark) Color(0xFF111829) else Color.White,
    )
    CompositionLocalProvider(LocalMvoonExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = MvoonTypography, shapes = MvoonShapes, content = content)
    }
}
