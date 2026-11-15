package fi.severi.overtime

import fi.severi.overtime.data.Repository
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.EpgData
import fi.severi.overtime.model.GROUP_ALL
import fi.severi.overtime.model.GROUP_FAVORITES
import fi.severi.overtime.model.GroupEntry
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

    /** Group whose channels the sidebar shows and Left/Right steps through. */
    val selectedGroup = MutableStateFlow(GROUP_ALL)

    /** True while the sidebar lists groups; false once a group is opened. The app starts on the group list. */
    val showGroupList = MutableStateFlow(true)

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

    /** All channels of the selected source, in playlist order. */
    val sourceChannels: StateFlow<List<Channel>> = combine(_channels, selectedSourceId) { channels, sourceId ->
        channels[sourceId].orEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, _channels.value[selectedSourceId.value].orEmpty())

    /** All channels and Favorites first, then the source's own groups in the chosen order, each with its channel count. */
    val groups: StateFlow<List<GroupEntry>> = combine(sourceChannels, _settings) { channels, settings ->
        buildGroups(channels, settings)
    }.stateIn(scope, SharingStarted.Eagerly, buildGroups(sourceChannels.value, _settings.value))

    private fun buildGroups(channels: List<Channel>, settings: AppSettings): List<GroupEntry> {
        val counts = LinkedHashMap<String, Int>()
        channels.forEach { counts[it.group] = (counts[it.group] ?: 0) + 1 }
        val own = counts.entries.map { GroupEntry(it.key, it.value) }
        val sorted = when (settings.groupSort) {
            GroupSort.PLAYLIST -> own
            GroupSort.ALPHABETICAL -> own.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            GroupSort.SIZE -> own.sortedWith(compareByDescending<GroupEntry> { it.count }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        val favorites = channels.count { it.id in settings.favorites }
        return listOf(GroupEntry(GROUP_ALL, channels.size), GroupEntry(GROUP_FAVORITES, favorites)) + sorted
    }

    fun openGroup(name: String) {
        selectedGroup.value = name
        query.value = ""
        showGroupList.value = false
    }

    fun backToGroups() {
        query.value = ""
        showGroupList.value = true
    }

    fun setGroupSort(sort: GroupSort) = updateSettings { it.copy(groupSort = sort) }

    fun cycleGroupSort() {
        val all = GroupSort.entries
        setGroupSort(all[(all.indexOf(_settings.value.groupSort) + 1) % all.size])
    }

    fun channelsIn(
        group: String,
        all: List<Channel> = sourceChannels.value,
        favorites: Set<String> = _settings.value.favorites,
    ): List<Channel> = when (group) {
        GROUP_ALL -> all
        GROUP_FAVORITES -> all.filter { it.id in favorites }
        else -> all.filter { it.group == group }
    }

    /**
     * Channels the sidebar should list. On the group list only a search shows channels (across every group);
     * inside a group the search narrows that group.
     */
    fun visibleChannels(
        all: List<Channel>,
        group: String,
        groupListShown: Boolean,
        query: String,
        favorites: Set<String>,
    ): List<Channel> {
        val q = query.trim()
        val base = when {
            groupListShown && q.isEmpty() -> return emptyList()
            groupListShown -> all
            else -> channelsIn(group, all, favorites)
        }
        return if (q.isEmpty()) base else base.filter { it.name.contains(q, ignoreCase = true) }
    }

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
        // A channel picked from a search outside the current group makes its own group the one Left/Right steps through.
        if (channel.sourceId == selectedSourceId.value && channelsIn(selectedGroup.value).none { it.id == channel.id }) {
            selectedGroup.value = channel.group
        }
        val s = _settings.value
        val rewindMs = if (s.rewindEnabled) s.rewindMinutes * 60_000L else 0L
        player.play(channel.url, s.networkCachingMs, s.userAgent, rewindMs)
        updateSettings { it.copy(lastChannelId = channel.id, lastSourceId = channel.sourceId) }
    }

    // ---- live rewind ------------------------------------------------------------

    fun seekBy(deltaMs: Long) = player.seekBy(deltaMs)

    fun seekTo(wallMs: Long) = player.seekTo(wallMs)

    fun goLive() = player.goLive()

    // ---- programme guide ---------------------------------------------------------

    val guideVisible = MutableStateFlow(false)

    /** Group whose channels the guide lists; starts as the sidebar's group each time it opens. */
    val guideGroup = MutableStateFlow(GROUP_ALL)

    /** Hide channels the guide has no programmes for. */
    val guideOnlyWithData = MutableStateFlow(true)

    /** Wall-clock time at the guide's left edge. */
    val guideStart = MutableStateFlow(0L)

    fun openGuide() {
        guideGroup.value = selectedGroup.value
        guideStart.value = guideHome()
        guideVisible.value = true
    }

    fun closeGuide() {
        guideVisible.value = false
    }

    fun toggleGuide() = if (guideVisible.value) closeGuide() else openGuide()

    fun shiftGuide(deltaMs: Long) = guideStart.update { it + deltaMs }

    /** Back to "now": half an hour of the past in view so the current programmes show in full. */
    fun guideToNow() {
        guideStart.value = guideHome()
    }

    private fun guideHome(): Long {
        val halfHour = 30 * 60_000L
        return (nowMillis() - halfHour) / halfHour * halfHour
    }

    fun programmesFor(channel: Channel): List<Programme> = _epg.value[channel.sourceId]?.programmesFor(channel).orEmpty()

    /** Stream info panel (resolution, fps, codecs…), toggled with the toolbar button or I. */
    val statsVisible = MutableStateFlow(false)

    fun toggleStats() = statsVisible.update { !it }

    /** Moves to the previous (-1) or next (+1) channel within the selected group. */
    fun step(delta: Int) {
        val list = channelsIn(selectedGroup.value)
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
        backToGroups()
        updateSettings { it.copy(lastSourceId = id) }
    }

    fun addSource(source: Source) {
        _sources.update { it + source }
        repo.saveSources(_sources.value)
        selectSource(source.id)
        refreshSource(source.id)
    }

    /** Saves changes to an existing source (same id, so favourites stay) and reloads its channels and guide. */
    fun updateSource(source: Source) {
        _sources.update { list -> list.map { if (it.id == source.id) source else it } }
        repo.saveSources(_sources.value)
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
        // Recording starts with playback, so turning rewind on or off restarts the current channel.
        if (after.rewindEnabled != before.rewindEnabled || after.rewindMinutes != before.rewindMinutes) {
            currentChannel.value?.let(::play)
        }
    }

    val dataDirectory: String get() = repo.dataDirectory

    fun shutdown() {
        player.release()
        scope.cancel()
    }
}
