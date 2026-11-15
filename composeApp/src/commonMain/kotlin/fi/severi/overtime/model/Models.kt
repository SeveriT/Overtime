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

    /** Combines two guides; where both list a channel, programmes are merged and de-duplicated by start time. */
    operator fun plus(other: EpgData): EpgData {
        if (isEmpty) return other
        if (other.isEmpty) return this
        val merged = HashMap(programmes)
        other.programmes.forEach { (id, list) ->
            merged[id] = merged[id]?.let { mine -> (mine + list).distinctBy { it.start }.sortedBy { it.start } } ?: list
        }
        return EpgData(nameToId = other.nameToId + nameToId, programmes = merged)
    }

    /** All programmes for a channel, sorted by start; empty if the guide doesn't know it. */
    fun programmesFor(channel: Channel): List<Programme> = resolveId(channel)?.let(programmes::get).orEmpty()

    /** Earliest start and latest stop across the whole guide, or null when empty. */
    val span: Pair<Long, Long>? by lazy {
        if (programmes.isEmpty()) null
        else programmes.values.minOf { it.first().start } to programmes.values.maxOf { it.last().stop }
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
    /** Sidebar width in dp, set by dragging its edge. */
    val sidebarWidth: Int = 360,
    /** Sidebar and title bar colour as #RRGGBB; null uses the theme default. */
    val sidebarColor: String? = null,
    /** Accent (primary) colour as #RRGGBB; null uses the theme default blue. */
    val accentColor: String? = null,
    /** Record live streams so they can be paused and rewound. */
    val rewindEnabled: Boolean = true,
    val rewindMinutes: Int = 5,
    // Library poster cards
    val posterWidth: PosterWidth = PosterWidth.BALANCED,
    val posterCorners: PosterCorners = PosterCorners.CLASSIC,
    val landscapePosters: Boolean = false,
    val hidePosterLabels: Boolean = false,
    /** Rated artwork from [posterTemplate] when a title has a TMDB or IMDb ID. */
    val ratedPosters: Boolean = true,
    val posterTemplate: String = DEFAULT_POSTER_TEMPLATE,
    val lastSourceId: String? = null,
    val lastChannelId: String? = null,
    val favorites: Set<String> = emptySet(),
    val volume: Int = 100,
)

@Serializable
enum class PosterWidth(val label: String, val dp: Int) {
    COMPACT("Compact", 100),
    DENSE("Dense", 120),
    STANDARD("Standard", 140),
    BALANCED("Balanced", 160),
    COMFORT("Comfort", 180),
    LARGE("Large", 200),
}

@Serializable
enum class PosterCorners(val label: String, val dp: Int) {
    SHARP("Sharp", 0),
    SUBTLE("Subtle", 4),
    CLASSIC("Classic", 8),
    ROUNDED("Rounded", 16),
    PILL("Pill", 28),
}

/**
 * PostersPlus artwork with rating bars and badges. Placeholders: {tmdb_id} {imdb_id} {id} {type} {shape};
 * a trailing ? ({tmdb_id?}) makes one optional.
 */
const val DEFAULT_POSTER_TEMPLATE =
    "https://postersplus.slokker.cc/poster?tmdb_id={tmdb_id?}&imdb_id={imdb_id?}&stremio_id={id}&type={type}" +
        "&shape={shape}&pc=stremio_tv_nuvio&tg=medium&sm=notch&tst=number&_sm=sash&bp=top_left&fti=1&rdm=2" +
        "&nsfsr=0.050&nsyo=0.93&rb=imdb,metacritic,popcorn,tomatoes&mw=lb:.99,tk:.01&tw=tk:.8,rt:.2&fbs=photoreal" +
        "&sws=1&sbsw=1.40&sbsh=1.20&sbpo=right&bdm=7&bms=5"

data class GroupEntry(val name: String, val count: Int)

const val GROUP_ALL = "All channels"
const val GROUP_FAVORITES = "Favorites"
