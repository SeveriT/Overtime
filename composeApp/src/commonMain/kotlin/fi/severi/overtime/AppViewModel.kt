package fi.severi.overtime

import fi.severi.overtime.data.Repository
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.EpgData
import fi.severi.overtime.model.Episode
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.MovieDetails
import fi.severi.overtime.model.Season
import fi.severi.overtime.model.SeriesDetails
import fi.severi.overtime.model.WatchProgress
import fi.severi.overtime.model.SourceType
import fi.severi.overtime.model.VodPlayback
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
import kotlinx.coroutines.flow.map
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

    /** The movie or episode playing, when it's on-demand rather than a channel. */
    val currentVod = MutableStateFlow<VodPlayback?>(null)

    /** Movies & series of Xtream sources. */
    val library = LibraryModel(repo, scope, sources)

    val hasLibrary: StateFlow<Boolean> = sources.map { list -> list.any { it.type == SourceType.XTREAM } }
        .stateIn(scope, SharingStarted.Eagerly, sources.value.any { it.type == SourceType.XTREAM })

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

    // ---- movies & series ----------------------------------------------------------

    /** Opens Movies & series. A live channel stops; a movie or episode keeps playing (shown as a mini player). */
    fun openLibrary() {
        closeGuide()
        if (currentChannel.value != null) stopPlayback()
        library.open()
    }

    /** Steps to the next audio track (B, like VLC). */
    fun cycleAudioTrack() = cycleTrack(subtitles = false)

    /** Steps through subtitles, including off (V, like VLC). */
    fun cycleSubtitles() = cycleTrack(subtitles = true)

    private fun cycleTrack(subtitles: Boolean) {
        val info = player.tracks() ?: return
        val list = if (subtitles) info.subtitles else info.audio.filter { it.id >= 0 }
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.id == if (subtitles) info.subtitleSelected else info.audioSelected }
        val next = list[(current + 1) % list.size]
        if (subtitles) player.selectSubtitleTrack(next.id) else player.selectAudioTrack(next.id)
    }

    /** Stops whatever is playing; the next launch won't auto-resume a stopped channel. */
    fun stopPlayback() {
        saveProgressNow()
        player.stop()
        currentChannel.value = null
        currentVod.value = null
        updateSettings { it.copy(lastChannelId = null) }
    }

    fun playMovie(item: LibraryItem, details: MovieDetails? = null, fromStart: Boolean = false) {
        val source = library.source() ?: return
        val entry = WatchProgress(
            key = "m:${source.id}:${item.id}",
            sourceId = source.id,
            kind = LibraryKind.MOVIES,
            itemId = item.id,
            title = item.name,
            extension = item.extension ?: "mp4",
            positionMs = 0,
            durationMs = 0,
            updatedAt = nowMillis(),
            poster = item.poster,
            backdrop = details?.backdrop ?: item.backdrop,
            tmdbId = item.tmdbId ?: details?.tmdbId,
            imdbId = item.imdbId ?: details?.imdbId,
        )
        startVod(repo.movieUrl(source, item), VodPlayback(item.name, item.year, item.poster), entry, next = null, fromStart = fromStart)
    }

    fun playEpisode(series: LibraryItem, details: SeriesDetails, season: Season, episode: Episode) {
        val source = library.source() ?: return
        val entry = episodeEntry(source.id, series, details, season, episode)
        val next = nextEpisodeAfter(details, season, episode)?.let { (s, e) -> episodeEntry(source.id, series, details, s, e) }
        startVod(repo.episodeUrl(source, episode), episodePlayback(entry), entry, next)
    }

    /** Plays a Continue watching card: resumes where it stopped, or starts a queued next episode. */
    fun resume(entry: WatchProgress) {
        val source = _sources.value.firstOrNull { it.id == entry.sourceId } ?: return
        if (entry.episodeId == null) {
            val item = LibraryItem(entry.itemId, LibraryKind.MOVIES, entry.title, entry.poster, null, null, "", entry.extension)
            startVod(repo.movieUrl(source, item), VodPlayback(entry.title, null, entry.poster), entry.copy(nextUp = false), next = null)
            return
        }
        val episode = Episode(entry.episodeId, entry.episode ?: 0, entry.episodeTitle.orEmpty(), entry.extension, null, null, null)
        startVod(repo.episodeUrl(source, episode), episodePlayback(entry), entry.copy(nextUp = false), next = null)
        // Find what comes after it in the background, so finishing this one queues the next.
        val series = LibraryItem(entry.itemId, LibraryKind.SERIES, entry.title, entry.poster, null, null, "", null)
        scope.launch {
            val details = runCatching { repo.seriesDetails(source, series) }.getOrNull() ?: return@launch
            val season = details.seasons.firstOrNull { it.number == entry.season } ?: return@launch
            val current = season.episodes.firstOrNull { it.id == entry.episodeId } ?: return@launch
            val next = nextEpisodeAfter(details, season, current) ?: return@launch
            if (playingEntry?.key == entry.key) nextEpisode = episodeEntry(source.id, series.copy(backdrop = entry.backdrop), details, next.first, next.second)
        }
    }

    fun forget(entry: WatchProgress) = history.remove(entry.key)

    private fun episodeEntry(sourceId: String, series: LibraryItem, details: SeriesDetails?, season: Season, episode: Episode) =
        WatchProgress(
            key = "e:$sourceId:${episode.id}",
            sourceId = sourceId,
            kind = LibraryKind.SERIES,
            itemId = series.id,
            title = series.name,
            extension = episode.extension,
            positionMs = 0,
            durationMs = 0,
            updatedAt = nowMillis(),
            episodeId = episode.id,
            season = season.number,
            episode = episode.number,
            episodeTitle = episode.title.ifBlank { null },
            poster = series.poster,
            backdrop = episode.image ?: details?.backdrop ?: series.backdrop,
            tmdbId = series.tmdbId ?: details?.tmdbId,
            imdbId = series.imdbId ?: details?.imdbId,
        )

    private fun episodePlayback(entry: WatchProgress) = VodPlayback(
        entry.title,
        "S${entry.season} E${entry.episode}" + entry.episodeTitle?.let { " · $it" }.orEmpty(),
        entry.poster,
    )

    private fun nextEpisodeAfter(details: SeriesDetails, season: Season, episode: Episode): Pair<Season, Episode>? {
        val ordered = details.seasons.flatMap { s -> s.episodes.map { s to it } }
        val index = ordered.indexOfFirst { it.second.id == episode.id }
        return ordered.getOrNull(index + 1)
    }

    // ---- watch progress -------------------------------------------------------------

    val history = WatchHistory(repo.loadProgress(), repo::saveProgress)

    val continueWatching: StateFlow<List<WatchProgress>> = history.entries
        .map { WatchHistory.continueWatching(it) }
        .stateIn(scope, SharingStarted.Eagerly, WatchHistory.continueWatching(history.entries.value))

    /** The movie or episode playing; its position is filled in as it plays. */
    @kotlin.concurrent.Volatile private var playingEntry: WatchProgress? = null

    /** For series: the episode after the one playing, queued as Next up when it finishes. */
    @kotlin.concurrent.Volatile private var nextEpisode: WatchProgress? = null

    /** Ignore player progress until the new file has started (the old one's state may still be around). */
    @kotlin.concurrent.Volatile private var awaitingStart = false
    private var lastSavedAt = 0L

    init {
        trackProgress()
    }

    /** On-demand files are seekable already, so they play directly without the rewind recorder. */
    private fun startVod(url: String, playback: VodPlayback, entry: WatchProgress, next: WatchProgress?, fromStart: Boolean = false) {
        saveProgressNow()
        currentChannel.value = null
        currentVod.value = playback
        val s = _settings.value
        val resumeAt = if (fromStart) 0L else history.resumeAt(entry.key) ?: 0L
        player.play(url, s.networkCachingMs, s.userAgent, rewindBufferMs = 0, startMs = resumeAt)
        awaitingStart = true
        playingEntry = entry
        nextEpisode = next
        library.close()
    }

    private fun trackProgress() = scope.launch {
        while (true) {
            delay(1_000)
            val entry = playingEntry ?: continue
            val state = player.state.value
            val media = state.media ?: continue
            if (awaitingStart) {
                if (!state.playing) continue
                awaitingStart = false
            }
            val updated = entry.copy(
                positionMs = if (state.ended) media.durationMs else media.positionMs,
                durationMs = media.durationMs,
                updatedAt = nowMillis(),
                nextUp = false,
            )
            playingEntry = updated
            if (state.ended || updated.updatedAt - lastSavedAt >= PROGRESS_SAVE_MS) commit(updated)
        }
    }

    private fun commit(entry: WatchProgress) {
        lastSavedAt = nowMillis()
        // A peek of a few seconds isn't worth a Continue watching card.
        if (entry.fraction < WatchProgress.STARTED_AT && history[entry.key] == null) return
        history.record(entry)
        if (entry.finished) {
            nextEpisode?.let { next ->
                val started = history[next.key]?.let { it.fraction >= WatchProgress.STARTED_AT } == true
                if (!started) history.record(next.copy(updatedAt = nowMillis() + 1, nextUp = true))
            }
            nextEpisode = null
        }
    }

    /** Saves the playing title's position right away (before switching to something else, or on exit). */
    private fun saveProgressNow() {
        if (!awaitingStart) playingEntry?.let(::commit)
        playingEntry = null
        nextEpisode = null
    }

    fun play(channel: Channel) {
        saveProgressNow()
        currentVod.value = null
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
                val guides = repo.epgUrlsFor(source).size
                if (guides > 0) {
                    busy.value = if (guides == 1) "Loading guide for ${source.name}…" else "Loading $guides guides for ${source.name}…"
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

    /** How many guides an M3U playlist's own header names (shown in the sources list). */
    fun playlistGuideCount(sourceId: String): Int = repo.playlistGuides(sourceId).size

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
        saveProgressNow()
        player.release()
        scope.cancel()
    }

    private companion object {
        /** How often the playing title's position is written to disk. */
        const val PROGRESS_SAVE_MS = 10_000L
    }
}
