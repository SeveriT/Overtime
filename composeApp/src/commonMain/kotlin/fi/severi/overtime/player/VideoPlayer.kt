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

data class PlayerState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    /** 0..100 while buffering, null once playback is running. */
    val buffering: Float? = null,
    val muted: Boolean = false,
    val volume: Int = 100,
    val error: String? = null,
    val timeshift: TimeshiftState? = null,
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

    /** Moves [deltaMs] back (negative) or forward within the rewind buffer. */
    fun seekBy(deltaMs: Long) = Unit

    /** Returns to the live edge. */
    fun goLive() = Unit
}

/** Draws the player's current video frame, letterboxed inside the given bounds. */
@Composable
expect fun VideoSurface(player: VideoPlayer, modifier: Modifier = Modifier)
