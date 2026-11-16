package fi.severi.overtime.data

import fi.severi.overtime.model.Episode
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.Season
import fi.severi.overtime.model.SeriesDetails

/** Source id (and category id) of titles from the user's own folders. */
const val LOCAL_SOURCE = "local"

/** A video file found in a library folder, with any artwork next to it (as file URIs). */
data class LocalFile(
    val path: String,
    /** File name with extension. */
    val name: String,
    /** Folder names from the library folder down to the file's folder (the file's own folder last). */
    val folders: List<String>,
    /** Artwork named after the video ("Movie.jpg", "Movie-poster.jpg"). */
    val ownArt: String? = null,
    /** poster/folder/cover image in the file's folder. */
    val folderArt: String? = null,
    /** poster/folder/cover image in the folder above (a show's folder, above "Season 1"). */
    val parentArt: String? = null,
    /** Video files in the same folder; a folder poster only belongs to a movie that's alone in it. */
    val videosInFolder: Int = 1,
)

/** The user's collection as library items: movies, and series with their seasons and episodes. */
data class LocalIndex(val movies: List<LibraryItem>, val shows: List<Pair<LibraryItem, SeriesDetails>>) {
    val isEmpty: Boolean get() = movies.isEmpty() && shows.isEmpty()

    fun show(id: String): SeriesDetails? = shows.firstOrNull { it.first.id == id }?.second

    /** [art] applied to the title [id] (posters only where the files didn't bring their own). */
    fun withArt(id: String, art: LocalArt): LocalIndex = LocalIndex(
        movies = movies.map { if (it.id == id) it.withArt(art) else it },
        shows = shows.map { (item, details) ->
            if (item.id == id) item.withArt(art) to details.copy(backdrop = details.backdrop ?: art.backdrop, imdbId = art.imdbId) else item to details
        },
    )

    /**
     * Series found under different names that are the same show (same IMDb id, e.g. "The Office US" and
     * "The Office Extended Cut") become one, their seasons combined.
     */
    fun mergeSameShows(): LocalIndex {
        val merged = shows.groupBy { it.first.imdbId ?: it.first.id }.map { (_, group) ->
            if (group.size == 1) return@map group.single()
            val (first, firstDetails) = group.first()
            val seasons = group.flatMap { it.second.seasons }.groupBy { it.number }.toSortedMap().map { (number, parts) ->
                Season(number, "Season $number", parts.flatMap { it.episodes }.sortedBy { it.number })
            }
            first to firstDetails.copy(seasons = seasons)
        }
        return LocalIndex(movies, merged.sortedBy { it.first.name.lowercase() })
    }

    private fun LibraryItem.withArt(art: LocalArt) = copy(
        poster = poster ?: art.poster,
        backdrop = backdrop ?: art.backdrop,
        imdbId = imdbId ?: art.imdbId,
    )

    companion object {
        val EMPTY = LocalIndex(emptyList(), emptyList())
    }
}

/** Artwork and IMDb id found online for a local title. */
@kotlinx.serialization.Serializable
data class LocalArt(val imdbId: String? = null, val poster: String? = null, val backdrop: String? = null)

/** Video file types scanned for. */
val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "mts", "webm", "mpg", "mpeg", "flv", "ogv", "3gp", "divx")

/**
 * Sorts file names into movies and series. Episodes are recognised by "S01E02", "1x02", or a number at
 * the start of a file inside a "Season 1" folder; the show name comes from the file name, or from the
 * folder when the file name is just the episode.
 */
object LocalLibraryParser {
    private val sxe = Regex("""(?i)(?:^|[^a-z0-9])s(\d{1,2})[ ._-]*e(\d{1,3})""")
    private val nxn = Regex("""(?i)(?:^|[^0-9])(\d{1,2})x(\d{2,3})(?![0-9])""")
    private val seasonFolder = Regex("""(?i)^(?:season|series|kausi|staffel|saison|temporada|s)[ ._-]*(\d{1,2})$""")
    private val leadingNumber = Regex("""^(\d{1,3})(?![0-9])""")
    private val year = Regex("""(?:^|[^0-9])((?:19|20)\d{2})(?![0-9])""")
    private val junk = Regex(
        """(?i)\b(2160p|1080p|720p|576p|480p|4k|uhd|hdr10\+?|hdr|dv|bluray|blu-ray|bdrip|brrip|web-?dl|webrip|web|hdtv|dvdrip|""" +
            """x\.?264|x\.?265|h\.?264|h\.?265|hevc|avc|aac|ac3|eac3|dts|ddp?|atmos|10bit|remux|proper|repack|extended|unrated|""" +
            """internal|multi|nordic|subs?)\b.*""",
    )

    data class EpisodeName(val show: String, val season: Int, val episode: Int, val title: String?, val year: String? = null)

