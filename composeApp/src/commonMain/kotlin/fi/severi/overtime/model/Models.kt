package fi.severi.overtime.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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

    /**
     * Guide channel ids by [idKey], for ids that differ from the playlist's only in case or spacing
     * ("jim.fi" vs "Jim.fi", "Eurosport 1.fi" vs "Eurosport1.fi"). The first id wins a clash.
     */
    private val idsByKey: Map<String, String> by lazy {
        HashMap<String, String>().also { map -> programmes.keys.forEach { map.putIfAbsent(idKey(it), it) } }
    }

    /** Resolves the XMLTV channel id for a channel, by tvg-id first and then by display name. */
    fun resolveId(channel: Channel): String? {
        channel.epgId?.let { id ->
            if (programmes.containsKey(id)) return id
            // Playlists and guides don't always agree on case or spaces.
            idsByKey[idKey(id)]?.let { return it }
        }
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
    /** Reload every source's channels and guide in the background when the app opens. */
    val refreshOnLaunch: Boolean = true,
    /** Start the next episode as soon as one ends. */
    val autoplayNext: Boolean = true,
    /** Up next appears this long before an episode ends, roughly when its end credits start. */
    val upNextLead: UpNextLead = UpNextLead.S45,
    // Library poster cards
    val posterWidth: PosterWidth = PosterWidth.LARGE,
    val posterCorners: PosterCorners = PosterCorners.CLASSIC,
    val landscapePosters: Boolean = true,
    val hidePosterLabels: Boolean = true,
    /** Rated artwork from [posterTemplate] when a title has a TMDB or IMDb ID. */
    val ratedPosters: Boolean = true,
    val posterTemplate: String = DEFAULT_POSTER_TEMPLATE,
    val lastSourceId: String? = null,
    val lastChannelId: String? = null,
    val favorites: Set<String> = emptySet(),
    /** Starred channel groups as "<source id>|<group name>"; listed first, in their usual order. */
    val favoriteGroups: Set<String> = emptySet(),
    val volume: Int = 100,
    val subtitles: SubtitleStyle = SubtitleStyle(),
    /** Subtitles to turn on for movies and episodes when the file has them; null leaves VLC's choice. */
    val subtitleLanguage: SubtitleLanguage? = null,
    /** Folders of the user's own movies and series (Movies & series > Local files). */
    val localFolders: List<String> = emptyList(),
    /** Look up posters for local files online (their titles go to Stremio's Cinemeta). */
    val localArtworkOnline: Boolean = true,
    /** Keyboard shortcuts the user changed, by Shortcut name; anything missing uses the default keys. */
    val shortcuts: Map<String, List<KeyCombo>> = emptyMap(),
    val picture: PictureAdjust = PictureAdjust(),
    /** Window bounds (dp) when it was last closed, restored at launch; null until first closed. */
    val window: WindowBounds? = null,
)

/**
 * Picture adjustments applied while drawing: [brightness] is an offset (-0.5..0.5, 0 = none),
 * [contrast] (0.5..1.5) and [saturation] (0..2) are factors, 1 = none.
 */
@Serializable
data class PictureAdjust(val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f) {
    val isDefault: Boolean get() = this == PictureAdjust()
}

/**
 * A subtitle language: its ISO 639 codes (VLC matches tagged tracks by these) and the names a track
 * might carry instead when it isn't tagged.
 */
