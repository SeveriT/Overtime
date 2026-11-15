package fi.severi.overtime.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorHsvTest {
    @Test
    fun roundTripsThroughHsv() {
        listOf("#FF0000", "#00FF00", "#0000FF", "#FFE84D", "#191C20", "#808080", "#000000", "#FFFFFF", "#7FE7FF").forEach { hex ->
            val hsv = parseHexColor(hex)!!.toHsv()
            assertEquals(hex, Color.hsv(hsv[0], hsv[1], hsv[2]).toHex(), "round trip of $hex")
        }
    }

    @Test
    fun readsHue() {
        assertEquals(0f, parseHexColor("#FF0000")!!.toHsv()[0])
        assertEquals(120f, parseHexColor("#00FF00")!!.toHsv()[0])
        assertEquals(240f, parseHexColor("#0000FF")!!.toHsv()[0])
    }
}
