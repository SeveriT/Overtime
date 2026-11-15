package fi.severi.overtime.player

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real libVLC playback. Needs VLC installed and network access; set OVERTIME_SMOKE=1 to enable.
 */
class VlcPlayerSmokeTest {
    private val smoke = System.getenv("OVERTIME_SMOKE") == "1"

    @Test
    fun rendersAFrameFromAnHlsStream() {
        if (!smoke) return
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

    /**
     * Serves real TS segments as a local *live* HLS channel (sliding window, no end), plays it with rewind
     * on, then rewinds to the oldest buffered moment and returns to live — checking frames keep coming.
     */
    @Test
    fun rewindsAndReturnsToLive() {
        if (!smoke) return
        val segments = downloadSegments(count = 6)
        val segmentMs = 4_000L // advertised duration; the window advances this often
        val started = System.currentTimeMillis()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/live.m3u8") { ex ->
            val available = (3 + ((System.currentTimeMillis() - started) / segmentMs).toInt()).coerceAtMost(segments.size)
            val first = available - 3
            val body = buildString {
                append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:$first\n")
                for (i in first until available) append("#EXTINF:4.0,\nseg$i.ts\n")
            }.toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        server.createContext("/seg") { ex ->
            val body = segments[ex.requestURI.path.removePrefix("/seg").removeSuffix(".ts").toInt()]
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        server.start()
        val player = VlcPlayer()
        try {
            player.play("http://127.0.0.1:${server.address.port}/live.m3u8", 1500, "Overtime-test", rewindBufferMs = 300_000)
            val first = runBlocking { withTimeoutOrNull(30_000) { player.frame.first { it != null } } }
            assertNotNull(first, "no frame from buffered playback; state=${player.state.value}")
            val live = runBlocking { withTimeoutOrNull(5_000) { player.state.first { it.timeshift != null } } }?.timeshift
            assertNotNull(live, "no timeshift state")
            println("Live: $live behind=${live.behindMs}")
            assertTrue(live.isLive, "should start live, behind ${live.behindMs} ms")
            // Let the window grow so the oldest moment is clearly further back than "live" allows.
            Thread.sleep(8_000)

            // Rewind to the start of the buffer.
            player.seekTo(live.bufferStart)
            Thread.sleep(3_000)
            val rewound = player.state.value.timeshift!!
            println("Rewound: behind=${rewound.behindMs} playing=${player.state.value.playing}")
            assertFalse(rewound.isLive, "should be behind live after rewinding")
            assertTrue(rewound.behindMs >= 6_000, "behind only ${rewound.behindMs} ms")
            assertFrameAdvances(player, "after rewind")
            // The reported position should stay behind live while playing on. (This checks our clock only;
            // whether VLC's picture stays behind still needs a look in the app.)
            Thread.sleep(5_000)
            val stillBehind = player.state.value.timeshift!!
            assertFalse(stillBehind.isLive, "jumped back to live by itself, behind ${stillBehind.behindMs} ms")

            // And back.
            player.goLive()
            Thread.sleep(3_000)
            val back = player.state.value.timeshift!!
            println("Back live: behind=${back.behindMs}")
            assertTrue(back.isLive, "should be live again, behind ${back.behindMs} ms")
            assertFrameAdvances(player, "after going live")
            assertTrue(player.state.value.error == null, "error: ${player.state.value.error}")
        } finally {
            player.release()
            server.stop(0)
        }
    }

    private fun assertFrameAdvances(player: VlcPlayer, label: String) {
        val before = player.frame.value
        val next = runBlocking { withTimeoutOrNull(5_000) { player.frame.first { it != null && it !== before } } }
        assertNotNull(next, "video stalled $label; state=${player.state.value}")
    }

    /** First [count] TS segments of the lowest-bitrate variant of the public mux test stream. */
    private fun downloadSegments(count: Int): List<ByteArray> {
        val master = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val variant = URI(master).toURL().readText().lines()
            .zipWithNext()
            .filter { (info, _) -> info.startsWith("#EXT-X-STREAM-INF") }
            .minBy { (info, _) -> Regex("""BANDWIDTH=(\d+)""").find(info)!!.groupValues[1].toLong() }
            .second
        val variantUrl = URI(master).resolve(variant)
        val uris = variantUrl.toURL().readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }.take(count)
        return uris.map { variantUrl.resolve(it).toURL().readBytes() }
    }
}
