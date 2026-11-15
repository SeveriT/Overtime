package fi.severi.overtime.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    /** 0..100 while buffering, null once playback is running. */
    val buffering: Float? = null,
    val muted: Boolean = false,
    val volume: Int = 100,
    val error: String? = null,
)

interface VideoPlayer {
    val state: StateFlow<PlayerState>
    fun play(url: String, networkCachingMs: Int, userAgent: String)
    fun stop()
    fun togglePause()
    fun setVolume(volume: Int)
    fun toggleMute()
    fun release()
}

/** Draws the player's current video frame, letterboxed inside the given bounds. */
@Composable
expect fun VideoSurface(player: VideoPlayer, modifier: Modifier = Modifier)
