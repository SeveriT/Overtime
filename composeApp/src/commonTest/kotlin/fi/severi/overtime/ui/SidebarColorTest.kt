package fi.severi.overtime.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SidebarColorTest {
    @Test
    fun parsesAndFormatsHex() {
        assertEquals(Color(0xFF16305C), parseHexColor("#16305C"))
        assertEquals(Color(0xFF16305C), parseHexColor("16305c"))
        assertEquals("#16305C", Color(0xFF16305C).toHex())
        assertNull(parseHexColor("#1630"))
        assertNull(parseHexColor("#GG305C"))
        assertNull(parseHexColor(null))
    }

    @Test
    fun fallsBackToDefaults() {
        assertEquals(DefaultSidebarColor, sidebarColorOf(null))
        assertEquals(DefaultSidebarColor, sidebarColorOf("nonsense"))
        assertEquals(DefaultAccent, accentColorOf(null))
        assertEquals(SidebarPresets.first().color, DefaultSidebarColor)
        assertEquals(AccentPresets.first().color, DefaultAccent)
    }

    @Test
    fun picksReadableText() {
        assertFalse(isLightColor(DefaultSidebarColor))
        assertTrue(isLightColor(Color(0xFFE8ECF2)))
        assertEquals(OvertimeColors.onSurface, contentColorFor(Color(0xFF0A0A0B)))
    }

    @Test
    fun accentDrivesPrimaryRoles() {
        assertSame(OvertimeColors, schemeWithAccent(DefaultAccent))
        val purple = Color(0xFFD0BCFF)
        val scheme = schemeWithAccent(purple)
        assertEquals(purple, scheme.primary)
        assertTrue(scheme.primaryContainer.luminance() < purple.luminance())
        // A dark accent is lifted so it stays visible on the dark scheme.
        val lifted = schemeWithAccent(Color(0xFF1F4FA8)).primary
        assertTrue(lifted.luminance() > Color(0xFF1F4FA8).luminance())
    }
}
