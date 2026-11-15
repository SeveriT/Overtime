package fi.severi.overtime.player

import fi.severi.overtime.model.SubtitleEdge
import fi.severi.overtime.model.SubtitleFont
import fi.severi.overtime.model.SubtitleSize
import fi.severi.overtime.model.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertTrue

class SubtitleArgsTest {
    @Test
    fun mapsStyleToVlcOptions() {
        val args = SubtitleStyle(SubtitleFont.VERDANA, SubtitleSize.LARGE, "#FFE84D", SubtitleEdge.BOX, bold = true).vlcArgs()
        assertTrue("--freetype-font=Verdana" in args)
        assertTrue("--sub-text-scale=125" in args)
        assertTrue("--freetype-color=${0xFFE84D}" in args)
        assertTrue("--freetype-bold" in args)
        assertTrue("--freetype-outline-thickness=0" in args)
        assertTrue("--freetype-background-opacity=176" in args)
    }

    /** libVLC refuses to start on an unknown option, so every style must create a player. Needs VLC (OVERTIME_SMOKE=1). */
    @Test
    fun vlcAcceptsEveryStyle() {
        if (System.getenv("OVERTIME_SMOKE") != "1") return
        val player = VlcPlayer(SubtitleStyle(bold = true))
        try {
            SubtitleEdge.entries.forEach { edge -> player.setSubtitleStyle(SubtitleStyle(SubtitleFont.SEGOE, SubtitleSize.SMALL, "#7FE7FF", edge)) }
        } finally {
            player.release()
        }
    }
}
