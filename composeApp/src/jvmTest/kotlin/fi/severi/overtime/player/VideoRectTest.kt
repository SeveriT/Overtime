package fi.severi.overtime.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoRectTest {
    private val screen = Size(1600f, 900f) // 16:9 player area

    @Test
    fun fitsHdExactly() {
        assertEquals(Rect(0f, 0f, 1600f, 900f), videoRect(screen, 1920, 1080, 1f))
    }

    @Test
    fun letterboxesFourByThree() {
        assertEquals(Rect(200f, 0f, 1400f, 900f), videoRect(screen, 640, 480, 1f))
    }

    @Test
    fun appliesNonSquarePixels() {
        // 720x576 PAL widescreen: 64:45 pixels make it 16:9.
        val r = videoRect(screen, 720, 576, 64f / 45f)
        assertEquals(1600f, r.width, 0.5f)
        assertEquals(900f, r.height, 0.5f)
    }

    @Test
    fun letterboxesWideFilmTopAndBottom() {
        val r = videoRect(screen, 1920, 800, 1f)
        assertEquals(1600f, r.width, 0.5f)
        assertEquals(666.7f, r.height, 0.5f)
    }
}
