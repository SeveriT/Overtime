package fi.severi.overtime.data

import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.EpgData
import fi.severi.overtime.model.Source
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
            SourceType.M3U_URL -> M3uParser.parse(client.get(source.url.trim()).bodyAsText(), source.id)
            SourceType.M3U_FILE -> M3uParser.parse(readLocalFile(source.url.trim()), source.id)
            SourceType.XTREAM -> xtream.liveChannels(source)
        }
        store.writeText(channelsFile(source.id), json.encodeToString(ListSerializer(Channel.serializer()), channels))
        channels
    }

    // ---- EPG ----------------------------------------------------------------

    fun epgUrlFor(source: Source): String? = when {
        source.epgUrl.isNotBlank() -> source.epgUrl.trim()
        source.type == SourceType.XTREAM -> xtream.epgUrl(source)
        else -> null
    }

    suspend fun cachedEpg(sourceId: String): EpgData = withContext(Dispatchers.Default) {
        runCatching { store.readBytes(epgFile(sourceId))?.let(::parseXmltv) }.getOrNull() ?: EpgData()
    }

    suspend fun fetchEpg(source: Source): EpgData = withContext(Dispatchers.Default) {
        val url = epgUrlFor(source) ?: return@withContext EpgData()
        val bytes = client.get(url).bodyAsBytes()
        val epg = parseXmltv(bytes)
        if (!epg.isEmpty) store.writeBytes(epgFile(source.id), bytes)
        epg
    }

    fun deleteSourceData(sourceId: String) {
        store.delete(channelsFile(sourceId))
        store.delete(epgFile(sourceId))
    }

    private fun channelsFile(sourceId: String) = "channels_$sourceId.json"
    private fun epgFile(sourceId: String) = "epg_$sourceId.xml"

    private companion object {
        const val SETTINGS = "settings.json"
        const val SOURCES = "sources.json"
    }
}
