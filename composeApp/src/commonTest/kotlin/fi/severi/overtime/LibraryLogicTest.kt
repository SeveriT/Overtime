package fi.severi.overtime

import fi.severi.overtime.data.PosterArt
import fi.severi.overtime.model.DEFAULT_POSTER_TEMPLATE
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.WatchProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryLogicTest {
    @Test
    fun fillsPosterTemplate() {
        val template = "https://p.example/poster?tmdb_id={tmdb_id?}&imdb_id={imdb_id?}&stremio_id={id}&type={type}&shape={shape}"
        assertEquals(
            "https://p.example/poster?tmdb_id=27205&imdb_id=tt1375666&stremio_id=tt1375666&type=movie&shape=poster",
            PosterArt.url(template, LibraryKind.MOVIES, "27205", "tt1375666", landscape = false),
        )
        // Only TMDB: the optional IMDb slot is left empty, the Stremio id falls back to tmdb:<id>.
        assertEquals(
            "https://p.example/poster?tmdb_id=1396&imdb_id=&stremio_id=tmdb:1396&type=series&shape=landscape",
            PosterArt.url(template, LibraryKind.SERIES, "1396", null, landscape = true),
        )
        // Bare IMDb numbers get their tt prefix.
        assertTrue(PosterArt.url(template, LibraryKind.MOVIES, null, "1375666", false)!!.contains("imdb_id=tt1375666"))
        // No usable id at all, or a required one missing: no URL, so the provider poster is used.
        assertNull(PosterArt.url(template, LibraryKind.MOVIES, null, null, false))
        assertNull(PosterArt.url(template, LibraryKind.MOVIES, "0", "", false))
        assertNull(PosterArt.url("https://p.example/{imdb_id}.jpg", LibraryKind.MOVIES, "27205", null, false))
        // The shipped default is well-formed.
        assertTrue(PosterArt.url(DEFAULT_POSTER_TEMPLATE, LibraryKind.MOVIES, "27205", null, false)!!.startsWith("https://postersplus.slokker.cc/poster?tmdb_id=27205&"))
    }

    private fun entry(
        key: String,
        position: Long,
        updatedAt: Long,
        series: String? = null,
        nextUp: Boolean = false,
        dismissed: Boolean = false,
    ) = WatchProgress(
        key = key, sourceId = "s", kind = if (series == null) LibraryKind.MOVIES else LibraryKind.SERIES,
        itemId = series ?: key, title = key, extension = "mp4", positionMs = position, durationMs = 1000,
        updatedAt = updatedAt, episodeId = if (series != null) key else null, nextUp = nextUp, dismissed = dismissed,
    )

    @Test
    fun continueWatchingRules() {
        val entries = listOf(
            entry("movie-half", 500, updatedAt = 10),
            entry("movie-peek", 5, updatedAt = 20),        // under 2 %: not a card
            entry("movie-done", 990, updatedAt = 30),      // finished
            entry("ep1", 990, updatedAt = 40, series = "show"),
            entry("ep2", 0, updatedAt = 41, series = "show", nextUp = true),
            entry("other-ep3", 980, updatedAt = 50, series = "other"), // finished: a series stays (as watched)
            entry("other-ep2", 400, updatedAt = 5, series = "other"),  // older entry of the same series: ignored
        )
        assertEquals(listOf("other-ep3", "ep2", "movie-half"), WatchHistory.continueWatching(entries).map { it.key })
    }

    @Test
    fun dismissedCardsStayGoneButKeepTheirPosition() {
        val entries = listOf(
            entry("movie-half", 500, updatedAt = 10, dismissed = true),
            entry("ep5", 300, updatedAt = 40, series = "show", dismissed = true),
            entry("ep4", 990, updatedAt = 30, series = "show"), // older episode must not bring the series back
        )
        assertEquals(emptyList(), WatchHistory.continueWatching(entries).map { it.key })

        val history = WatchHistory(listOf(entry("movie-half", 500, updatedAt = 10)), save = {})
        history.dismiss("movie-half")
        assertEquals(500, history.resumeAt("movie-half"))
        // Watching again clears it.
        history.record(entry("movie-half", 600, updatedAt = 20))
        assertEquals(listOf("movie-half"), WatchHistory.continueWatching(history.entries.value).map { it.key })
    }

    @Test
    fun historyResumesOnlyUnfinished() {
        var saved: List<WatchProgress> = emptyList()
        val history = WatchHistory(emptyList()) { saved = it }
        history.record(entry("a", 400, 1))
        history.record(entry("b", 990, 2))
        history.record(entry("c", 0, 3, series = "x", nextUp = true))
        assertEquals(400L, history.resumeAt("a"))
        assertNull(history.resumeAt("b"))
        assertNull(history.resumeAt("c"))
        assertEquals(3, saved.size)
        history.record(entry("a", 600, 4))
        assertEquals(600L, history["a"]?.positionMs)
        assertEquals(3, history.entries.value.size)
    }
}
