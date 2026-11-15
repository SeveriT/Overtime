package fi.severi.overtime

import fi.severi.overtime.model.WatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saved positions in movies and episodes. Feeds resume-on-play and the Continue watching row, where
 * finished episodes are replaced by the next one ("Next up").
 */
class WatchHistory(initial: List<WatchProgress>, private val save: (List<WatchProgress>) -> Unit) {
    private val _entries = MutableStateFlow(initial)
    val entries: StateFlow<List<WatchProgress>> = _entries.asStateFlow()

    operator fun get(key: String): WatchProgress? = _entries.value.firstOrNull { it.key == key }

    /** Records [entry], replacing any older one with the same key, and saves when [persist] is set. */
    fun record(entry: WatchProgress, persist: Boolean = true) {
        _entries.value = (listOf(entry) + _entries.value.filterNot { it.key == entry.key }).take(MAX_ENTRIES)
        if (persist) save(_entries.value)
    }

    fun remove(key: String) {
        _entries.value = _entries.value.filterNot { it.key == key }
        save(_entries.value)
    }

    /** Position to resume [key] from, or null to start at the beginning. */
    fun resumeAt(key: String): Long? =
        get(key)?.takeIf { !it.nextUp && !it.finished && it.fraction >= WatchProgress.STARTED_AT }?.positionMs

    companion object {
        private const val MAX_ENTRIES = 300

        /**
         * What Continue watching shows, newest first: movies started but not finished, and for each series
         * only its most recent entry — shown when that episode is in progress or queued as next up, hidden
         * when it was finished with nothing queued after it.
         */
        fun continueWatching(entries: List<WatchProgress>, limit: Int = 20): List<WatchProgress> {
            val seriesSeen = HashSet<String>()
            val result = ArrayList<WatchProgress>()
            for (entry in entries.sortedByDescending { it.updatedAt }) {
                if (entry.episodeId != null && !seriesSeen.add("${entry.sourceId}:${entry.itemId}")) continue
                if (entry.nextUp || (!entry.finished && entry.fraction >= WatchProgress.STARTED_AT)) result += entry
                if (result.size == limit) break
            }
            return result
        }
    }
}
