package fi.severi.overtime.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class M3uParserTest {
    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="yle1.fi" tvg-name="Yle TV1" tvg-logo="http://x/yle1.png" group-title="Finland",Yle TV1 HD
        http://example.com/live/1.ts
        #EXTINF:-1,Plain, with comma
        #EXTGRP:Sports
        http://example.com/live/2.m3u8
        #EXTVLCOPT:http-user-agent=Foo
        #EXTINF:0 group-title="News" , Spaced Name
        http://example.com/live/3.ts
    """.trimIndent()

    @Test
    fun parsesAttributesAndNames() {
        val channels = M3uParser.parse(playlist, "src")
        assertEquals(3, channels.size)

        val first = channels[0]
        assertEquals("Yle TV1 HD", first.name)
        assertEquals("Finland", first.group)
        assertEquals("yle1.fi", first.epgId)
        assertEquals("http://x/yle1.png", first.logo)
        assertEquals("http://example.com/live/1.ts", first.url)
        assertEquals("src", first.sourceId)

        val second = channels[1]
        assertEquals("Plain, with comma", second.name)
        assertEquals("Sports", second.group)
        assertNull(second.logo)

        assertEquals("Spaced Name", channels[2].name)
        assertEquals("News", channels[2].group)
    }

    @Test
    fun readsGuideUrlsFromHeader() {
        val text = "#EXTM3U x-tvg-url=\"http://a.example/guide.xml.gz, https://b.example/epg.xml\" url-tvg=\"http://a.example/guide.xml.gz\"\n" +
            "#EXTINF:-1,One\nhttp://example.com/1.ts"
        val playlist = M3uParser.parsePlaylist(text, "src")
        assertEquals(listOf("http://a.example/guide.xml.gz", "https://b.example/epg.xml"), playlist.guideUrls)
        assertEquals(1, playlist.channels.size)
        assertEquals(emptyList(), M3uParser.parsePlaylist(playlistWithoutHeader, "src").guideUrls)
    }

    private val playlistWithoutHeader = "#EXTINF:-1,One\nhttp://example.com/1.ts"

    @Test
    fun idsAreStableAcrossRuns() {
        val a = M3uParser.parse(playlist, "src").map { it.id }
        val b = M3uParser.parse(playlist, "src").map { it.id }
        assertEquals(a, b)
        assertEquals(3, a.toSet().size)
    }
}
