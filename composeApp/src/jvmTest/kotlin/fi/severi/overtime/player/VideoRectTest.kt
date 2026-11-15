package fi.severi.overtime.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoRectTest {
    private val screen = Size(1600f, 900f) // 16:9 player area

    @Test
    fun autoFitsHdExactly() {
        assertEquals(Rect(0f, 0f, 1600f, 900f), videoRect(screen, 1920, 1080, 1f, AspectMode.AUTO))
    }

    @Test
    fun autoLetterboxesFourByThree() {
        assertEquals(Rect(200f, 0f, 1400f, 900f), videoRect(screen, 640, 480, 1f, AspectMode.AUTO))
    }

    @Test
    fun autoAppliesNonSquarePixels() {
        // 720×576 PAL widescreen: 64:45 pixels make it 16:9.
        val r = videoRect(screen, 720, 576, 64f / 45f, AspectMode.AUTO)
        assertEquals(1600f, r.width, 0.5f)
        assertEquals(900f, r.height, 0.5f)
    }

    @Test
    fun forcedRatioIgnoresSource() {
        assertEquals(Rect(200f, 0f, 1400f, 900f), videoRect(screen, 1920, 1080, 1f, AspectMode.STANDARD))
    }

    @Test
    fun cinemaLetterboxesTopAndBottom() {
        val r = videoRect(screen, 1920, 1080, 1f, AspectMode.CINEMA)
        assertEquals(1600f, r.width, 0.5f)
        assertEquals(675f, r.height, 0.5f)
        assertEquals(112.5f, r.top, 0.5f)
    }

    @Test
    fun zoomFillsAndCrops() {
        // 4:3 zoomed into 16:9 spans the width and overflows top and bottom.
        val r = videoRect(screen, 640, 480, 1f, AspectMode.ZOOM)
        assertEquals(1600f, r.width, 0.5f)
        assertEquals(1200f, r.height, 0.5f)
        assertEquals(-150f, r.top, 0.5f)
    }
}
