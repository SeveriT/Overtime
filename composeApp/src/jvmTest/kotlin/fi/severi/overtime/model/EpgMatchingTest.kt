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
}
