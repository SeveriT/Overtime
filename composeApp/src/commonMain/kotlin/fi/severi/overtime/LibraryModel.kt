package fi.severi.overtime

import fi.severi.overtime.data.Repository
import fi.severi.overtime.model.LibraryCategory
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.MovieDetails
import fi.severi.overtime.model.SeriesDetails
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * State for the Movies & Series library of Xtream sources. Categories and items are fetched on demand
 * and kept in memory for the session, so browsing back and forth is instant after the first load.
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
    val categoryId = MutableStateFlow<String?>(null)
    val items = MutableStateFlow<List<LibraryItem>>(emptyList())

    /** Search text; searching covers the whole library, not just the open category. */
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
    private var listJob: Job? = null
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

    fun openCategory(id: String) {
        categoryId.value = id
        query.value = ""
        val source = source() ?: return
        val key = Triple(source.id, kind.value, id)
        itemCache[key]?.let {
            items.value = it
            return
        }
        items.value = emptyList()
        listJob?.cancel()
        listJob = scope.launch {
            load("Loading ${categories.value.firstOrNull { it.id == id }?.name ?: "category"}…") {
                val list = repo.libraryItems(source, kind.value, id)
                itemCache[key] = list
                if (categoryId.value == id) items.value = list
            }
        }
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
                if (item.kind == LibraryKind.MOVIES) movieDetails.value = repo.movieDetails(source, item)
                else seriesDetails.value = repo.seriesDetails(source, item)
            } catch (e: Exception) {
                error.value = "Couldn't load details: ${e.message ?: e::class.simpleName}"
            } finally {
                detailsLoading.value = false
            }
        }
    }

    private fun loadCategories() {
        val source = source() ?: return
        val key = source.id to kind.value
        categoryCache[key]?.let {
            categories.value = it
            it.firstOrNull()?.let { first -> openCategory(first.id) }
            return
        }
        listJob?.cancel()
        listJob = scope.launch {
            load("Loading ${kind.value.label.lowercase()} categories…") {
                val list = repo.libraryCategories(source, kind.value)
                categoryCache[key] = list
                categories.value = list
            }
            categories.value.firstOrNull()?.let { openCategory(it.id) }
        }
    }

    private fun resetLists() {
        categories.value = emptyList()
        categoryId.value = null
        items.value = emptyList()
        query.value = ""
        searchResults.value = emptyList()
        selected.value = null
        movieDetails.value = null
        seriesDetails.value = null
    }

    private suspend fun load(status: String, block: suspend () -> Unit) {
        error.value = null
        loading.value = status
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
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
    }
}
