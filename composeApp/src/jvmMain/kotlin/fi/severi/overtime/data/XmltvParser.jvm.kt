package fi.severi.overtime.data

import fi.severi.overtime.model.EpgData
import fi.severi.overtime.model.Programme
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

private val xmltvTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

/** Streaming XMLTV parser; handles multi-megabyte guides without building a DOM. */
actual fun parseXmltv(bytes: ByteArray): EpgData {
    if (bytes.isEmpty()) return EpgData()
    val input: InputStream =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(bytes))
        else ByteArrayInputStream(bytes)

    val factory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.IS_COALESCING, true)
    }
    val reader = factory.createXMLStreamReader(input)

    val nameToId = HashMap<String, String>()
    val programmes = HashMap<String, ArrayList<Programme>>()

    var channelId: String? = null
    var pChannel = ""
    var pStart = 0L
    var pStop = 0L
    val title = StringBuilder()
    val desc = StringBuilder()
    var text: StringBuilder? = null

    try {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "channel" -> channelId = reader.getAttributeValue(null, "id")
                    "display-name" -> if (channelId != null) text = StringBuilder()
                    "programme" -> {
                        pChannel = reader.getAttributeValue(null, "channel").orEmpty()
                        pStart = parseXmltvTime(reader.getAttributeValue(null, "start"))
                        pStop = parseXmltvTime(reader.getAttributeValue(null, "stop"))
                        title.setLength(0)
                        desc.setLength(0)
                    }
                    "title" -> if (channelId == null) text = title
                    "desc" -> if (channelId == null) text = desc
                }
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text?.append(reader.text)
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "display-name" -> {
                        val id = channelId
                        // Repaired like the channel names, so the two still match
                        val name = TextRepair.repair(text?.toString()?.trim().orEmpty())
                        if (id != null && name.isNotEmpty()) nameToId.putIfAbsent(name.lowercase(), id)
                        text = null
                    }
                    "channel" -> channelId = null
                    "title", "desc" -> text = null
                    "programme" -> if (pChannel.isNotEmpty() && pStart > 0 && pStop > pStart) {
                        programmes.getOrPut(pChannel) { ArrayList() } +=
                            Programme(
                                pChannel, pStart, pStop,
                                TextRepair.repair(title.toString().trim()),
                                TextRepair.repair(desc.toString().trim())
                            )
                    }
                }
            }
        }
    } finally {
        reader.close()
    }

    programmes.values.forEach { it.sortBy(Programme::start) }
    return EpgData(nameToId = nameToId, programmes = programmes)
}

/** Parses "20261115203000 +0200" (offset optional, seconds optional). Returns 0 when unparseable. */
private fun parseXmltvTime(value: String?): Long {
    if (value.isNullOrBlank()) return 0
    return runCatching {
        val digits = value.takeWhile(Char::isDigit).padEnd(14, '0').take(14)
        val local = LocalDateTime.parse(digits, xmltvTime)
        val rest = value.drop(value.indexOfFirst { !it.isDigit() }.coerceAtLeast(0)).trim()
        val offset = if (rest.isEmpty() || value.all(Char::isDigit)) ZoneId.systemDefault().rules.getOffset(local) else ZoneOffset.of(rest)
        local.toInstant(offset).toEpochMilli()
    }.getOrDefault(0)
}
