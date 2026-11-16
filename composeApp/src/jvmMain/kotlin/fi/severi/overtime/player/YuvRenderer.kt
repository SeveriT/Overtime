package fi.severi.overtime.player

import com.sun.jna.Pointer
import fi.severi.overtime.model.PictureAdjust
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Takes VLC's decoded frames as planar I420 — what the decoder produces, so VLC does no colour
 * conversion, and a 4K frame is 12 MB instead of 33 MB of RGB — and draws the newest one with a GPU
 * shader that converts YUV to RGB while scaling.
 *
 * Each plane is a single-channel (GRAY_8) bitmap; this Skia build can't allocate two-channel R8G8, which
 * rules out NV12's interleaved chroma. Frames go into three reusable slots, so nothing is allocated per
 * frame and the slot being drawn is never the one VLC writes next. Planes are copied once,
 * native-to-native, straight into Skia's pixel memory.
 */
internal class YuvRenderer {
    private class Plane(width: Int, height: Int) {
        val bitmap = Bitmap().apply {
            check(allocPixels(ImageInfo(width, height, ColorType.GRAY_8, ColorAlphaType.OPAQUE))) {
                "Could not allocate a ${width}x$height video plane"
            }
        }
        val address = bitmap.peekPixels()!!.addr
        val size = width.toLong() * height
    }

    private class Slot(width: Int, height: Int) {
        val planes = listOf(Plane(width, height), Plane(width / 2, height / 2), Plane(width / 2, height / 2))
        fun close() = planes.forEach { it.bitmap.close() }
    }

    private val lock = Any()
    private var slots: List<Slot> = emptyList()
    private var front = -1
    private var inUse = -1

    var width = 0
        private set
    var height = 0
        private set

    private val _version = MutableStateFlow(0L)

    /** Bumps on every new (or cleared) frame so the surface redraws. */
    val version: StateFlow<Long> = _version.asStateFlow()

    val hasFrame: Boolean get() = synchronized(lock) { front >= 0 }

    /** Sizes the slots for a new video format. Dimensions must be even. Called from VLC's thread. */
    fun configure(width: Int, height: Int) = synchronized(lock) {
        slots.forEach { it.close() }
        slots = List(SLOTS) { Slot(width, height) }
        this.width = width
        this.height = height
        front = -1
        inUse = -1
    }

    /** Copies a decoded frame: planes Y, U, V. Called from VLC's thread. */
    fun write(planes: Array<ByteBuffer>) {
        val index = synchronized(lock) {
            if (slots.isEmpty()) return
            slots.indices.first { it != front && it != inUse }
        }
        val slot = slots[index]
        slot.planes.forEachIndexed { i, plane ->
            copyInto(planes[i], plane.address, plane.size)
            plane.bitmap.notifyPixelsChanged()
        }
        synchronized(lock) { front = index }
        _version.value = _version.value + 1
    }

    fun clear() {
        synchronized(lock) { front = -1 }
        _version.value = _version.value + 1
    }

    /** Draws the newest frame at [left],[top] scaled by [scale]. */
    fun draw(canvas: Canvas, left: Float, top: Float, scaleX: Float, scaleY: Float = scaleX, adjust: PictureAdjust = PictureAdjust()) {
        val program = effect ?: return
        val index = synchronized(lock) {
            if (front < 0) return
            inUse = front
            front
        }
        // Subtitles are drawn into the frame by VLC, so scaling quality decides how sharp they are in a
        // window. Luma carries the detail: a cubic filter keeps strokes crisp when shrinking moderately
        // (and when enlarging), mipmaps stop thin lines breaking up below half size. Chroma is soft anyway.
        val shrink = min(scaleX, scaleY)
        val lumaSampling: SamplingMode =
            if (shrink < MIPMAP_BELOW) FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR) else SamplingMode.CATMULL_ROM
        // makeShader snapshots the pixels, so the slot is free again right after this.
        val shaders = slots[index].planes.mapIndexed { plane, it ->
            it.bitmap.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, if (plane == 0) lumaSampling else SamplingMode.LINEAR)
        }
        synchronized(lock) { inUse = -1 }

        val builder = RuntimeShaderBuilder(program)
        val paint = Paint()
        try {
            builder.child("yTex", shaders[0])
            builder.child("uTex", shaders[1])
            builder.child("vTex", shaders[2])
            // HD and up use BT.709, SD uses BT.601; both limited (TV) range.
            val c = if (height >= 720) BT709 else BT601
            builder.uniform("rCoef", c[0], c[1], c[2])
            builder.uniform("gCoef", c[3], c[4], c[5])
            builder.uniform("bCoef", c[6], c[7], c[8])
            builder.uniform("brightness", adjust.brightness)
            builder.uniform("contrast", adjust.contrast)
            builder.uniform("saturation", adjust.saturation)
            val shader = builder.makeShader()
            paint.shader = shader
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scaleX, scaleY)
            canvas.drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), paint)
            canvas.restore()
            shader.close()
        } finally {
            paint.close()
            builder.close()
            shaders.forEach { it.close() }
        }
    }

    fun release() = synchronized(lock) {
        slots.forEach { it.close() }
        slots = emptyList()
        front = -1
    }

    companion object {
        private const val SLOTS = 3

        /** Below this scale the luma plane is sampled from mipmaps instead of with a cubic filter. */
        private const val MIPMAP_BELOW = 0.5f

        private const val SKSL = """
            uniform shader yTex;
            uniform shader uTex;
            uniform shader vTex;
            uniform float3 rCoef;
            uniform float3 gCoef;
            uniform float3 bCoef;
            uniform float brightness;
            uniform float contrast;
            uniform float saturation;

            half4 main(float2 p) {
                float2 c = p * 0.5;                          // chroma planes are half size
                float3 yuv = float3(
                    yTex.eval(p).r - 0.0627451,              // 16/255
                    uTex.eval(c).r - 0.5019608,              // 128/255
                    vTex.eval(c).r - 0.5019608);
                float3 rgb = float3(dot(rCoef, yuv), dot(gCoef, yuv), dot(bCoef, yuv));
                // Picture adjustments: contrast around mid-grey, brightness offset, saturation around luma.
                rgb = (rgb - 0.5) * contrast + 0.5 + brightness;
                float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
                rgb = mix(float3(luma), rgb, saturation);
                return half4(half3(saturate(rgb)), 1.0);
            }
        """

        // Limited-range YUV → RGB, rows of (Y, U, V) weights.
        private val BT709 = floatArrayOf(
            1.16438f, 0f, 1.79274f,
            1.16438f, -0.21325f, -0.53291f,
            1.16438f, 2.11240f, 0f,
        )
        private val BT601 = floatArrayOf(
            1.16438f, 0f, 1.59603f,
            1.16438f, -0.39176f, -0.81297f,
            1.16438f, 2.01723f, 0f,
        )

        /** Null if the GPU shader can't be compiled; the player then falls back to RGB frames. */
        val effect: RuntimeEffect? by lazy { runCatching { RuntimeEffect.makeForShader(SKSL) }.getOrNull() }

        private fun copyInto(source: ByteBuffer, address: Long, size: Long) {
            val count = min(size, source.capacity().toLong()).toInt()
            val target = Pointer(address).getByteBuffer(0, count.toLong())
            val from = source.duplicate()
            from.clear()
            from.limit(count)
            target.put(from)
        }
    }
}
