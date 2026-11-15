package fi.severi.overtime.data

import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.Episode
import fi.severi.overtime.model.LibraryCategory
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.MovieDetails
import fi.severi.overtime.model.Season
import fi.severi.overtime.model.SeriesDetails
import fi.severi.overtime.model.Source
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Xtream Codes "player_api" client: live channels, movies and series.
 *
 * Panels differ a lot: numbers arrive as strings or numbers, empty objects as `[]`, nulls everywhere,
 * episodes keyed by season or as nested arrays. Everything is read leniently.
 */
class XtreamApi(private val client: HttpClient, private val json: Json) {

    // ---- live ------------------------------------------------------------------------

    suspend fun liveChannels(source: Source): List<Channel> {
        val categories = categoryNames(fetchArray(apiUrl(source, "get_live_categories")))
        return fetchArray(apiUrl(source, "get_live_streams")).mapNotNull { obj ->
            val streamId = obj.str("stream_id").ifEmpty { return@mapNotNull null }
            Channel(
                id = "${source.id}:$streamId",
                sourceId = source.id,
                name = obj.str("name"),
                group = categories[obj.str("category_id")] ?: "Other",
                url = "${base(source)}/live/${credentials(source)}/$streamId.ts",
                logo = obj.str("stream_icon").ifBlank { null },
                epgId = obj.str("epg_channel_id").ifBlank { null },
            )
        }
    }

    fun epgUrl(source: Source): String =
        "${base(source)}/xmltv.php?username=${source.username.encodeURLParameter()}&password=${source.password.encodeURLParameter()}"

    // ---- movies & series -------------------------------------------------------------

    suspend fun categories(source: Source, kind: LibraryKind): List<LibraryCategory> {
        val action = if (kind == LibraryKind.MOVIES) "get_vod_categories" else "get_series_categories"
        return fetchArray(apiUrl(source, action))
            .map { LibraryCategory(it.str("category_id"), it.str("category_name")) }
            .filter { it.id.isNotEmpty() }
    }

    /** Items in [categoryId], or the whole library when null. */
    suspend fun items(source: Source, kind: LibraryKind, categoryId: String?): List<LibraryItem> {
        val action = if (kind == LibraryKind.MOVIES) "get_vod_streams" else "get_series"
        val url = apiUrl(source, action) + (categoryId?.let { "&category_id=${it.encodeURLParameter()}" } ?: "")
        return fetchArray(url).mapNotNull { parseItem(it, kind) }
    }

    suspend fun movieDetails(source: Source, item: LibraryItem): MovieDetails {
        val root = fetchObject(apiUrl(source, "get_vod_info") + "&vod_id=${item.id}")
        val info = root.obj("info") ?: JsonObject(emptyMap())
        return MovieDetails(
            plot = info.firstOf("plot", "description"),
            genre = info.firstOf("genre"),
            cast = info.firstOf("cast", "actors"),
            director = info.firstOf("director"),
            releaseDate = info.firstOf("releasedate", "release_date"),
            duration = info.firstOf("duration")?.let(::tidyDuration),
            backdrop = info.firstImage("backdrop_path") ?: info.firstOf("movie_image", "cover_big"),
        )
    }

