package fi.severi.overtime.player

import fi.severi.overtime.model.SubtitleEdge
import fi.severi.overtime.model.SubtitleStyle

/**
 * libVLC start-up options for [SubtitleStyle]. VLC reads these when it creates its text renderer, so a
 * change needs a fresh VLC instance (see [VlcPlayer.setSubtitleStyle]).
 */
internal fun SubtitleStyle.vlcArgs(): List<String> {
    val rgb = color.removePrefix("#").toIntOrNull(16) ?: 0xFFFFFF
    return listOf(
        "--freetype-font=${font.family}",
        "--sub-text-scale=${size.percent}",
        "--freetype-color=$rgb",
        if (bold) "--freetype-bold" else "--no-freetype-bold",
        // VLC's outline choices: 0 none, 2 thin, 4 normal, 6 thick.
        "--freetype-outline-thickness=${if (edge == SubtitleEdge.OUTLINE) 4 else 0}",
        "--freetype-outline-color=0",
        "--freetype-shadow-color=0",
        "--freetype-shadow-opacity=${when (edge) { SubtitleEdge.SHADOW -> 230; SubtitleEdge.OUTLINE -> 128; SubtitleEdge.BOX -> 0 }}",
        "--freetype-background-color=0",
        "--freetype-background-opacity=${if (edge == SubtitleEdge.BOX) 176 else 0}",
    )
}
