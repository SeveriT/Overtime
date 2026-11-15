package fi.severi.overtime.player

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Checks the I420 → RGB shader and plane layout by drawing synthetic frames off-screen. */
class YuvRendererTest {
    // Tall enough (≥ 720) to select BT.709, the matrix HD/4K channels use.
    private val width = 4
    private val height = 720

    private fun plane(size: Int, value: Int) = ByteBuffer.allocateDirect(size).apply { repeat(size) { put(value.toByte()) } }

    private fun frame(y: Int, u: Int, v: Int): Array<ByteBuffer> {
        val chroma = (width / 2) * (height / 2)
        return arrayOf(plane(width * height, y), plane(chroma, u), plane(chroma, v))
    }

    /** Renders one frame at 1:1 and returns the centre pixel as (r, g, b). */
    private fun render(y: Int, u: Int, v: Int): Triple<Int, Int, Int> {
        val renderer = YuvRenderer()
        renderer.configure(width, height)
        renderer.write(frame(y, u, v))
        val surface = Surface.makeRasterN32Premul(width, height)
        renderer.draw(surface.canvas, 0f, 0f, 1f)
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(width, height)
        surface.makeImageSnapshot().readPixels(bitmap, 0, 0)
        val argb = bitmap.getColor(width / 2, height / 2)
        renderer.release()
        return Triple(Color.getR(argb), Color.getG(argb), Color.getB(argb))
    }

    private fun assertColor(expected: Triple<Int, Int, Int>, actual: Triple<Int, Int, Int>, label: String) {
        val close = abs(expected.first - actual.first) <= 6 &&
            abs(expected.second - actual.second) <= 6 &&
            abs(expected.third - actual.third) <= 6
        assertTrue(close, "$label: expected $expected, got $actual")
    }

    @Test
    fun shaderCompiles() {
        assertNotNull(YuvRenderer.effect, "YUV shader failed to compile; the player would fall back to RGB")
    }

    @Test
    fun convertsLimitedRangeBt709() {
        assertColor(Triple(255, 255, 255), render(235, 128, 128), "white")
        assertColor(Triple(0, 0, 0), render(16, 128, 128), "black")
        // Pure red in BT.709 limited range: Y 63, Cb 102, Cr 240.
        assertColor(Triple(255, 0, 0), render(63, 102, 240), "red")
        // Pure blue: Y 32, Cb 240, Cr 118.
        assertColor(Triple(0, 0, 255), render(32, 240, 118), "blue")
    }
}
