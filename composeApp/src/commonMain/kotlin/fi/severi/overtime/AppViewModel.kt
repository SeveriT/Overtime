package fi.severi.overtime

import fi.severi.overtime.data.Repository
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.EpgData
import fi.severi.overtime.model.GROUP_ALL
import fi.severi.overtime.model.GROUP_FAVORITES
import fi.severi.overtime.model.GroupSort
import fi.severi.overtime.model.Programme
import fi.severi.overtime.model.Source
import fi.severi.overtime.player.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(private val repo: Repository, val player: VideoPlayer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _settings = MutableStateFlow(repo.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _sources = MutableStateFlow(repo.loadSources())
    val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    private val _channels = MutableStateFlow(_sources.value.associate { it.id to repo.cachedChannels(it.id) })
    private val _epg = MutableStateFlow<Map<String, EpgData>>(emptyMap())

    val selectedSourceId = MutableStateFlow(_settings.value.lastSourceId ?: _sources.value.firstOrNull()?.id)
    val selectedGroup = MutableStateFlow(GROUP_ALL)
    val query = MutableStateFlow("")
    val currentChannel = MutableStateFlow<Channel?>(null)

    /** Status text while something loads, null when idle. */
    val busy = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)

    /** Wall clock refreshed every 30 s so EPG progress bars move. */
    val clock = MutableStateFlow(nowMillis())

    val fullscreen = MutableStateFlow(false)

    /** Sidebar shown over the video while fullscreen; not persisted, resets each time fullscreen is entered. */
    private val fullscreenSidebar = MutableStateFlow(false)

    val sidebarVisible: StateFlow<Boolean> = combine(fullscreen, fullscreenSidebar, _settings) { full, overlay, settings ->
        if (full) overlay else settings.sidebarVisible
    }.stateIn(scope, SharingStarted.Eagerly, _settings.value.sidebarVisible)

    fun toggleSidebar() {
        if (fullscreen.value) fullscreenSidebar.update { !it }
        else updateSettings { it.copy(sidebarVisible = !it.sidebarVisible) }
    }

    fun setFullscreen(enabled: Boolean) {
        if (enabled) fullscreenSidebar.value = false
        fullscreen.value = enabled
    }

    fun toggleFullscreen() = setFullscreen(!fullscreen.value)

    val groups: StateFlow<List<String>> = combine(_channels, selectedSourceId, _settings) { channels, sourceId, settings ->
        val own = channels[sourceId].orEmpty()
        val sorted = when (settings.groupSort) {
            GroupSort.ALPHABETICAL -> own.asSequence().map { it.group }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).toList()
            GroupSort.PLAYLIST -> own.asSequence().map { it.group }.distinct().toList()
            GroupSort.SIZE -> own.groupingBy { it.group }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.key })
                .map { it.key }
        }
        listOf(GROUP_ALL, GROUP_FAVORITES) + sorted
    }.stateIn(scope, SharingStarted.Eagerly, listOf(GROUP_ALL, GROUP_FAVORITES))

    fun setGroupSort(sort: GroupSort) = updateSettings { it.copy(groupSort = sort) }

    fun cycleGroupSort() {
        val all = GroupSort.entries
        setGroupSort(all[(all.indexOf(_settings.value.groupSort) + 1) % all.size])
    }

    val visibleChannels: StateFlow<List<Channel>> =
        combine(_channels, selectedSourceId, selectedGroup, query, _settings) { channels, sourceId, group, query, settings ->
            val base = channels[sourceId].orEmpty()
            val grouped = when (group) {
                GROUP_ALL -> base
                GROUP_FAVORITES -> base.filter { it.id in settings.favorites }
                else -> base.filter { it.group == group }
            }
            val q = query.trim()
            if (q.isEmpty()) grouped else grouped.filter { it.name.contains(q, ignoreCase = true) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val epgForSelectedSource: StateFlow<EpgData> = combine(_epg, selectedSourceId) { epg, id -> epg[id] ?: EpgData() }
        .stateIn(scope, SharingStarted.Eagerly, EpgData())

    init {
        player.setVolume(_settings.value.volume)
        scope.launch {
            while (true) {
                delay(30_000)
                clock.value = nowMillis()
            }
        }
        scope.launch {
            _sources.value.forEach { source ->
                val cached = repo.cachedEpg(source.id)
                if (!cached.isEmpty) _epg.update { it + (source.id to cached) }
            }
        }
        _settings.value.lastChannelId?.let { lastId ->
            _channels.value.values.asSequence().flatten().firstOrNull { it.id == lastId }?.let(::play)
        }
    }

    fun nowNext(channel: Channel, now: Long = clock.value): Pair<Programme?, Programme?> =
        _epg.value[channel.sourceId]?.nowNext(channel, now) ?: (null to null)

    fun isFavorite(channel: Channel) = channel.id in _settings.value.favorites

    // ---- playback -------------------------------------------------------------

    fun play(channel: Channel) {
        currentChannel.value = channel
        val s = _settings.value
        player.play(channel.url, s.networkCachingMs, s.userAgent)
        updateSettings { it.copy(lastChannelId = channel.id, lastSourceId = channel.sourceId) }
    }

    /** Moves to the previous (-1) or next (+1) channel in the visible list. */
    fun step(delta: Int) {
        val list = visibleChannels.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == currentChannel.value?.id }
        val target = if (index < 0) 0 else ((index + delta) % list.size + list.size) % list.size
        play(list[target])
    }

    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        player.setVolume(clamped)
        updateSettings { it.copy(volume = clamped) }
    }

    fun adjustVolume(delta: Int) = setVolume(_settings.value.volume + delta)

    fun toggleFavorite(channel: Channel) = updateSettings {
        it.copy(favorites = if (channel.id in it.favorites) it.favorites - channel.id else it.favorites + channel.id)
    }

    // ---- sources --------------------------------------------------------------

    fun selectSource(id: String) {
        selectedSourceId.value = id
        selectedGroup.value = GROUP_ALL
        updateSettings { it.copy(lastSourceId = id) }
    }

    fun addSource(source: Source) {
        _sources.update { it + source }
        repo.saveSources(_sources.value)
        selectSource(source.id)
        refreshSource(source.id)
    }

    fun removeSource(id: String) {
        _sources.update { list -> list.filterNot { it.id == id } }
        repo.saveSources(_sources.value)
        repo.deleteSourceData(id)
        _channels.update { it - id }
        _epg.update { it - id }
        if (currentChannel.value?.sourceId == id) {
            player.stop()
            currentChannel.value = null
        }
        if (selectedSourceId.value == id) selectedSourceId.value = _sources.value.firstOrNull()?.id
    }

    fun refreshSource(id: String) {
        val source = _sources.value.firstOrNull { it.id == id } ?: return
        scope.launch {
            error.value = null
            busy.value = "Loading channels for ${source.name}…"
            try {
                val channels = repo.fetchChannels(source)
                _channels.update { it + (source.id to channels) }
                if (repo.epgUrlFor(source) != null) {
                    busy.value = "Loading guide for ${source.name}…"
                    val epg = repo.fetchEpg(source)
                    _epg.update { it + (source.id to epg) }
                }
            } catch (e: Exception) {
                error.value = "${source.name}: ${e.message ?: e::class.simpleName}"
            } finally {
                busy.value = null
            }
        }
    }

    fun refreshAll() = _sources.value.forEach { refreshSource(it.id) }

    // ---- settings -------------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = _settings.value
        val after = transform(before)
        if (after == before) return
        _settings.value = after
        repo.saveSettings(after)
        if (after.userAgent != before.userAgent) repo.setUserAgent(after.userAgent)
    }

    val dataDirectory: String get() = repo.dataDirectory

    fun shutdown() {
        player.release()
        scope.cancel()
    }
}
