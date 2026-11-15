package fi.severi.overtime

import fi.severi.overtime.data.Repository
import fi.severi.overtime.model.LibraryCategory
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.MovieDetails
import fi.severi.overtime.model.SeriesDetails
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.SourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * State for the Movies & Series library of Xtream sources.
 *
 * The home page shows one row per category; each row loads when it first scrolls into view (a few at a
 * time). "See all" opens a category as a grid. Everything fetched is kept in memory for the session.
 */
class LibraryModel(
    private val repo: Repository,
    private val scope: CoroutineScope,
    private val sources: StateFlow<List<Source>>,
) {
    val visible = MutableStateFlow(false)
    val kind = MutableStateFlow(LibraryKind.MOVIES)
    val sourceId = MutableStateFlow<String?>(null)
    val categories = MutableStateFlow<List<LibraryCategory>>(emptyList())

    /** Items per category id, filled as rows come into view. */
    val rows = MutableStateFlow<Map<String, List<LibraryItem>>>(emptyMap())

    /** Category opened with See all; null shows the home rows. */
    val categoryId = MutableStateFlow<String?>(null)

    /** Search text; searching covers the whole library, not just one category. */
    val query = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<LibraryItem>>(emptyList())

    /** Status text while loading, null when idle. */
    val loading = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)

    val selected = MutableStateFlow<LibraryItem?>(null)
    val movieDetails = MutableStateFlow<MovieDetails?>(null)
    val seriesDetails = MutableStateFlow<SeriesDetails?>(null)
    val detailsLoading = MutableStateFlow(false)

    private val categoryCache = HashMap<Pair<String, LibraryKind>, List<LibraryCategory>>()
    private val itemCache = HashMap<Triple<String, LibraryKind, String?>, List<LibraryItem>>()
    private val rowJobs = HashMap<Triple<String, LibraryKind, String>, Job>()
    private val rowLimit = Semaphore(MAX_PARALLEL_ROWS)
    private var categoriesJob: Job? = null
    private var searchJob: Job? = null
    private var detailsJob: Job? = null

    val xtreamSources: List<Source> get() = sources.value.filter { it.type == SourceType.XTREAM }

    fun source(): Source? = sources.value.firstOrNull { it.id == sourceId.value }

    fun open() {
        if (xtreamSources.none { it.id == sourceId.value }) {
            sourceId.value = xtreamSources.firstOrNull()?.id
            resetLists()
        }
        visible.value = true
        if (categories.value.isEmpty()) loadCategories()
    }

    fun close() {
        visible.value = false
    }

    fun setKind(newKind: LibraryKind) {
        if (kind.value == newKind) return
        kind.value = newKind
        resetLists()
        loadCategories()
    }

    fun setSource(id: String) {
        if (sourceId.value == id) return
        sourceId.value = id
        resetLists()
        loadCategories()
    }

    /** Loads a home row's items unless they're already here or on the way. */
    fun ensureRow(categoryId: String) {
        val source = source() ?: return
        val key = Triple(source.id, kind.value, categoryId)
        itemCache[key]?.let { list ->
            if (rows.value[categoryId] == null) rows.update { it + (categoryId to list) }
            return
        }
        if (rowJobs[key]?.isActive == true) return
        rowJobs[key] = scope.launch {
            rowLimit.withPermit {
                try {
                    val list = repo.libraryItems(source, key.second, categoryId)
                    itemCache[key] = list
                    if (sourceId.value == source.id && kind.value == key.second) rows.update { it + (categoryId to list) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message ?: e::class.simpleName
                }
            }
        }
    }

    fun openCategory(id: String) {
        categoryId.value = id
        query.value = ""
        ensureRow(id)
    }

    fun closeCategory() {
        categoryId.value = null
    }

    fun search(text: String) {
        query.value = text
        val q = text.trim()
        if (q.isEmpty()) {
            searchResults.value = emptyList()
            return
        }
        val source = source() ?: return
        val key = Triple(source.id, kind.value, null)
        val all = itemCache[key]
        if (all != null) {
            searchResults.value = filter(all, q)
            return
        }
        if (searchJob?.isActive == true) return // the whole library is already on its way
        searchJob = scope.launch {
            load("Loading the whole ${kind.value.label.lowercase()} library for search…") {
                val list = repo.libraryItems(source, kind.value, null)
                itemCache[key] = list
                searchResults.value = filter(list, query.value.trim())
            }
        }
    }

    fun select(item: LibraryItem) {
        selected.value = item
        movieDetails.value = null
        seriesDetails.value = null
        val source = source() ?: return
        detailsJob?.cancel()
        detailsJob = scope.launch {
            detailsLoading.value = true
            try {
                if (item.kind == LibraryKind.MOVIES) {
                    val details = repo.movieDetails(source, item)
                    movieDetails.value = details
                    adoptIds(item, details.tmdbId, details.imdbId, details.backdrop)
                } else {
                    val details = repo.seriesDetails(source, item)
                    seriesDetails.value = details
                    adoptIds(item, details.tmdbId, details.imdbId, details.backdrop)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Couldn't load details: ${e.message ?: e::class.simpleName}"
            } finally {
                detailsLoading.value = false
            }
        }
    }

    fun deselect() {
        detailsJob?.cancel()
        selected.value = null
        movieDetails.value = null
        seriesDetails.value = null
    }

    /** Details often carry the TMDB/IMDb IDs the list lacked; use them for the selected title's artwork. */
    private fun adoptIds(item: LibraryItem, tmdbId: String?, imdbId: String?, backdrop: String?) {
        if (selected.value?.id != item.id) return
        selected.value = item.copy(
            tmdbId = item.tmdbId ?: tmdbId,
            imdbId = item.imdbId ?: imdbId,
            backdrop = item.backdrop ?: backdrop,
        )
    }

    private fun loadCategories() {
        val source = source() ?: return
        val key = source.id to kind.value
        categoryCache[key]?.let {
            categories.value = it
            return
        }
        categoriesJob?.cancel()
        categoriesJob = scope.launch {
            load("Loading ${kind.value.label.lowercase()} categories…") {
                val list = repo.libraryCategories(source, kind.value)
                categoryCache[key] = list
                categories.value = list
            }
        }
    }

    private fun resetLists() {
        categories.value = emptyList()
        rows.value = emptyMap()
        categoryId.value = null
        query.value = ""
        searchResults.value = emptyList()
        deselect()
    }

    private suspend fun load(status: String, block: suspend () -> Unit) {
        error.value = null
        loading.value = status
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message ?: e::class.simpleName
        } finally {
            if (loading.value == status) loading.value = null
        }
    }

    private fun filter(list: List<LibraryItem>, q: String) =
        list.filter { it.name.contains(q, ignoreCase = true) }.take(MAX_RESULTS)

    private companion object {
        const val MAX_RESULTS = 500
        const val MAX_PARALLEL_ROWS = 3
    }
}
