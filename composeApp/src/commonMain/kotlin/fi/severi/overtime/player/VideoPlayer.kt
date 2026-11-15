package fi.severi.overtime.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import fi.severi.overtime.model.SubtitleStyle
import kotlinx.coroutines.flow.StateFlow

/** Rewind / forward step for the toolbar buttons and Shift+arrow keys. */
const val REWIND_STEP_MS = 10_000L

/**
 * Live rewind window, all in epoch millis: the oldest buffered moment, the live edge and what is on
 * screen now. Present only while the stream is being recorded.
 */
data class TimeshiftState(
    val bufferStart: Long,
    val liveEdge: Long,
    val position: Long,
    /** How far behind the edge still counts as live (the playback cushion plus some slack). */
    val liveToleranceMs: Long,
) {
    val behindMs: Long get() = (liveEdge - position).coerceAtLeast(0)
    val lengthMs: Long get() = (liveEdge - bufferStart).coerceAtLeast(0)
    val isLive: Boolean get() = behindMs <= liveToleranceMs
}

/** Snapshot for the stream info panel. Unknown values are null. */
data class StreamStats(
    val width: Int?,
    val height: Int?,
    /** Frame rate declared by the stream. */
    val frameRate: Double?,
    /** Frames actually drawn per second, measured over the last interval. */
    val measuredFps: Double?,
    val videoCodec: String?,
    val bitrateKbps: Int?,
    val droppedFrames: Int?,
    val displayedFrames: Int?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val audioSampleRate: Int?,
    /** How the stream reaches the player, e.g. "HLS · recorded for rewind". */
    val source: String,
    val bufferMs: Long?,
    val bufferBytes: Long?,
)

/** An audio or subtitle track; id -1 means "off". */
data class MediaTrack(val id: Int, val name: String)

data class TrackInfo(
    val audio: List<MediaTrack>,
    val audioSelected: Int,
    val subtitles: List<MediaTrack>,
    val subtitleSelected: Int,
)

/** Position within an on-demand file (movie, episode). */
data class MediaProgress(val positionMs: Long, val durationMs: Long)

data class PlayerState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    /** 0..100 while buffering, null once playback is running. */
    val buffering: Float? = null,
    val muted: Boolean = false,
    val volume: Int = 100,
    val error: String? = null,
    val timeshift: TimeshiftState? = null,
    /** Set for on-demand files with a known length. */
    val media: MediaProgress? = null,
    /** An on-demand file played to the end. */
    val ended: Boolean = false,
)

interface VideoPlayer {
    val state: StateFlow<PlayerState>

    /**
     * Plays [url]; with [rewindBufferMs] > 0 the stream is recorded so it can be paused and rewound.
     * [startMs] starts an on-demand file part-way through (resume). Only [onDemand] media (movies, episodes)
     * report a seek position; live streams don't, even when VLC gives them a "length".
     */
    fun play(
        url: String,
        networkCachingMs: Int,
        userAgent: String,
        rewindBufferMs: Long = 0,
        startMs: Long = 0,
        onDemand: Boolean = false,
    )
    fun stop()
    fun togglePause()
    fun setVolume(volume: Int)
    fun toggleMute()
    fun release()

    /** Jumps to wall-clock time [wallMs] inside the rewind buffer. */
    fun seekTo(wallMs: Long) = Unit

    /** Moves [deltaMs] back (negative) or forward: within the rewind buffer, or within an on-demand file. */
    fun seekBy(deltaMs: Long) = Unit

    /** Jumps to [positionMs] in an on-demand file. */
    fun seekMedia(positionMs: Long) = Unit

    /** Returns to the live edge. */
    fun goLive() = Unit

    /** Current stream details for the info panel; null when nothing is playing. */
    fun streamStats(): StreamStats? = null

    /** Audio and subtitle tracks of what's playing; null when nothing is. */
    fun tracks(): TrackInfo? = null

    fun selectAudioTrack(id: Int) = Unit

    /** -1 turns subtitles off. */
    fun selectSubtitleTrack(id: Int) = Unit

    /** Restyles subtitles; whatever is playing carries on from the same spot. */
    fun setSubtitleStyle(style: SubtitleStyle) = Unit
}

/** How the picture is fitted to the player area. [ratio] forces a display shape (width / height). */
enum class AspectMode(val label: String, val ratio: Float?) {
    AUTO("Auto", null),
    WIDE("16:9", 16f / 9f),
    STANDARD("4:3", 4f / 3f),
    CINEMA("21:9", 64f / 27f),
    /** Fills the area keeping the shape, cropping the edges. */
    ZOOM("Zoom", null),
}

/**
 * Where a [frameWidth] x [frameHeight] frame goes inside [container]. [pixelAspect] is the stream's
 * sample aspect ratio (non-square pixels on some SD channels). ZOOM overflows the container, so clip.
 */
fun videoRect(container: Size, frameWidth: Int, frameHeight: Int, pixelAspect: Float, mode: AspectMode): Rect {
    if (frameWidth <= 0 || frameHeight <= 0 || container.width <= 0f || container.height <= 0f) return Rect(Offset.Zero, container)
    val source = frameWidth * pixelAspect / frameHeight
    return when (mode) {
        AspectMode.ZOOM -> fitted(container, source, cover = true)
        else -> fitted(container, mode.ratio ?: source, cover = false)
    }
}

private fun fitted(container: Size, aspect: Float, cover: Boolean): Rect {
    val wider = aspect > container.width / container.height
    // Fit: a wider picture spans the width. Cover: it spans the height and overflows sideways.
    val size = if (wider != cover) Size(container.width, container.width / aspect) else Size(container.height * aspect, container.height)
    return Rect(Offset((container.width - size.width) / 2, (container.height - size.height) / 2), size)
}

/** Draws the player's current video frame inside the given bounds, shaped by [aspect]. */
@Composable
expect fun VideoSurface(player: VideoPlayer, modifier: Modifier = Modifier, aspect: AspectMode = AspectMode.AUTO)
