package fi.severi.overtime.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EpgMatchingTest {
    private val programme = Programme("Jim.fi", start = 0, stop = 60_000, title = "Uutiset")
    private val guide = EpgData(nameToId = mapOf("jim.fi" to "Jim.fi"), programmes = mapOf("Jim.fi" to listOf(programme)))

    @Test
    fun tvgIdMatchesRegardlessOfCase() {
        // m3u4u's playlist says "jim.fi" while its guide says "Jim.fi".
        val channel = Channel("s:1", "s", "JIM HD", "TV", "http://x/1.ts", epgId = "jim.fi")
        assertEquals("Jim.fi", guide.resolveId(channel))
        assertEquals(programme, guide.nowNext(channel, now = 30_000).first)
    }

    @Test
    fun tvgIdMatchesRegardlessOfSpaces() {
        fun guideWith(id: String) = EpgData(programmes = mapOf(id to listOf(Programme(id, 0, 60_000, "Sport"))))
        fun channel(epgId: String) = Channel("s:2", "s", "Channel", "Sport", "http://x/2.ts", epgId = epgId)
        // Playlist with spaces, guide without, and the other way round.
        assertEquals("Eurosport1.fi", guideWith("Eurosport1.fi").resolveId(channel("Eurosport 1.fi")))
        assertEquals("BBC Three HD.uk", guideWith("BBC Three HD.uk").resolveId(channel("BBCThreeHD.uk")))
    }
}
