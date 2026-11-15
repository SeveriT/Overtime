package fi.severi.overtime.model

/** On-demand content from an Xtream Codes provider. */
enum class LibraryKind(val label: String) {
    MOVIES("Movies"),
    SERIES("Series"),
}

data class LibraryCategory(val id: String, val name: String)

/** A movie or a series as listed in a category. */
data class LibraryItem(
    /** Xtream stream_id (movies) or series_id (series). */
    val id: String,
    val kind: LibraryKind,
    val name: String,
    val poster: String?,
    /** Rating as the provider gives it, usually out of 10. */
    val rating: String?,
    val year: String?,
    val categoryId: String,
    /** Movie file extension for the play URL (mkv, mp4…). */
    val extension: String?,
    /** When the provider added it, epoch seconds; 0 if unknown. */
    val added: Long = 0,
)

data class MovieDetails(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val duration: String?,
    val backdrop: String?,
)

data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val extension: String,
    val duration: String?,
    val plot: String?,
    val image: String?,
)

data class Season(val number: Int, val name: String, val episodes: List<Episode>)

data class SeriesDetails(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val releaseDate: String?,
    val backdrop: String?,
    val seasons: List<Season>,
)

/** What the player shows for an on-demand title. */
data class VodPlayback(val title: String, val subtitle: String?, val poster: String?)