    fun parse(files: List<LocalFile>): LocalIndex {
        val movies = ArrayList<LibraryItem>()
        // Show key -> (name, art, episodes)
        val shows = LinkedHashMap<String, Triple<String, String?, MutableList<Pair<EpisodeName, LocalFile>>>>()
        for (file in files) {
            val episode = episodeOf(file)
            if (episode == null) {
                movies += movieOf(file)
            } else {
                val key = episode.show.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { episode.show.lowercase() }
                val art = if (file.folders.lastOrNull()?.let { seasonFolder.matches(it.trim()) } == true) file.parentArt ?: file.folderArt else file.folderArt
                val entry = shows.getOrPut(key) { Triple(episode.show, art, mutableListOf()) }
                if (entry.second == null && art != null) shows[key] = entry.copy(second = art)
                shows.getValue(key).third += episode to file
            }
        }
        val showItems = shows.map { (key, value) ->
            val (name, art, episodes) = value
            val item = LibraryItem(
                id = "show:$key", kind = LibraryKind.SERIES, name = name, poster = art, rating = null,
                year = episodes.firstNotNullOfOrNull { it.first.year },
                categoryId = LOCAL_SOURCE, extension = null, backdrop = art,
            )
            val seasons = episodes.groupBy { it.first.season }.toSortedMap().map { (number, list) ->
                Season(
                    number = number,
                    name = "Season $number",
                    episodes = list.sortedBy { it.first.episode }.map { (ep, file) ->
                        Episode(
                            id = file.path, number = ep.episode, title = ep.title.orEmpty(),
                            extension = file.name.substringAfterLast('.', "mkv"), duration = null, plot = null, image = file.ownArt,
                        )
                    },
                )
            }
            item to SeriesDetails(plot = null, genre = null, cast = null, releaseDate = null, backdrop = art, seasons = seasons)
        }.sortedBy { it.first.name.lowercase() }
        return LocalIndex(movies.sortedBy { it.name.lowercase() }, showItems)
    }

    fun episodeOf(file: LocalFile): EpisodeName? {
        val stem = file.name.substringBeforeLast('.')
        val match = sxe.find(stem) ?: nxn.find(stem)
        if (match != null) {
            val season = match.groupValues[1].toInt()
            val number = match.groupValues[2].toInt()
            // The match may start on the separator before "S01E02"; everything before it is the show name.
            val (before, showYear) = splitYear(stem.substring(0, match.range.first))
            val after = clean(stripJunk(stem.substring(match.range.last + 1))).ifEmpty { null }
            if (before.isNotEmpty()) return EpisodeName(before, season, number, after, showYear)
            val (folderName, folderYear) = showFromFolders(file.folders)?.let(::splitYear) ?: ("Unknown series" to null)
            return EpisodeName(folderName, season, number, after, folderYear)
        }
        // "Show/Season 2/05 - Title.mkv"
        val folder = file.folders.lastOrNull()?.trim() ?: return null
        val season = seasonFolder.matchEntire(folder)?.groupValues?.get(1)?.toInt() ?: return null
        val trimmed = stem.trim()
        val lead = leadingNumber.find(trimmed) ?: return null
        val number = lead.groupValues[1].toInt()
        val title = clean(stripJunk(trimmed.substring(lead.range.last + 1))).ifEmpty { null }
        val (show, showYear) = showFromFolders(file.folders.dropLast(1))?.let(::splitYear) ?: return null
        return EpisodeName(show, season, number, title, showYear)
    }

    fun movieOf(file: LocalFile): LibraryItem {
        val stem = file.name.substringBeforeLast('.')
        val yearMatch = year.findAll(stem).lastOrNull { it.range.first > 0 }
        val title = if (yearMatch != null) clean(stem.substring(0, yearMatch.range.first + 1)) else clean(stripJunk(stem))
        val art = file.ownArt ?: file.folderArt?.takeIf { file.videosInFolder == 1 }
        return LibraryItem(
            id = file.path, kind = LibraryKind.MOVIES, name = title.ifEmpty { stem }, poster = art, rating = null,
            year = yearMatch?.groupValues?.get(1), categoryId = LOCAL_SOURCE, extension = file.name.substringAfterLast('.', "mkv"),
            backdrop = art,
        )
    }

    /** The nearest folder that isn't a season folder, as a show name. */
    private fun showFromFolders(folders: List<String>): String? =
        folders.asReversed().map { it.trim() }.firstOrNull { it.isNotEmpty() && !seasonFolder.matches(it) }?.let(::clean)?.ifEmpty { null }

    private fun stripJunk(text: String) = junk.replace(text, "")

    /** "True.Detective.(2014)." -> "True Detective" and "2014"; a name without a trailing year is just cleaned. */
    fun splitYear(text: String): Pair<String, String?> {
        val match = year.findAll(text).lastOrNull { it.range.first > 0 }
        if (match != null) {
            val name = clean(text.substring(0, match.range.first + 1))
            // Only a year at the end (after the name) is a release year; "Blade Runner 2049 S01" keeps its number.
            val rest = clean(text.substring(match.range.last + 1))
            if (name.isNotEmpty() && rest.isEmpty()) return name to match.groupValues[1]
        }
        return clean(text) to null
    }

    /** Dots and underscores to spaces, bracketed tags and stray separators removed. */
    fun clean(text: String): String =
        text.replace(Regex("""\[[^\]]*]"""), " ")
            .replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\(\s*\)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', ' ', ')')
            // A bracket left open by cutting the name short, e.g. "Show (" before a removed year.
            .removeSuffix("(")
            .trim('-', ' ', '(', ')')
            .trim()
}

/** Video files under the given folders, with their artwork. Blocking: call off the UI thread. */
expect fun scanLocalFolders(folders: List<String>): List<LocalFile>

/** Asks for a folder with the system picker; null when cancelled. */
expect fun pickFolder(): String?
