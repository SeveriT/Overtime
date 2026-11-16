package fi.severi.overtime

import fi.severi.overtime.data.Repository
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.model.PictureAdjust
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
import fi.severi.overtime.model.favoriteGroupKey
import fi.severi.overtime.model.GroupSort
import fi.severi.overtime.model.Programme
import fi.severi.overtime.model.Source
import fi.severi.overtime.player.VideoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel as Signal
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(private val repo: Repository, val player: VideoPlayer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _settings = MutableStateFlow(repo.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Settings are written in the background, at most one write at a time; quick runs of changes
    // (dragging a slider) collapse into one write. shutdown() writes whatever is still pending.
    private val settingsDirty = Signal<Unit>(Signal.CONFLATED)
    private val settingsWriteLock = Any()

    private fun writeSettings() = synchronized(settingsWriteLock) { repo.saveSettings(_settings.value) }

    init {
        scope.launch {
            for (ignored in settingsDirty) {
                delay(SETTINGS_SAVE_DELAY_MS)
                writeSettings()
            }
        }
    }

    /** Live volume: the slider and player follow it at once; settings catch up when it settles. */
    val volume = MutableStateFlow(_settings.value.volume)
    private var volumeSaveJob: Job? = null

    init {
        // VLC's volume call can wait on its audio thread, so it runs off the UI thread; a StateFlow
        // always hands over the newest value, skipping steps that came in meanwhile.
        scope.launch { volume.collect { player.setVolume(it) } }
    }

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

    /** Address of what's playing (channel, movie or episode), for copying from the stream info panel. */
    val streamUrl = MutableStateFlow<String?>(null)

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

    /** Sidebar during a movie or episode: hidden when each one starts; live TV keeps its own setting. */
    private val vodSidebar = MutableStateFlow(false)

    /** A narrow window hides the sidebar on its own; S can still show it until the window widens again. */
    private val narrowWindow = MutableStateFlow(false)
    private val narrowSidebar = MutableStateFlow(false)

    fun setNarrowWindow(narrow: Boolean) {
        if (narrow && !narrowWindow.value) narrowSidebar.value = false
        narrowWindow.value = narrow
    }

    /** Fullscreen or a narrow window decide on their own; otherwise null. */
    private val sidebarOverride = combine(fullscreen, fullscreenSidebar, narrowWindow, narrowSidebar) { full, overlay, narrow, narrowShown ->
        when {
            full -> overlay
            narrow -> narrowShown
            else -> null
        }
    }

    val sidebarVisible: StateFlow<Boolean> = combine(sidebarOverride, _settings, currentVod, vodSidebar) { override, settings, vod, vodShown ->
        override ?: if (vod != null) vodShown else settings.sidebarVisible
    }.stateIn(scope, SharingStarted.Eagerly, _settings.value.sidebarVisible)

    fun toggleSidebar() {
        when {
            fullscreen.value -> fullscreenSidebar.update { !it }
            narrowWindow.value -> narrowSidebar.update { !it }
            currentVod.value != null -> vodSidebar.update { !it }
            else -> updateSettings { it.copy(sidebarVisible = !it.sidebarVisible) }
        }
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

    /**
     * All channels and Favorites first, then the source's own groups in the chosen order, starred groups
     * ahead of the rest (each keeping that order), each with its channel count.
     */
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
        val sourceId = channels.firstOrNull()?.sourceId
        val (starred, rest) = sorted.partition { sourceId != null && favoriteGroupKey(sourceId, it.name) in settings.favoriteGroups }
        val favorites = channels.count { it.id in settings.favorites }
        return listOf(GroupEntry(GROUP_ALL, channels.size), GroupEntry(GROUP_FAVORITES, favorites)) +
            starred.map { it.copy(favorite = true) } + rest
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

    /** Live picture adjustments; saved to settings when a slider is released. */
    val picture = MutableStateFlow(_settings.value.picture)

    fun adjustPicture(adjust: PictureAdjust) {
        picture.value = adjust
        player.setPicture(adjust)
    }

    fun savePicture() = updateSettings { it.copy(picture = picture.value) }

    fun resetPicture() {
        adjustPicture(PictureAdjust())
        savePicture()
    }

    init {
        player.setVolume(_settings.value.volume)
        player.setPicture(picture.value)
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
            // After the cached guide is in, so it can't overwrite the fresh one. Cached channels stay usable
            // (and the last channel keeps playing) while this runs.
            if (_settings.value.refreshOnLaunch) refreshAll(quiet = true)
        }
        // Resuming the last channel happens in the init block at the end of the class, once every
        // property play() touches has been initialised.
    }

    fun nowNext(channel: Channel, now: Long = clock.value): Pair<Programme?, Programme?> =
        _epg.value[channel.sourceId]?.nowNext(channel, now) ?: (null to null)

    fun isFavorite(channel: Channel) = channel.id in _settings.value.favorites

    // ---- playback -------------------------------------------------------------

    // ---- movies & series ----------------------------------------------------------

    /** What the on-demand title is, for the Now playing sidebar (details load in the background if needed). */
    data class VodContext(
        val sourceId: String,
        val item: LibraryItem,
        val movie: MovieDetails? = null,
        val series: SeriesDetails? = null,
        val episodeId: String? = null,
        val season: Int? = null,
    )

    val vodContext = MutableStateFlow<VodContext?>(null)

    /** Fills in details the context lacks (e.g. after resuming from Continue watching). */
    private fun loadVodDetails(context: VodContext) {
        if (context.movie != null || context.series != null) return
        val source = _sources.value.firstOrNull { it.id == context.sourceId } ?: return
        scope.launch {
            val updated = runCatching {
                if (context.item.kind == LibraryKind.MOVIES) context.copy(movie = repo.movieDetails(source, context.item))
                else context.copy(series = repo.seriesDetails(source, context.item))
            }.getOrNull() ?: return@launch
            if (vodContext.value?.item?.id == context.item.id) vodContext.value = updated
        }
    }

    /** Opens Movies & series. A live channel stops; a movie or episode keeps playing (shown as a mini player). */
    /** Stop for a movie or episode: back to the library to pick something else. */
    fun stopToLibrary() {
        stopPlayback()
        library.open()
    }

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
        streamUrl.value = null
        vodContext.value = null
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
        vodContext.value = VodContext(source.id, item, movie = details).also(::loadVodDetails)
    }

    fun playEpisode(series: LibraryItem, details: SeriesDetails, season: Season, episode: Episode) {
        val source = library.source() ?: return
        val entry = episodeEntry(source.id, series, details, season, episode)
        val next = nextEpisodeAfter(details, season, episode)?.let { (s, e) -> episodeEntry(source.id, series, details, s, e) }
        startVod(repo.episodeUrl(source, episode), episodePlayback(entry), entry, next)
        vodContext.value = VodContext(source.id, series, series = details, episodeId = episode.id, season = season.number)
    }

    /** Plays a Continue watching card: resumes where it stopped, or starts a queued next episode. */
    fun resume(entry: WatchProgress) {
        val source = _sources.value.firstOrNull { it.id == entry.sourceId } ?: return
        if (entry.episodeId == null) {
            val item = LibraryItem(
                entry.itemId, LibraryKind.MOVIES, entry.title, entry.poster, null, null, "", entry.extension,
                tmdbId = entry.tmdbId, imdbId = entry.imdbId, backdrop = entry.backdrop,
            )
            startVod(repo.movieUrl(source, item), VodPlayback(entry.title, null, entry.poster), entry.copy(nextUp = false), next = null)
            vodContext.value = VodContext(source.id, item).also(::loadVodDetails)
            return
        }
        val episode = Episode(entry.episodeId, entry.episode ?: 0, entry.episodeTitle.orEmpty(), entry.extension, null, null, null)
        startVod(repo.episodeUrl(source, episode), episodePlayback(entry), entry.copy(nextUp = false), next = null)
        val series = LibraryItem(
            entry.itemId, LibraryKind.SERIES, entry.title, entry.poster, null, null, "", null,
            tmdbId = entry.tmdbId, imdbId = entry.imdbId, backdrop = entry.backdrop,
        )
        vodContext.value = VodContext(source.id, series, episodeId = entry.episodeId, season = entry.season)
        // Load the episode list for the sidebar, and find what comes after this one so finishing it queues the next.
        scope.launch {
            val details = runCatching { repo.seriesDetails(source, series) }.getOrNull() ?: return@launch
            vodContext.value?.takeIf { it.episodeId == entry.episodeId }?.let { vodContext.value = it.copy(series = details) }
            val season = details.seasons.firstOrNull { it.number == entry.season } ?: return@launch
            val current = season.episodes.firstOrNull { it.id == entry.episodeId } ?: return@launch
            val next = nextEpisodeAfter(details, season, current) ?: return@launch
            if (playingEntry?.key == entry.key) {
                nextEpisode = episodeEntry(source.id, series.copy(backdrop = entry.backdrop), details, next.first, next.second)
                upNext.value = nextEpisode
            }
        }
    }

    /** Removes a card from Continue watching (a series disappears until watched again). */
    fun forget(entry: WatchProgress) = history.dismiss(entry.key)

    /** A finished series card opens the series, to pick what to watch next. */
    fun openSeries(entry: WatchProgress) {
        library.select(
            LibraryItem(
                entry.itemId, LibraryKind.SERIES, entry.title, entry.poster, null, null, "", null,
                tmdbId = entry.tmdbId, imdbId = entry.imdbId, backdrop = entry.backdrop,
            ),
        )
    }

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

    /** The episode after the one playing, for the Next episode button and autoplay; null for movies and finales. */
    val upNext = MutableStateFlow<WatchProgress?>(null)

    /** Up next was dismissed for this episode: no card, and no autoplay when it ends. */
    private val upNextDismissed = MutableStateFlow(false)

    /**
     * Seconds left in the episode while it's in its last [AppSettings.upNextLead] (about when the end
     * credits start): the Up next card shows meanwhile. Null otherwise. The streams carry no credit
     * markers, so it goes by the time left.
     */
    val upNextCountdown: StateFlow<Int?> = combine(player.state, upNext, upNextDismissed, _settings) { state, next, dismissed, settings ->
        val media = state.media
        val leadMs = settings.upNextLead.seconds * 1_000L
        when {
            next == null || dismissed || media == null || state.ended -> null
            // Too short for a credits window to make sense (e.g. a trailer-length file).
            media.durationMs < leadMs * 3 -> null
            media.durationMs - media.positionMs > leadMs -> null
            else -> ((media.durationMs - media.positionMs + 999) / 1_000).toInt()
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    /** Plays the episode after the current one, from where it was left off if it was started before. */
    fun playNextEpisode() {
        val next = upNext.value ?: return
        val context = vodContext.value
        val details = context?.series
        val season = details?.seasons?.firstOrNull { it.number == next.season }
        val episode = season?.episodes?.firstOrNull { it.id == next.episodeId }
        // With the episode list already loaded, play it directly; otherwise as a Continue watching entry.
        if (context != null && details != null && season != null && episode != null && library.source()?.id == context.sourceId) {
            playEpisode(context.item, details, season, episode)
        } else {
            resume(next)
        }
    }

    /** Hides Up next for this episode and lets it play out without starting the next one. */
    fun dismissUpNext() {
        upNextDismissed.value = true
    }

    init {
        // Seeking back out of the credits brings Up next back.
        scope.launch {
            combine(player.state, _settings) { state, settings ->
                val media = state.media ?: return@combine false
                media.durationMs - media.positionMs > settings.upNextLead.seconds * 1_000L + UP_NEXT_REARM_MS
            }.distinctUntilChanged().collect { beforeCredits -> if (beforeCredits) upNextDismissed.value = false }
        }
        // An episode that plays to the end goes straight on to the next one (Up next counted down already).
        scope.launch {
            player.state.map { it.ended }.distinctUntilChanged().collect { ended ->
                if (ended && upNext.value != null && !upNextDismissed.value && _settings.value.autoplayNext) playNextEpisode()
            }
        }
    }

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
        vodSidebar.value = false
        val s = _settings.value
        val resumeAt = if (fromStart) 0L else history.resumeAt(entry.key) ?: 0L
        player.play(url, s.networkCachingMs, s.userAgent, rewindBufferMs = 0, startMs = resumeAt, onDemand = true)
        streamUrl.value = url
        awaitingStart = true
        playingEntry = entry.copy(dismissed = false)
        nextEpisode = next
        upNextDismissed.value = false
        upNext.value = next
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
        upNext.value = null
    }

    fun play(channel: Channel) {
        saveProgressNow()
        currentVod.value = null
        vodContext.value = null
        currentChannel.value = channel
        // A channel picked from a search outside the current group makes its own group the one Left/Right steps through.
        if (channel.sourceId == selectedSourceId.value && channelsIn(selectedGroup.value).none { it.id == channel.id }) {
            selectedGroup.value = channel.group
        }
        val s = _settings.value
        val rewindMs = if (s.rewindEnabled) s.rewindMinutes * 60_000L else 0L
        player.play(channel.url, s.networkCachingMs, s.userAgent, rewindMs)
        streamUrl.value = channel.url
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

    /** Hide channels the guide has no programmes for (off by default: show every channel). */
    val guideOnlyWithData = MutableStateFlow(false)

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
        if (clamped == this.volume.value) return
        this.volume.value = clamped
        volumeSaveJob?.cancel()
        volumeSaveJob = scope.launch {
            delay(VOLUME_SETTLE_MS)
            updateSettings { it.copy(volume = clamped) }
        }
    }

    fun adjustVolume(delta: Int) = setVolume(volume.value + delta)

    fun toggleFavoriteGroup(group: String) {
        val sourceId = selectedSourceId.value ?: return
        val key = favoriteGroupKey(sourceId, group)
        updateSettings { it.copy(favoriteGroups = if (key in it.favoriteGroups) it.favoriteGroups - key else it.favoriteGroups + key) }
    }

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

    /**
     * Reloads a source's channels and guide. [quiet] (the refresh at launch) shows "Updating…" and, when
     * there are cached channels to fall back on, keeps a failure out of the sidebar.
     */
    fun refreshSource(id: String, quiet: Boolean = false) {
        val source = _sources.value.firstOrNull { it.id == id } ?: return
        scope.launch {
            error.value = null
            busy.value = if (quiet) "Updating ${source.name}…" else "Loading channels for ${source.name}…"
            try {
                val channels = repo.fetchChannels(source)
                _channels.update { it + (source.id to channels) }
                val guides = repo.epgUrlsFor(source).size
                if (guides > 0) {
                    busy.value = when {
                        quiet -> "Updating guide for ${source.name}…"
                        guides == 1 -> "Loading guide for ${source.name}…"
                        else -> "Loading $guides guides for ${source.name}…"
                    }
                    val epg = repo.fetchEpg(source)
                    _epg.update { it + (source.id to epg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!quiet || _channels.value[source.id].isNullOrEmpty()) {
                    error.value = "${source.name}: ${e.message ?: e::class.simpleName}"
                }
            } finally {
                busy.value = null
            }
        }
    }

    fun refreshAll(quiet: Boolean = false) = _sources.value.forEach { refreshSource(it.id, quiet) }

    /** How many guides an M3U playlist's own header names (shown in the sources list). */
    fun playlistGuideCount(sourceId: String): Int = repo.playlistGuides(sourceId).size

    // ---- settings -------------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = _settings.value
        val after = transform(before)
        if (after == before) return
        _settings.value = after
        settingsDirty.trySend(Unit)
        if (after.subtitles != before.subtitles) scope.launch { player.setSubtitleStyle(after.subtitles) }
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
        // Anything still waiting to be written, including a volume change that hadn't settled.
        _settings.value = _settings.value.copy(volume = volume.value)
        writeSettings()
    }

    // Keep this the LAST initialiser in the class: play() uses state declared all through the class,
    // and Kotlin initialises properties and init blocks top to bottom.
    init {
        _settings.value.lastChannelId?.let { lastId ->
            _channels.value.values.asSequence().flatten().firstOrNull { it.id == lastId }?.let(::play)
        }
    }

    private companion object {
        /** How often the playing title's position is written to disk. */
        const val PROGRESS_SAVE_MS = 10_000L

        /** How far before the Up next window a seek must land to show the card again after Cancel. */
        const val UP_NEXT_REARM_MS = 5_000L

        /** Settings changes are collected this long before writing. */
        const val SETTINGS_SAVE_DELAY_MS = 300L

        /** The volume is stored in settings once the slider has been still this long. */
        const val VOLUME_SETTLE_MS = 400L
    }
}
