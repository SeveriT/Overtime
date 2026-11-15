package fi.severi.overtime.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** The app's colour scheme, also used outside Compose (e.g. to tint the native title bar). */
val OvertimeColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1A33),
    primaryContainer = Color(0xFF1E3A6E),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFF6C453),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF15181E),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1F232B),
    onSurfaceVariant = Color(0xFFA3A8B3),
    surfaceContainer = Color(0xFF1A1E25),
    surfaceContainerHigh = Color(0xFF22272F),
    outline = Color(0xFF353B47),
    outlineVariant = Color(0xFF262B34),
    error = Color(0xFFFF7A7A),
)

@Composable
fun OvertimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OvertimeColors, content = content)
}

// ---- Sidebar & title bar colour -------------------------------------------------

data class ColorPreset(val name: String, val color: Color)

/** First entry is the default (the theme's own surface colour). */
val SidebarPresets = listOf(
    ColorPreset("Slate (default)", OvertimeColors.surface),
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

private val DarkText = Color(0xFF15181E)

/** Parses #RRGGBB (the # is optional). Returns null for anything else. */
fun parseHexColor(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6 || digits.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return Color(0xFF000000 or digits.toLong(16))
}

fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** The sidebar colour for a stored setting, falling back to the default. */
fun sidebarColorOf(setting: String?): Color = parseHexColor(setting) ?: OvertimeColors.surface

fun isLightColor(color: Color): Boolean = color.luminance() > 0.4f

/** Readable text colour on top of [background]. */
fun contentColorFor(background: Color): Color = if (isLightColor(background)) DarkText else OvertimeColors.onSurface

/**
 * Re-themes the sidebar for a custom colour: surface becomes [color], text flips to dark on light colours,
 * and dividers, borders and tiles are mixed from the two so they stay subtle on any colour.
 */
@Composable
fun SidebarTheme(color: Color, content: @Composable () -> Unit) {
    if (color == OvertimeColors.surface) {
        content()
        return
    }
    val light = isLightColor(color)
    val on = contentColorFor(color)
    val scheme = OvertimeColors.copy(
        surface = color,
        onSurface = on,
        onSurfaceVariant = lerp(color, on, 0.68f),
        surfaceVariant = lerp(color, on, 0.08f),
        outline = lerp(color, on, 0.28f),
        outlineVariant = lerp(color, on, 0.12f),
        primary = if (light) Color(0xFF1F4FA8) else OvertimeColors.primary,
        primaryContainer = if (light) lerp(color, Color(0xFF1F4FA8), 0.22f) else OvertimeColors.primaryContainer,
        secondary = if (light) Color(0xFFB8860B) else OvertimeColors.secondary,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides on, content = content)
    }
}
