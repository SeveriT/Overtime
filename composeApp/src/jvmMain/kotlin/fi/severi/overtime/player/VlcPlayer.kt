package fi.severi.overtime.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
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

    /** GPU path: I420 frames converted to RGB by a shader. Used whenever the shader compiles. */
    internal val yuv = YuvRenderer()

    @Volatile internal var usingYuv = false
        private set

    private val bufferFormatCallback = object : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            if (YuvRenderer.effect != null) {
                // I420 is the decoder's own output, so VLC hands over its planes without RGB conversion.
                // Even sizes keep the half-size chroma planes exact.
                val width = sourceWidth and 1.inv()
                val height = sourceHeight and 1.inv()
                yuv.configure(width, height)
                usingYuv = true
                return BufferFormat(
                    "I420",
                    width,
                    height,
                    intArrayOf(width, width / 2, width / 2),
                    intArrayOf(height, height / 2, height / 2),
                )
            }
            usingYuv = false
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
        ) {
            if (usingYuv) {
                framesDrawn.incrementAndGet()
                yuv.write(nativeBuffers)
            } else {
                renderFrame(nativeBuffers[0], bufferFormat)
            }
        }
    }

    /** Frames handed to the screen so far (for tests and the fps readout). */
    fun framesShown(): Long = framesDrawn.get()

    init {
        check(NativeDiscovery().discover()) { "VLC not found. Install 64-bit VLC 3.x from videolan.org." }
        factory = MediaPlayerFactory("--no-video-title-show", "--quiet")
        player = factory.mediaPlayers().newEmbeddedMediaPlayer()
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                if (runningSince == 0L) runningSince = System.currentTimeMillis()
                _state.update { it.copy(playing = true, paused = false, buffering = null, error = null) }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                if (runningSince != 0L) {
                    playedBeforeMs += System.currentTimeMillis() - runningSince
                    runningSince = 0L
                }
                _state.update { it.copy(paused = true) }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(playing = false, paused = false, buffering = null) }
                // While time-shifting, a seek restarts VLC; keep the last frame up instead of flashing black.
                if (recorder == null) {
                    _frame.value = null
                    yuv.clear()
                }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _state.update { it.copy(buffering = if (newCache >= 100f) null else newCache) }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _state.update { it.copy(playing = false, buffering = null, error = "Stream could not be played") }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                // A buffered input only "ends" because we replaced it (seek) or stopped recording.
                if (recorder != null) return
                // A movie or episode reaching its end is normal; a live stream ending is not.
                if (_state.value.media != null) {
                    _state.update { it.copy(playing = false, buffering = null, ended = true) }
                } else {
                    _state.update { it.copy(playing = false, buffering = null, error = "Stream ended") }
                }
            }
        })
    }

    // ---- live rewind -------------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionLock = Any()
    private val timeshiftRoot = File(System.getProperty("java.io.tmpdir"), "overtime-timeshift")

    init {
        // Buffers left behind by a crash.
        timeshiftRoot.deleteRecursively()
        // Position and length of on-demand files for the seek bar.
        scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(TICK_MS)
                publishMediaProgress()
            }
        }
    }

    private fun publishMediaProgress() {
        val length = if (recorder == null) player.status().length() else 0L
        val progress = if (length > 0) MediaProgress(player.status().time().coerceIn(0, length), length) else null
        if (progress != _state.value.media) _state.update { it.copy(media = progress) }
    }

    override fun seekMedia(positionMs: Long) {
        val length = _state.value.media?.durationMs ?: return
        if (recorder != null) return
        player.controls().setTime(positionMs.coerceIn(0, length))
        _state.update { it.copy(ended = false) }
        publishMediaProgress()
    }

    @Volatile private var recorder: TimeshiftRecorder? = null
    private var media: TimeshiftMedia? = null

    /** Replaced inputs stay referenced briefly: libVLC may still call their callbacks while closing. */
    private val retired = ArrayDeque<TimeshiftMedia>()
    private var mediaStartWall = 0L
    private var cushionMs = 1_500L
    private var sessionJob: Job? = null

    // Our own playback clock for the current buffered input: VLC reports no time for a live callback
    // input, so count the wall time spent actually playing (paused time excluded).
    @Volatile private var playedBeforeMs = 0L
    @Volatile private var runningSince = 0L

    private fun playedMs(): Long {
        val since = runningSince
        return playedBeforeMs + if (since != 0L) System.currentTimeMillis() - since else 0L
    }

    private fun playDirect(url: String, networkCachingMs: Int, userAgent: String, startMs: Long = 0) {
        val options = listOfNotNull(
            ":network-caching=$networkCachingMs",
            ":http-user-agent=$userAgent",
            // Resume part-way into an on-demand file.
            if (startMs > 0) ":start-time=${startMs / 1000.0}" else null,
        )
        player.media().play(url, *options.toTypedArray())
    }

    private suspend fun runSession(rec: TimeshiftRecorder, url: String, networkCachingMs: Int, userAgent: String) {
        val status = rec.status.first { it != TimeshiftRecorder.Status.Starting }
        synchronized(sessionLock) {
            if (recorder !== rec) return
            if (status != TimeshiftRecorder.Status.Ready) {
                // Not recordable (or our download failed): VLC may still manage it on its own.
                rec.stop()
                recorder = null
                playDirect(url, networkCachingMs, userAgent)
                return
            }
            // HLS needs a bigger cushion than plain TS; the recorder knows which this is.
            cushionMs = max(cushionMs, rec.liveDelayMs)
            startAt(rec, rec.offsetAt(rec.liveWall - cushionMs))
        }
        while (currentCoroutineContext().isActive) {
            delay(TICK_MS)
            val failed = rec.status.value as? TimeshiftRecorder.Status.Failed
            if (failed != null) {
                _state.update { it.copy(error = "Stream stopped: ${failed.message}") }
                return
            }
            publish(rec)
        }
    }

    /** Points VLC at [offset] in the buffer. Caller holds [sessionLock]. */
    private fun startAt(rec: TimeshiftRecorder, offset: Long) {
        media?.let {
            it.cancel()
            retired.addLast(it)
            while (retired.size > 4) retired.removeFirst()
        }
        val next = TimeshiftMedia(rec, offset) {
            // Paused for longer than the buffer: resume from the oldest moment still kept.
            scope.launch { seekTo(rec.earliestWall + cushionMs) }
        }
        media = next
        mediaStartWall = rec.wallAt(offset)
        playedBeforeMs = 0L
        runningSince = 0L
        player.media().play(next)
        publish(rec)
    }

    private fun publish(rec: TimeshiftRecorder) {
        val start = rec.earliestWall
        val live = rec.liveWall
        val position = (mediaStartWall + playedMs()).coerceIn(start, max(start, live))
        // HLS's live edge jumps a segment at a time, so "live" must allow for one jump.
        val tolerance = cushionMs + LIVE_SLACK_MS + rec.edgeStepMs
        _state.update { it.copy(timeshift = TimeshiftState(start, live, position, liveToleranceMs = tolerance)) }
    }

    private fun endSession() {
        sessionJob?.cancel()
        sessionJob = null
        media?.cancel()
        media?.let(retired::addLast)
        media = null
        recorder?.stop()
        recorder = null
    }

    override fun seekTo(wallMs: Long) {
        synchronized(sessionLock) {
            val rec = recorder ?: return
            if (media == null) return
            val newest = rec.liveWall - cushionMs
            val target = wallMs.coerceIn(rec.earliestWall, max(rec.earliestWall, newest))
            startAt(rec, rec.offsetAt(target))
        }
    }

    override fun seekBy(deltaMs: Long) {
        _state.value.timeshift?.let {
            seekTo(it.position + deltaMs)
            return
        }
        _state.value.media?.let { seekMedia(it.positionMs + deltaMs) }
    }

    override fun goLive() = seekTo(Long.MAX_VALUE)

    // ---- stream info ------------------------------------------------------------------

    private val framesDrawn = AtomicLong()
    private var fpsSampleAt = 0L
    private var fpsSampleFrames = 0L

    override fun streamStats(): StreamStats? {
        val info = player.media().info() ?: return null
        val videoTrackId = player.video().track()
        val video = info.videoTracks().let { tracks -> tracks.firstOrNull { it.id() == videoTrackId } ?: tracks.firstOrNull() }
        val audioTrackId = player.audio().track()
        val audio = info.audioTracks().let { tracks -> tracks.firstOrNull { it.id() == audioTrackId } ?: tracks.firstOrNull() }
        val stats = info.statistics()

        // Measured fps over the time since the previous call (the panel polls about once a second).
        val now = System.currentTimeMillis()
        val frames = framesDrawn.get()
        val measured = if (fpsSampleAt != 0L && now - fpsSampleAt in 250..5_000) {
            (frames - fpsSampleFrames) * 1000.0 / (now - fpsSampleAt)
        } else {
            null
        }
        fpsSampleAt = now
        fpsSampleFrames = frames

        val rec = recorder
        val dimension = player.video().videoDimension()
        return StreamStats(
            width = video?.width()?.takeIf { it > 0 } ?: dimension?.width,
            height = video?.height()?.takeIf { it > 0 } ?: dimension?.height,
            frameRate = video?.takeIf { it.frameRate() > 0 && it.frameRateBase() > 0 }
                ?.let { it.frameRate().toDouble() / it.frameRateBase() },
            measuredFps = measured,
            videoCodec = video?.let { describeVideoCodec(it.codecName(), it.codecDescription(), it.profile(), it.level()) },
            // libVLC reports bytes per millisecond (×8000 = kbit/s), as VLC's own statistics dialog does.
            bitrateKbps = stats?.demuxBitrate()?.takeIf { it > 0f }?.let { (it * 8000).toInt() },
            droppedFrames = stats?.picturesLost(),
            displayedFrames = stats?.picturesDisplayed(),
            audioCodec = audio?.let { describeAudioCodec(it.codecName(), it.codecDescription()) },
            audioChannels = audio?.channels()?.takeIf { it > 0 },
            audioSampleRate = audio?.rate()?.takeIf { it > 0 },
            source = if (rec != null) "${rec.formatLabel ?: "Stream"} · recorded for rewind" else "Direct",
            bufferMs = rec?.let { it.liveWall - it.earliestWall },
            bufferBytes = rec?.let { it.latestOffset - it.earliestOffset },
        )
    }

    private fun renderFrame(buffer: ByteBuffer, format: BufferFormat) {
        framesDrawn.incrementAndGet()
        val info = imageInfo ?: return
        val source = buffer.duplicate()
        source.clear()
        if (pixels.size != source.remaining()) pixels = ByteArray(source.remaining())
        source.get(pixels, 0, min(pixels.size, source.remaining()))
        _frame.value = Image.makeRaster(info, pixels, format.pitches[0]).toComposeImageBitmap()
    }

    override fun play(url: String, networkCachingMs: Int, userAgent: String, rewindBufferMs: Long, startMs: Long) {
        synchronized(sessionLock) {
            endSession()
            _state.update {
                it.copy(playing = false, paused = false, buffering = 0f, error = null, timeshift = null, media = null, ended = false)
            }
            if (rewindBufferMs <= 0) {
                _frame.value = null; yuv.clear()
                playDirect(url, networkCachingMs, userAgent, startMs)
                return
            }
            // Start this far behind live so short network hiccups never starve playback.
            cushionMs = networkCachingMs.toLong().coerceAtLeast(MIN_CUSHION_MS)
            val rec = TimeshiftRecorder(
                url = url,
                userAgent = userAgent,
                bufferMs = rewindBufferMs,
                readyAfterMs = cushionMs,
                dir = File(timeshiftRoot, UUID.randomUUID().toString()),
            )
            recorder = rec
            _frame.value = null; yuv.clear()
            rec.start()
            sessionJob = scope.launch { runSession(rec, url, networkCachingMs, userAgent) }
        }
    }

    override fun stop() {
        synchronized(sessionLock) { endSession() }
        player.controls().stop()
        _state.update { it.copy(timeshift = null) }
    }

    override fun togglePause() {
        if (_state.value.ended) {
            // Play again from the start after a movie finished.
            _state.update { it.copy(ended = false) }
            player.controls().play()
            return
        }
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

    // ---- audio & subtitle tracks ---------------------------------------------------------

    override fun tracks(): TrackInfo? {
        if (!player.status().isPlayable && !_state.value.playing && !_state.value.paused) return null
        return TrackInfo(
            audio = player.audio().trackDescriptions().map { MediaTrack(it.id(), tidyTrackName(it.id(), it.description())) },
            audioSelected = player.audio().track(),
            subtitles = player.subpictures().trackDescriptions().map { MediaTrack(it.id(), tidyTrackName(it.id(), it.description())) },
            subtitleSelected = player.subpictures().track(),
        )
    }

    override fun selectAudioTrack(id: Int) {
        player.audio().setTrack(id)
    }

    override fun selectSubtitleTrack(id: Int) {
        player.subpictures().setTrack(id)
    }

    override fun release() {
        synchronized(sessionLock) { endSession() }
        scope.cancel()
        player.release()
        factory.release()
        yuv.release()
        timeshiftRoot.deleteRecursively()
    }

    private companion object {
        const val TICK_MS = 500L
        const val MIN_CUSHION_MS = 1_500L
        const val LIVE_SLACK_MS = 4_000L
    }
}

@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    val vlc = player as? VlcPlayer ?: return
    val frame by vlc.frame.collectAsState()
    val yuvVersion by vlc.yuv.version.collectAsState()
    Canvas(modifier) {
        if (vlc.usingYuv) {
            @Suppress("UNUSED_EXPRESSION") yuvVersion // read here so each new frame redraws
            val renderer = vlc.yuv
            if (renderer.width == 0 || !renderer.hasFrame) return@Canvas
            val scale = min(size.width / renderer.width, size.height / renderer.height)
            val left = (size.width - renderer.width * scale) / 2
            val top = (size.height - renderer.height * scale) / 2
            drawIntoCanvas { renderer.draw(it.skiaCanvas, left, top, scale) }
            return@Canvas
        }
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
