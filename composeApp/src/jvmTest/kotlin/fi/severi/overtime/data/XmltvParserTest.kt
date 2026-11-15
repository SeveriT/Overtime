package fi.severi.overtime.data

import fi.severi.overtime.model.Channel
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class XmltvParserTest {
    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="yle1.fi"><display-name>Yle TV1</display-name><display-name>TV1</display-name></channel>
          <programme start="20261115200000 +0200" stop="20261115210000 +0200" channel="yle1.fi">
            <title>News</title><desc>Evening news</desc>
          </programme>
          <programme start="20261115210000 +0200" stop="20261115220000 +0200" channel="yle1.fi"><title>Sports</title></programme>
        </tv>
    """.trimIndent()

    // 2026-11-15 20:30 +02:00 (= 18:30 UTC)
    private val duringNews = 1_794_767_400_000L

    @Test
    fun parsesProgrammesAndNames() {
        val epg = parseXmltv(xml.encodeToByteArray())
        val list = assertNotNull(epg.programmes["yle1.fi"])
        assertEquals(listOf("News", "Sports"), list.map { it.title })
        assertEquals("Evening news", list[0].description)
        assertEquals(60 * 60 * 1000L, list[0].stop - list[0].start)
        assertEquals("yle1.fi", epg.nameToId["tv1"])
    }

    @Test
    fun handlesGzipInput() {
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(xml.encodeToByteArray()) } }.toByteArray()
        assertEquals(2, parseXmltv(gz).programmes["yle1.fi"]?.size)
    }

    @Test
    fun nowNextMatchesByIdThenByName() {
        val epg = parseXmltv(xml.encodeToByteArray())
        val byId = Channel("c1", "s", "Whatever", "g", "http://x", epgId = "yle1.fi")
        val (now, next) = epg.nowNext(byId, duringNews)
        assertEquals("News", now?.title)
        assertEquals("Sports", next?.title)

        val byName = Channel("c2", "s", "Yle TV1 HD", "g", "http://x")
        assertEquals("News", epg.nowNext(byName, duringNews).first?.title)

        val unknown = Channel("c3", "s", "Nope", "g", "http://x")
        assertNull(epg.nowNext(unknown, duringNews).first)
    }

    @Test
    fun guideListsProgrammesAndSpan() {
        val epg = parseXmltv(xml.encodeToByteArray())
        val channel = Channel("c1", "s", "TV1", "g", "http://x")
        assertEquals(listOf("News", "Sports"), epg.programmesFor(channel).map { it.title })
        assertEquals(emptyList(), epg.programmesFor(Channel("c3", "s", "Nope", "g", "http://x")))
        val (from, to) = epg.span!!
        assertEquals(2 * 60 * 60 * 1000L, to - from)
        assertNull(parseXmltv(ByteArray(0)).span)
    }

    @Test
    fun mergesGuides() {
        val first = parseXmltv(xml.encodeToByteArray())
        val second = parseXmltv(
            """
            <tv>
              <channel id="mtv3.fi"><display-name>MTV3</display-name></channel>
              <programme start="20261115200000 +0200" stop="20261115210000 +0200" channel="mtv3.fi"><title>Uutiset</title></programme>
              <programme start="20261115210000 +0200" stop="20261115220000 +0200" channel="yle1.fi"><title>Sports</title></programme>
              <programme start="20261115220000 +0200" stop="20261115230000 +0200" channel="yle1.fi"><title>Late film</title></programme>
            </tv>
            """.trimIndent().encodeToByteArray(),
        )
        val merged = first + second
        assertEquals(listOf("News", "Sports", "Late film"), merged.programmesFor(Channel("a", "s", "TV1", "g", "u")).map { it.title })
        assertEquals(listOf("Uutiset"), merged.programmesFor(Channel("b", "s", "MTV3", "g", "u")).map { it.title })
    }
}