    suspend fun seriesDetails(source: Source, item: LibraryItem): SeriesDetails {
        val root = fetchObject(apiUrl(source, "get_series_info") + "&series_id=${item.id}")
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val seasonNames = (root["seasons"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .associate { it.str("season_number").toIntOrNull() to it.str("name") }
        return SeriesDetails(
            plot = info.firstOf("plot", "description"),
            genre = info.firstOf("genre"),
            cast = info.firstOf("cast"),
            releaseDate = info.firstOf("releaseDate", "release_date", "releasedate"),
            backdrop = info.firstImage("backdrop_path") ?: info.firstOf("cover"),
            seasons = parseEpisodes(root["episodes"])
                .groupBy { it.first }
                .toSortedMap()
                .map { (number, episodes) ->
                    Season(
                        number = number,
                        name = seasonNames[number]?.ifBlank { null } ?: "Season $number",
                        episodes = episodes.map { it.second }.sortedBy { it.number },
                    )
                },
        )
    }

    fun movieUrl(source: Source, item: LibraryItem): String =
        "${base(source)}/movie/${credentials(source)}/${item.id}.${item.extension ?: "mp4"}"

    fun episodeUrl(source: Source, episode: Episode): String =
        "${base(source)}/series/${credentials(source)}/${episode.id}.${episode.extension}"

    // ---- parsing -----------------------------------------------------------------------

    internal fun parseItem(obj: JsonObject, kind: LibraryKind): LibraryItem? {
        val id = obj.str(if (kind == LibraryKind.MOVIES) "stream_id" else "series_id").ifEmpty { return null }
        val name = obj.str("name").ifEmpty { obj.str("title") }
        return LibraryItem(
            id = id,
            kind = kind,
            name = name,
            poster = obj.firstOf("stream_icon", "cover"),
            rating = obj.firstOf("rating")?.takeUnless { it == "0" || it == "0.0" },
            year = obj.firstOf("year")?.take(4) ?: YEAR.find(name)?.groupValues?.get(1)
                ?: obj.firstOf("releaseDate", "release_date")?.take(4),
            categoryId = obj.str("category_id"),
            extension = obj.firstOf("container_extension"),
            added = obj.str("added").toLongOrNull() ?: obj.str("last_modified").toLongOrNull() ?: 0L,
        )
    }

    /** (season, episode) pairs from either `{"1": [...], "2": [...]}` or `[[...], [...]]`. */
    internal fun parseEpisodes(element: JsonElement?): List<Pair<Int, Episode>> {
        val groups: List<Pair<Int?, JsonElement>> = when (element) {
            is JsonObject -> element.entries.map { (key, value) -> key.toIntOrNull() to value }
            is JsonArray -> element.mapIndexed { index, value -> (index + 1) to value }
            else -> emptyList()
        }
        return groups.flatMap { (seasonKey, list) ->
            (list as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { ep ->
                val id = ep.str("id").ifEmpty { return@mapNotNull null }
                val season = ep.str("season").toIntOrNull() ?: seasonKey ?: 1
                val info = ep.obj("info")
                season to Episode(
                    id = id,
                    number = ep.str("episode_num").toIntOrNull() ?: 0,
                    title = ep.str("title"),
                    extension = ep.firstOf("container_extension") ?: "mp4",
                    duration = info?.firstOf("duration")?.let(::tidyDuration),
                    plot = info?.firstOf("plot", "description"),
                    image = info?.firstOf("movie_image", "cover_big"),
                )
            }
        }
    }

    // ---- HTTP & JSON helpers ----------------------------------------------------------

    private fun base(source: Source) = source.url.trim().trimEnd('/')

    private fun credentials(source: Source) =
        "${source.username.encodeURLParameter()}/${source.password.encodeURLParameter()}"

    private fun apiUrl(source: Source, action: String) =
        "${base(source)}/player_api.php?username=${source.username.encodeURLParameter()}" +
            "&password=${source.password.encodeURLParameter()}&action=$action"

    private suspend fun fetchArray(url: String): List<JsonObject> {
        val element = json.parseToJsonElement(client.get(url).bodyAsText())
        if (element !is JsonArray) {
            val auth = (element as? JsonObject)?.obj("user_info")?.str("auth")
            throw IllegalStateException(if (auth == "0") "Xtream login rejected" else "Unexpected Xtream response")
        }
        return element.mapNotNull { it as? JsonObject }
    }

    private fun categoryNames(list: List<JsonObject>): Map<String, String> =
        list.associate { it.str("category_id") to it.str("category_name") }

    private suspend fun fetchObject(url: String): JsonObject =
        json.parseToJsonElement(client.get(url).bodyAsText()) as? JsonObject
            ?: throw IllegalStateException("Unexpected Xtream response")

    internal companion object {
        private val YEAR = Regex("""\((\d{4})\)""")

        /** A primitive as text; JSON null and non-primitives become "". */
        fun JsonObject.str(key: String): String =
            (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim().orEmpty()

        fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

        fun JsonObject.firstOf(vararg keys: String): String? = keys.firstNotNullOfOrNull { str(it).ifBlank { null } }

        /** backdrop_path is an array of URLs on most panels, a plain string on some. */
        fun JsonObject.firstImage(key: String): String? = when (val value = get(key)) {
            is JsonArray -> value.firstNotNullOfOrNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content?.ifBlank { null } }
            else -> str(key).ifBlank { null }
        }

        /** "01:52:31" → "1 h 52 min"; anything else is kept as is. */
        fun tidyDuration(raw: String): String {
            val parts = raw.split(':').mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return raw
            val (h, m) = parts
            return when {
                h > 0 -> "$h h $m min"
                m > 0 -> "$m min"
                else -> raw
            }
        }
    }
}
