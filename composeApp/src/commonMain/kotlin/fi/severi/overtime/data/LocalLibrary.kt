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

    companion object {
        val EMPTY = LocalIndex(emptyList(), emptyList())
    }
}

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

    data class EpisodeName(val show: String, val season: Int, val episode: Int, val title: String?)

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
                id = "show:$key", kind = LibraryKind.SERIES, name = name, poster = art, rating = null, year = null,
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
            val before = clean(stem.substring(0, match.range.first))
            val after = clean(stripJunk(stem.substring(match.range.last + 1))).ifEmpty { null }
            return EpisodeName(before.ifEmpty { showFromFolders(file.folders) ?: "Unknown series" }, season, number, after)
        }
        // "Show/Season 2/05 - Title.mkv"
        val folder = file.folders.lastOrNull()?.trim() ?: return null
        val season = seasonFolder.matchEntire(folder)?.groupValues?.get(1)?.toInt() ?: return null
        val trimmed = stem.trim()
        val lead = leadingNumber.find(trimmed) ?: return null
        val number = lead.groupValues[1].toInt()
        val title = clean(stripJunk(trimmed.substring(lead.range.last + 1))).ifEmpty { null }
        val show = showFromFolders(file.folders.dropLast(1)) ?: return null
        return EpisodeName(show, season, number, title)
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

    /** Dots and underscores to spaces, bracketed tags and stray separators removed. */
    fun clean(text: String): String =
        text.replace(Regex("""\[[^\]]*]"""), " ")
            .replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\(\s*\)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', ' ', '(', ')')
            .trim()
}

/** Video files under the given folders, with their artwork. Blocking: call off the UI thread. */
expect fun scanLocalFolders(folders: List<String>): List<LocalFile>

/** Asks for a folder with the system picker; null when cancelled. */
expect fun pickFolder(): String?
