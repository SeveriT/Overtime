package fi.severi.overtime.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Full Material 3 dark scheme from the icon's blue (#1F4FA8), with every colour role filled in so
 * containers, tonal buttons and drawer items all get proper tones. Also used outside Compose
 * (the native title bar).
 */
val OvertimeColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF102F60),
    primaryContainer = Color(0xFF2B4678),
    onPrimaryContainer = Color(0xFFD8E2FF),
    inversePrimary = Color(0xFF445E91),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDEBCDF),
    onTertiary = Color(0xFF402843),
    tertiaryContainer = Color(0xFF583E5B),
    onTertiaryContainer = Color(0xFFFBD7FC),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFADC6FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

/** Favourite stars stay gold whatever the scheme. */
val FavoriteGold = Color(0xFFF6C453)

@Composable
fun OvertimeTheme(accent: Color = DefaultAccent, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = schemeWithAccent(accent),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

// ---- Accent colour ----------------------------------------------------------------

val DefaultAccent = OvertimeColors.primary

/** Pastel (tone ~80) accents that read well on the dark scheme. First entry is the default. */
val AccentPresets = listOf(
    ColorPreset("Blue (default)", DefaultAccent),
    ColorPreset("Purple", Color(0xFFD0BCFF)),
    ColorPreset("Pink", Color(0xFFFFB1C8)),
    ColorPreset("Red", Color(0xFFFFB4A8)),
    ColorPreset("Orange", Color(0xFFFFB77C)),
    ColorPreset("Amber", Color(0xFFF0C24F)),
    ColorPreset("Green", Color(0xFF9DD67E)),
    ColorPreset("Teal", Color(0xFF82D3CB)),
    ColorPreset("Cyan", Color(0xFF8AD0F2)),
    ColorPreset("Grey", Color(0xFFC6C6CC)),
)

fun accentColorOf(setting: String?): Color = parseHexColor(setting) ?: DefaultAccent

/**
 * Derives the primary roles from one accent colour. Dark accents are lifted so they stay visible on the
 * dark scheme; the selected-item tone (secondaryContainer) is tinted with the accent too.
 */
fun schemeWithAccent(accent: Color): ColorScheme {
    if (accent == DefaultAccent) return OvertimeColors
    val primary = if (accent.luminance() < 0.3f) lerp(accent, Color.White, 0.5f) else accent
    return OvertimeColors.copy(
        primary = primary,
        onPrimary = lerp(primary, Color.Black, 0.78f),
        primaryContainer = lerp(primary, Color.Black, 0.62f),
        onPrimaryContainer = lerp(primary, Color.White, 0.65f),
        inversePrimary = lerp(primary, Color.Black, 0.45f),
        surfaceTint = primary,
        secondaryContainer = lerp(OvertimeColors.surfaceContainerHighest, primary, 0.2f),
        onSecondaryContainer = lerp(primary, Color.White, 0.7f),
    )
}

// ---- Sidebar & title bar colour -------------------------------------------------

data class ColorPreset(val name: String, val color: Color)

/** Default sidebar colour: the drawer container tone. */
val DefaultSidebarColor = OvertimeColors.surfaceContainerLow

/** First entry is the default. */
val SidebarPresets = listOf(
    ColorPreset("Slate (default)", DefaultSidebarColor),
    ColorPreset("Black", Color(0xFF0A0A0B)),
    ColorPreset("Graphite", Color(0xFF232427)),
    ColorPreset("Navy", Color(0xFF0F1A30)),
    ColorPreset("Blue", Color(0xFF16305C)),
    ColorPreset("Teal", Color(0xFF0F2E30)),
    ColorPreset("Forest", Color(0xFF132A1B)),
    ColorPreset("Wine", Color(0xFF2E1219)),
    ColorPreset("Plum", Color(0xFF251536)),
    ColorPreset("Bronze", Color(0xFF2E2012)),
)

private val DarkText = Color(0xFF1A1C20)

/** Parses #RRGGBB (the # is optional). Returns null for anything else. */
fun parseHexColor(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6 || digits.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return Color(0xFF000000 or digits.toLong(16))
}

fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** The sidebar colour for a stored setting, falling back to the default. */
fun sidebarColorOf(setting: String?): Color = parseHexColor(setting) ?: DefaultSidebarColor

fun isLightColor(color: Color): Boolean = color.luminance() > 0.4f

/** Readable text colour on top of [background]. */
fun contentColorFor(background: Color): Color = if (isLightColor(background)) DarkText else OvertimeColors.onSurface

/**
 * Re-themes the sidebar for a custom colour. The sidebar sits on surfaceContainerLow, so that becomes
 * [color]; the higher container tones, the selected-item tone and the text roles are mixed from it so
 * drawer items, the search pill and tonal buttons keep their hierarchy on any colour.
 */
@Composable
fun SidebarTheme(color: Color, content: @Composable () -> Unit) {
    if (color == DefaultSidebarColor) {
        content()
        return
    }
    val base = MaterialTheme.colorScheme
    val light = isLightColor(color)
    val on = contentColorFor(color)
    val accent = if (light) lerp(base.primary, Color.Black, 0.6f) else base.primary
    val scheme = base.copy(
        surfaceContainerLow = color,
        surfaceContainer = lerp(color, on, 0.05f),
        surfaceContainerHigh = lerp(color, on, 0.09f),
        surfaceContainerHighest = lerp(color, on, 0.13f),
        surface = color,
        onSurface = on,
        onSurfaceVariant = lerp(color, on, 0.72f),
        surfaceVariant = lerp(color, on, 0.16f),
        outline = lerp(color, on, 0.45f),
        outlineVariant = lerp(color, on, 0.18f),
        secondaryContainer = lerp(lerp(color, on, 0.12f), accent, 0.2f),
        onSecondaryContainer = on,
        primary = accent,
        onPrimary = if (light) Color.White else base.onPrimary,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides on, content = content)
    }
}
