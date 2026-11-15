package fi.severi.overtime.data

import fi.severi.overtime.model.Channel

object M3uParser {
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String, sourceId: String): List<Channel> {
        val channels = ArrayList<Channel>()
        var pending: PendingEntry? = null
        var extGroup: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val body = line.substringAfter(':', "")
                    val attrs = attrRegex.findAll(body).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val rest = attrRegex.replace(body, "")
                    val name = rest.substringAfter(',', "").trim().ifEmpty { attrs["tvg-name"].orEmpty() }
                    pending = PendingEntry(name, attrs)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                else -> {
                    val entry = pending
                    if (entry != null) {
                        val group = entry.attrs["group-title"]?.takeIf { it.isNotBlank() } ?: extGroup ?: "Ungrouped"
                        channels += Channel(
                            id = "$sourceId:${line.hashCode().toUInt().toString(16)}",
                            sourceId = sourceId,
                            name = entry.name.ifEmpty { line.substringAfterLast('/') },
                            group = group,
                            url = line,
                            logo = entry.attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                            epgId = entry.attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        )
                    }
                    pending = null
                    extGroup = null
                }
            }
        }
        return channels
    }

    private class PendingEntry(val name: String, val attrs: Map<String, String>)
}
