package fi.severi.overtime.player

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plays a public test stream for a few seconds through libVLC and checks a frame arrives.
 * Needs VLC installed and network access; set OVERTIME_SMOKE=1 to enable.
 */
class VlcPlayerSmokeTest {
    @Test
    fun rendersAFrameFromAnHlsStream() {
        if (System.getenv("OVERTIME_SMOKE") != "1") return
        val player = VlcPlayer()
        try {
            player.play("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", 1000, "Overtime-test")
            val frame = runBlocking { withTimeoutOrNull(20_000) { player.frame.first { it != null } } }
            assertNotNull(frame, "no video frame received within 20 s; state=${player.state.value}")
            assertTrue(frame.width > 0 && frame.height > 0)
            println("Frame ${frame.width}x${frame.height}, state=${player.state.value}")
        } finally {
            player.release()
        }
    }
}
