package fi.severi.overtime.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    /** Plays [url]; with [rewindBufferMs] > 0 the stream is recorded so it can be paused and rewound. */
    fun play(url: String, networkCachingMs: Int, userAgent: String, rewindBufferMs: Long = 0)
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
}

/** Draws the player's current video frame, letterboxed inside the given bounds. */
@Composable
expect fun VideoSurface(player: VideoPlayer, modifier: Modifier = Modifier)
