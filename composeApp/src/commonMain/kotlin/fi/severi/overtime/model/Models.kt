package fi.severi.overtime.model

import kotlinx.serialization.Serializable

enum class SourceType(val label: String) {
    M3U_URL("M3U playlist URL"),
    M3U_FILE("M3U playlist file"),
    XTREAM("Xtream Codes login"),
}

/** A place channels come from. [url] is the playlist URL, the local file path, or the Xtream server base. */
@Serializable
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    /** XMLTV guide URL. Optional for M3U sources; derived automatically for Xtream. */
    val epgUrl: String = "",
)

@Serializable
data class Channel(
    val id: String,
    val sourceId: String,
    val name: String,
    val group: String,
    val url: String,
    val logo: String? = null,
    /** tvg-id / epg_channel_id used to match XMLTV programmes. */
    val epgId: String? = null,
)

@Serializable
data class Programme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String = "",
)

/** Parsed XMLTV guide. [nameToId] maps every lower-cased display name to its channel id for fallback matching. */
data class EpgData(
    val nameToId: Map<String, String> = emptyMap(),
    val programmes: Map<String, List<Programme>> = emptyMap(),
) {
    val isEmpty get() = programmes.isEmpty()

    /** Resolves the XMLTV channel id for a channel, by tvg-id first and then by display name. */
    fun resolveId(channel: Channel): String? {
        channel.epgId?.let { if (programmes.containsKey(it)) return it }
        val name = channel.name.lowercase()
        nameToId[name]?.let { return it }
        return nameToId[name.removeSuffix(" hd").removeSuffix(" fhd").removeSuffix(" uhd").trim()]
    }

    /** Current and following programme on the channel at [now]. */
    fun nowNext(channel: Channel, now: Long): Pair<Programme?, Programme?> {
        val list = resolveId(channel)?.let(programmes::get) ?: return null to null
        val search = list.binarySearchBy(now) { it.start }
        val index = if (search >= 0) search else -search - 2
        val current = list.getOrNull(index)?.takeIf { it.stop > now }
        val next = list.getOrNull(index + 1)
        return current to next
    }
}

enum class GroupSort(val label: String) {
    PLAYLIST("Playlist order"),
    ALPHABETICAL("A to Z"),
    SIZE("Most channels first"),
}

@Serializable
data class AppSettings(
    val userAgent: String = "VLC/3.0.24 LibVLC/3.0.24",
    val networkCachingMs: Int = 1500,
    val groupSort: GroupSort = GroupSort.PLAYLIST,
    val sidebarVisible: Boolean = true,
    /** Sidebar and title bar colour as #RRGGBB; null uses the theme default. */
    val sidebarColor: String? = null,
    /** Accent (primary) colour as #RRGGBB; null uses the theme default blue. */
    val accentColor: String? = null,
    /** Record live streams so they can be paused and rewound. */
    val rewindEnabled: Boolean = true,
    val rewindMinutes: Int = 5,
    val lastSourceId: String? = null,
    val lastChannelId: String? = null,
    val favorites: Set<String> = emptySet(),
    val volume: Int = 100,
)

data class GroupEntry(val name: String, val count: Int)

const val GROUP_ALL = "All channels"
const val GROUP_FAVORITES = "Favorites"
