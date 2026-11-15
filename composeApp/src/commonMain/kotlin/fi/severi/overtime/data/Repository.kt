package fi.severi.overtime.data

import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.EpgData
import fi.severi.overtime.model.Episode
import fi.severi.overtime.model.LibraryCategory
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.MovieDetails
import fi.severi.overtime.model.SeriesDetails
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.WatchProgress
import fi.severi.overtime.model.SourceType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Loads, caches and persists sources, channel lists and EPG data. */
class Repository(private val store: FileStore) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private var client: HttpClient = createHttpClient(loadSettings().userAgent)
    private var xtream = XtreamApi(client, json)

    val dataDirectory: String get() = store.directory

    fun setUserAgent(userAgent: String) {
        client.close()
        client = createHttpClient(userAgent)
        xtream = XtreamApi(client, json)
    }

    // ---- settings & sources -------------------------------------------------

    fun loadSettings(): AppSettings =
        runCatching { store.readText(SETTINGS)?.let { json.decodeFromString<AppSettings>(it) } }.getOrNull() ?: AppSettings()

    fun saveSettings(settings: AppSettings) = store.writeText(SETTINGS, json.encodeToString(settings))

    fun loadSources(): List<Source> =
        runCatching { store.readText(SOURCES)?.let { json.decodeFromString(ListSerializer(Source.serializer()), it) } }
            .getOrNull() ?: emptyList()

    fun saveSources(sources: List<Source>) =
        store.writeText(SOURCES, json.encodeToString(ListSerializer(Source.serializer()), sources))

    // ---- channels -----------------------------------------------------------

    fun cachedChannels(sourceId: String): List<Channel> =
        runCatching { store.readText(channelsFile(sourceId))?.let { json.decodeFromString(ListSerializer(Channel.serializer()), it) } }
            .getOrNull() ?: emptyList()

    suspend fun fetchChannels(source: Source): List<Channel> = withContext(Dispatchers.Default) {
        val channels = when (source.type) {
            SourceType.M3U_URL -> rememberGuides(source, M3uParser.parsePlaylist(client.get(source.url.trim()).bodyAsText(), source.id))
            SourceType.M3U_FILE -> rememberGuides(source, M3uParser.parsePlaylist(readLocalFile(source.url.trim()), source.id))
            SourceType.XTREAM -> xtream.liveChannels(source)
        }
        store.writeText(channelsFile(source.id), json.encodeToString(ListSerializer(Channel.serializer()), channels))
        channels
    }

    private fun rememberGuides(source: Source, playlist: M3uPlaylist): List<Channel> {
        store.writeText(guidesFile(source.id), playlist.guideUrls.joinToString("\n"))
        return playlist.channels
    }

    // ---- watch progress -----------------------------------------------------

    fun loadProgress(): List<WatchProgress> =
        runCatching { store.readText(PROGRESS)?.let { json.decodeFromString(ListSerializer(WatchProgress.serializer()), it) } }
            .getOrNull() ?: emptyList()

    fun saveProgress(entries: List<WatchProgress>) =
        store.writeText(PROGRESS, json.encodeToString(ListSerializer(WatchProgress.serializer()), entries))

    // ---- movies & series (Xtream) -------------------------------------------

    suspend fun libraryCategories(source: Source, kind: LibraryKind): List<LibraryCategory> =
        withContext(Dispatchers.Default) { xtream.categories(source, kind) }

    suspend fun libraryItems(source: Source, kind: LibraryKind, categoryId: String?): List<LibraryItem> =
        withContext(Dispatchers.Default) { xtream.items(source, kind, categoryId) }

    suspend fun movieDetails(source: Source, item: LibraryItem): MovieDetails =
        withContext(Dispatchers.Default) { xtream.movieDetails(source, item) }

    suspend fun seriesDetails(source: Source, item: LibraryItem): SeriesDetails =
        withContext(Dispatchers.Default) { xtream.seriesDetails(source, item) }

    fun movieUrl(source: Source, item: LibraryItem): String = xtream.movieUrl(source, item)

    fun episodeUrl(source: Source, episode: Episode): String = xtream.episodeUrl(source, episode)

    // ---- EPG ----------------------------------------------------------------

    /** Guide URLs named in the playlist's header, from the last refresh. */
    fun playlistGuides(sourceId: String): List<String> =
        store.readText(guidesFile(sourceId))?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * Where the guide comes from: the user's URL(s) if set (comma-separated for several), else the Xtream
     * provider's xmltv.php, else whatever the M3U header names.
     */
    fun epgUrlsFor(source: Source): List<String> = when {
        source.epgUrl.isNotBlank() -> source.epgUrl.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        source.type == SourceType.XTREAM -> listOf(xtream.epgUrl(source))
        else -> playlistGuides(source.id)
    }

    suspend fun cachedEpg(sourceId: String): EpgData = withContext(Dispatchers.Default) {
        // epg_<id>.xml is the single-guide file from older versions.
        (listOf(legacyEpgFile(sourceId)) + (0 until MAX_GUIDES).map { epgFile(sourceId, it) })
            .mapNotNull { name -> runCatching { store.readBytes(name)?.let(::parseXmltv) }.getOrNull() }
            .fold(EpgData()) { all, next -> all + next }
    }

    /** Downloads every guide for the source and merges them. One failing guide doesn't sink the others. */
    suspend fun fetchEpg(source: Source): EpgData = withContext(Dispatchers.Default) {
        val urls = epgUrlsFor(source).take(MAX_GUIDES)
        if (urls.isEmpty()) return@withContext EpgData()
        var merged = EpgData()
        var firstError: Exception? = null
        urls.forEachIndexed { index, url ->
            try {
                val bytes = client.get(url).bodyAsBytes()
                val epg = parseXmltv(bytes)
                if (!epg.isEmpty) {
                    store.writeBytes(epgFile(source.id, index), bytes)
                    merged += epg
                }
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        // Drop cached guides that are no longer listed.
        (urls.size until MAX_GUIDES).forEach { store.delete(epgFile(source.id, it)) }
        store.delete(legacyEpgFile(source.id))
        if (merged.isEmpty) firstError?.let { throw it }
        merged
    }

    fun deleteSourceData(sourceId: String) {
        store.delete(channelsFile(sourceId))
        store.delete(guidesFile(sourceId))
        store.delete(legacyEpgFile(sourceId))
        (0 until MAX_GUIDES).forEach { store.delete(epgFile(sourceId, it)) }
    }

    private fun channelsFile(sourceId: String) = "channels_$sourceId.json"
    private fun guidesFile(sourceId: String) = "guides_$sourceId.txt"
    private fun legacyEpgFile(sourceId: String) = "epg_$sourceId.xml"
    private fun epgFile(sourceId: String, index: Int) = "epg_${sourceId}_$index.xml"

    private companion object {
        const val SETTINGS = "settings.json"
        const val SOURCES = "sources.json"
        const val PROGRESS = "progress.json"
        const val MAX_GUIDES = 8
    }
}
