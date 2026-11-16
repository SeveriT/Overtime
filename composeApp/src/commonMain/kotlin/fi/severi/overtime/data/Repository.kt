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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.http.encodeURLPathPart

/** Loads, caches and persists sources, channel lists and EPG data. */
class Repository(private val store: FileStore) {
    // coerceInputValues: a value this version doesn't know (e.g. a removed option) falls back to its default
    // instead of failing the whole settings file.
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; coerceInputValues = true }
    private var client: HttpClient = createHttpClient(loadSettings().userAgent)
    private var xtream = XtreamApi(client, json)

    val dataDirectory: String get() = store.directory

    fun setUserAgent(userAgent: String) {
        client.close()
        client = createHttpClient(userAgent)
        xtream = XtreamApi(client, json)
    }

    // ---- artwork for local files ----------------------------------------------

    private val artCache: MutableMap<String, LocalArt?> by lazy {
        runCatching {
            store.readText(LOCAL_ART)?.let { json.decodeFromString<Map<String, LocalArt?>>(it) }
        }.getOrNull()?.toMutableMap() ?: mutableMapOf()
    }
    private val artLock = Any()
    private val leadingNumber = Regex("""^\s*\d{1,3}[\s._-]+""")

    /** Misses already asked with the current search; saved with the cache so they aren't asked again. */
    private val artMissesChecked: MutableSet<String> by lazy {
        runCatching { store.readText(LOCAL_ART_MISSES)?.lines()?.filter { it.isNotBlank() }?.toMutableSet() }.getOrNull() ?: mutableSetOf()
    }
    private val editionWords = Regex(
        """(?i)\b(extended( cut| edition)?|director'?s cut|theatrical( cut)?|uncut|unrated|remastered|superfan( episodes)?|special edition|complete( series)?)\b""",
    )

    /**
     * Poster, background and IMDb id for a local title, looked up by name (and year) in Stremio's Cinemeta
     * catalogue, which needs no account. Cached on disk, misses included, so each title is asked once.
     */
    suspend fun localArtwork(kind: LibraryKind, title: String, year: String?): LocalArt? = withContext(Dispatchers.Default) {
        val key = "${kind.name}|${title.lowercase()}|${year.orEmpty()}"
        // A miss cached by an older, weaker search is asked again once.
        synchronized(artLock) {
            if (key in artCache && (artCache[key] != null || key in artMissesChecked)) return@withContext artCache[key]
        }
        val type = if (kind == LibraryKind.MOVIES) "movie" else "series"
        suspend fun search(text: String) = json.parseToJsonElement(
            client.get("https://v3-cinemeta.strem.io/catalog/$type/top/search=${text.encodeURLPathPart()}.json").bodyAsText(),
        ).jsonObject["metas"]?.jsonArray.orEmpty().map { it.jsonObject }
        val found = runCatching {
            // Fallbacks when the full name finds nothing: without edition words ("The Office Extended Cut"),
            // and without a collection number in front ("5 Vares - Sukkanauhakäärme").
            val plain = editionWords.replace(title, " ").replace(Regex("""\s+"""), " ").trim()
            val candidates = listOf(title, plain, leadingNumber.replace(title, ""), leadingNumber.replace(plain, ""))
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            var metas = emptyList<kotlinx.serialization.json.JsonObject>()
            for (candidate in candidates) {
                metas = search(candidate)
                if (metas.isNotEmpty()) break
            }
            // Same year first (releaseInfo is "2010" or "2005-2013"), otherwise the best match.
            val pick = metas.firstOrNull { meta -> year != null && meta["releaseInfo"]?.jsonPrimitive?.contentOrNull?.startsWith(year) == true }
                ?: metas.firstOrNull()
            pick?.let { meta ->
                fun field(name: String) = meta[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                LocalArt(
                    imdbId = field("imdb_id") ?: field("id")?.takeIf { it.startsWith("tt") },
                    // Cinemeta lists small images; the large size keeps posters sharp.
                    poster = field("poster")?.replace("/poster/small/", "/poster/large/")?.replace("/poster/medium/", "/poster/large/"),
                    backdrop = field("background"),
                )
            }
        }.getOrElse { return@withContext null } // offline or a hiccup: try again next scan, don't cache
        synchronized(artLock) {
            artCache[key] = found
            if (found == null) artMissesChecked += key
            runCatching { store.writeText(LOCAL_ART, json.encodeToString(artCache.toMap())) }
            if (found == null) runCatching { store.writeText(LOCAL_ART_MISSES, artMissesChecked.joinToString("\n")) }
        }
        found
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
        // An error page or a provider hiccup parses as an empty list; don't let it wipe the saved channels.
        if (channels.isEmpty() && cachedChannels(source.id).isNotEmpty()) error("the provider returned no channels")
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
                // Keep showing the last good copy of a guide that failed to download.
                runCatching { store.readBytes(epgFile(source.id, index))?.let(::parseXmltv) }.getOrNull()?.let { merged += it }
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
        const val LOCAL_ART = "local_art.json"

        /** Bumped name when the search improves, so older misses are retried once. */
        const val LOCAL_ART_MISSES = "local_art_misses_v2.txt"
        const val SOURCES = "sources.json"
        const val PROGRESS = "progress.json"
        const val MAX_GUIDES = 8
    }
}
