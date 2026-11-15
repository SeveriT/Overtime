package fi.severi.overtime.data

import fi.severi.overtime.model.LibraryKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XtreamParsingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val api = XtreamApi(HttpClient(CIO), json)

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    @Test
    fun readsMoviesLeniently() {
        val movie = api.parseItem(
            obj("""{"stream_id": 4521, "name": "Heat (1995)", "stream_icon": null, "rating": "8.3", "category_id": "12", "container_extension": "mkv", "added": "1700000000"}"""),
            LibraryKind.MOVIES,
        )!!
        assertEquals("4521", movie.id)
        assertEquals("1995", movie.year)
        assertNull(movie.poster, "JSON null must not become the text \"null\"")
        assertEquals("8.3", movie.rating)
        assertEquals("mkv", movie.extension)
        assertEquals(1_700_000_000L, movie.added)

        val unrated = api.parseItem(obj("""{"stream_id": "7", "name": "X", "rating": 0, "category_id": "1"}"""), LibraryKind.MOVIES)!!
        assertNull(unrated.rating)
        assertNull(api.parseItem(obj("""{"name": "no id"}"""), LibraryKind.MOVIES))
    }

    @Test
    fun readsArtworkIds() {
        val movie = api.parseItem(
            obj("""{"stream_id": 1, "name": "Inception", "tmdb_id": "27205", "category_id": "1"}"""),
            LibraryKind.MOVIES,
        )!!
        assertEquals("27205", movie.tmdbId)
        val series = api.parseItem(
            obj("""{"series_id": 2, "name": "Breaking Bad", "tmdb": 1396, "backdrop_path": ["http://img/bb.jpg"], "category_id": "1"}"""),
            LibraryKind.SERIES,
        )!!
        assertEquals("1396", series.tmdbId)
        assertEquals("http://img/bb.jpg", series.backdrop)
    }

    @Test
    fun readsSeries() {
        val series = api.parseItem(
            obj("""{"series_id": 88, "name": "Dark", "cover": "http://img/dark.jpg", "releaseDate": "2017-12-01", "category_id": "3"}"""),
            LibraryKind.SERIES,
        )!!
        assertEquals("88", series.id)
        assertEquals("http://img/dark.jpg", series.poster)
        assertEquals("2017", series.year)
    }

    @Test
    fun readsEpisodesKeyedBySeasonOrAsArrays() {
        val keyed = api.parseEpisodes(
            obj(
                """{"episodes": {"1": [{"id": "101", "episode_num": 1, "title": "Secrets", "container_extension": "mp4",
                    "info": {"duration": "00:52:10", "plot": "Missing boy"}}],
                   "2": [{"id": "201", "episode_num": "2", "title": "Lies", "season": 2}]}}""",
            )["episodes"],
        )
        assertEquals(listOf(1, 2), keyed.map { it.first })
        assertEquals("Secrets", keyed[0].second.title)
        assertEquals("52 min", keyed[0].second.duration)
        assertEquals("mp4", keyed[1].second.extension) // default when missing
        assertEquals(2, keyed[1].second.number)

        val nested = api.parseEpisodes(obj("""{"e": [[{"id": "1", "episode_num": 1}], [{"id": "2", "episode_num": 1}]]}""")["e"])
        assertEquals(listOf(1, 2), nested.map { it.first })
    }

    @Test
    fun tidiesDurations() {
        assertEquals("1 h 52 min", XtreamApi.tidyDuration("01:52:31"))
        assertEquals("45 min", XtreamApi.tidyDuration("00:45:00"))
        assertEquals("90 min", XtreamApi.tidyDuration("90 min"))
    }
}
