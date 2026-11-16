package fi.severi.overtime.model

import kotlinx.serialization.Serializable

/** On-demand content from an Xtream Codes provider. */
@Serializable
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
    /** IDs for rated poster artwork, when the provider includes them. */
    val tmdbId: String? = null,
    val imdbId: String? = null,
    /** Wide artwork some panels list for series. */
    val backdrop: String? = null,
)

data class MovieDetails(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val duration: String?,
    val backdrop: String?,
    val tmdbId: String? = null,
    val imdbId: String? = null,
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
    val tmdbId: String? = null,
    val imdbId: String? = null,
)

/** What the player shows for an on-demand title. */
data class VodPlayback(val title: String, val subtitle: String?, val poster: String?)

/**
 * Where the user is in a movie or episode, saved so it can resume and appear under Continue watching.
 * Carries enough to draw the card and start playback without asking the provider again.
 */
@Serializable
data class WatchProgress(
    /** "m:<source>:<movie id>" or "e:<source>:<episode id>". */
    val key: String,
    val sourceId: String,
    val kind: LibraryKind,
    /** Movie id, or the series id for episodes. */
    val itemId: String,
    val title: String,
    val extension: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val episodeId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    /** Queued automatically after the previous episode finished; not started yet. */
    val nextUp: Boolean = false,
    /** Removed from Continue watching by the user; the saved position is kept for resuming. */
    val dismissed: Boolean = false,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = durationMs > 0 && fraction >= FINISHED_AT

    /** Watched long enough to count: 10 seconds (or 2 % of a clip shorter than about 8 minutes). */
    val started: Boolean get() = durationMs > 0 && (positionMs >= STARTED_MS || fraction >= STARTED_AT)

    companion object {
        /** Under STARTED_MS (or STARTED_AT of a short clip) nothing is saved as "started"; above FINISHED_AT it counts as watched. */
        const val STARTED_AT = 0.02f
        const val STARTED_MS = 10_000L
        const val FINISHED_AT = 0.95f
    }
}
