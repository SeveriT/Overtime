package fi.severi.overtime.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * libVLC-backed player. Video frames are rendered through a callback surface into a Compose [ImageBitmap],
 * so the picture is an ordinary composable and overlays draw on top of it without AWT interop.
 */
class VlcPlayer : VideoPlayer {
    private val factory: MediaPlayerFactory
    private val player: EmbeddedMediaPlayer

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()

    private var imageInfo: ImageInfo? = null
    private var pixels = ByteArray(0)

    private val bufferFormatCallback = object : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            imageInfo = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }
    }

    private val renderCallback = object : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) = Unit
        override fun unlock(mediaPlayer: MediaPlayer) = Unit
        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int,
        ) = renderFrame(nativeBuffers[0], bufferFormat)
    }

    init {
        check(NativeDiscovery().discover()) { "VLC not found. Install 64-bit VLC 3.x from videolan.org." }
        factory = MediaPlayerFactory("--no-video-title-show", "--quiet")
        player = factory.mediaPlayers().newEmbeddedMediaPlayer()
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(playing = true, paused = false, buffering = null, error = null) }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(paused = true) }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(playing = false, paused = false, buffering = null) }
                _frame.value = null
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _state.update { it.copy(buffering = if (newCache >= 100f) null else newCache) }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(playing = false, buffering = null, error = "Stream could not be played") }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(playing = false, buffering = null, error = "Stream ended") }
            }
        })
    }

    private fun renderFrame(buffer: ByteBuffer, format: BufferFormat) {
        val info = imageInfo ?: return
        val source = buffer.duplicate()
        source.clear()
        if (pixels.size != source.remaining()) pixels = ByteArray(source.remaining())
        source.get(pixels, 0, min(pixels.size, source.remaining()))
        _frame.value = Image.makeRaster(info, pixels, format.pitches[0]).toComposeImageBitmap()
    }

    override fun play(url: String, networkCachingMs: Int, userAgent: String) {
        _state.update { it.copy(playing = false, paused = false, buffering = 0f, error = null) }
        player.media().play(url, ":network-caching=$networkCachingMs", ":http-user-agent=$userAgent")
    }

    override fun stop() = player.controls().stop()

    override fun togglePause() {
        if (_state.value.playing || _state.value.paused) player.controls().pause()
    }

    override fun setVolume(volume: Int) {
        player.audio().setVolume(volume.coerceIn(0, 100))
        _state.update { it.copy(volume = volume) }
    }

    override fun toggleMute() {
        player.audio().mute()
        _state.update { it.copy(muted = !it.muted) }
    }

    override fun release() {
        player.release()
        factory.release()
    }
}

@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    val vlc = player as? VlcPlayer ?: return
    val frame by vlc.frame.collectAsState()
    Canvas(modifier) {
        val image = frame ?: return@Canvas
        val scale = min(size.width / image.width, size.height / image.height)
        val dstWidth = (image.width * scale).roundToInt()
        val dstHeight = (image.height * scale).roundToInt()
        drawImage(
            image = image,
            dstOffset = IntOffset(((size.width - dstWidth) / 2).roundToInt(), ((size.height - dstHeight) / 2).roundToInt()),
            dstSize = IntSize(dstWidth, dstHeight),
            filterQuality = FilterQuality.Medium,
        )
    }
}
