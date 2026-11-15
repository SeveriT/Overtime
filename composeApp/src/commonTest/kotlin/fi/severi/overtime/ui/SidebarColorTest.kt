package fi.severi.overtime.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun fallsBackToDefault() {
        assertEquals(OvertimeColors.surface, sidebarColorOf(null))
        assertEquals(OvertimeColors.surface, sidebarColorOf("nonsense"))
    }

    @Test
    fun picksReadableText() {
        assertFalse(isLightColor(OvertimeColors.surface))
        assertTrue(isLightColor(Color(0xFFE8ECF2)))
        assertEquals(OvertimeColors.onSurface, contentColorFor(Color(0xFF0A0A0B)))
    }
}