@Serializable
enum class SubtitleLanguage(val label: String, val codes: List<String>, val native: String) {
    ENGLISH("English", listOf("en", "eng"), "English"),
    FINNISH("Finnish", listOf("fi", "fin"), "Suomi"),
    SWEDISH("Swedish", listOf("sv", "swe"), "Svenska"),
    NORWEGIAN("Norwegian", listOf("no", "nor", "nob", "nb"), "Norsk"),
    DANISH("Danish", listOf("da", "dan"), "Dansk"),
    ESTONIAN("Estonian", listOf("et", "est"), "Eesti"),
    GERMAN("German", listOf("de", "ger", "deu"), "Deutsch"),
    FRENCH("French", listOf("fr", "fre", "fra"), "Français"),
    SPANISH("Spanish", listOf("es", "spa"), "Español"),
    ITALIAN("Italian", listOf("it", "ita"), "Italiano"),
    DUTCH("Dutch", listOf("nl", "dut", "nld"), "Nederlands"),
    PORTUGUESE("Portuguese", listOf("pt", "por"), "Português"),
    POLISH("Polish", listOf("pl", "pol"), "Polski"),
    RUSSIAN("Russian", listOf("ru", "rus"), "Русский"),
    TURKISH("Turkish", listOf("tr", "tur"), "Türkçe"),
    ARABIC("Arabic", listOf("ar", "ara"), "العربية"),
    ;

    /** Whether a track name like "Track 3 - [Finnish]", "Suomi" or "fin" is this language. */
    fun matches(trackName: String): Boolean {
        val name = trackName.lowercase()
        if (name.contains(label.lowercase()) || name.contains(native.lowercase())) return true
        val words = name.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return codes.any { it in words }
    }
}

/** How VLC draws plain-text subtitles (SRT and the like); styled ASS subtitles keep their own look. */
@Serializable
data class SubtitleStyle(
    val font: SubtitleFont = SubtitleFont.SEGOE,
    val size: SubtitleSize = SubtitleSize.EXTRA_SMALL,
    /** Text colour as #RRGGBB. */
    val color: String = "#FFDE00",
    val edge: SubtitleEdge = SubtitleEdge.OUTLINE,
    val bold: Boolean = false,
)

@Serializable
enum class SubtitleFont(val label: String, val family: String) {
    ARIAL("Arial", "Arial"),
    SEGOE("Segoe UI", "Segoe UI"),
    VERDANA("Verdana", "Verdana"),
    TAHOMA("Tahoma", "Tahoma"),
    GEORGIA("Georgia", "Georgia"),
}

@Serializable
enum class SubtitleSize(val label: String, val percent: Int) {
    TINY("Tiny", 50),
    EXTRA_SMALL("Extra small", 65),
    SMALL("Small", 80),
    NORMAL("Normal", 100),
    LARGE("Large", 125),
    EXTRA_LARGE("Extra large", 150),
}

/** What keeps the text readable on bright pictures. */
@Serializable
enum class SubtitleEdge(val label: String) {
    OUTLINE("Outline"),
    SHADOW("Drop shadow"),
    BOX("Background box"),
}

@Serializable
enum class UpNextLead(val label: String, val seconds: Int) {
    S30("30 s", 30),
    S45("45 s", 45),
    S60("60 s", 60),
    S90("90 s", 90),
}

/** A key plus modifiers. [key] is Compose's Key.keyCode. */
@Serializable
data class KeyCombo(val key: Long, val shift: Boolean = false, val ctrl: Boolean = false, val alt: Boolean = false)

@Serializable
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean = false)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
enum class PosterWidth(val label: String, val dp: Int) {
    // Older versions had six sizes; their saved names map to the nearest of these.
    @JsonNames("COMPACT", "DENSE", "STANDARD")
    SMALL("Small", 150),

    @JsonNames("BALANCED", "COMFORT")
    MEDIUM("Medium", 180),

    LARGE("Large", 220),
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

data class GroupEntry(val name: String, val count: Int, val favorite: Boolean = false)

/** Key of a group in [AppSettings.favoriteGroups]. */
fun favoriteGroupKey(sourceId: String, group: String) = "$sourceId|$group"

const val GROUP_ALL = "All channels"
const val GROUP_FAVORITES = "Favorites"

/** A channel id compared loosely: lower case, without spaces. */
private fun idKey(id: String): String = id.lowercase().filterNot { it.isWhitespace() }
