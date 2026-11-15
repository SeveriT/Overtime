package fi.severi.overtime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.PosterArt
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Episode
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.MovieDetails
import fi.severi.overtime.model.SeriesDetails
import fi.severi.overtime.model.SourceType
import fi.severi.overtime.model.WatchProgress

private val DetailsWidth = 400.dp
private const val ROW_ITEMS = 40
private const val NEW_FOR_SECONDS = 14L * 24 * 3600

/** Poster card look, from Settings → Poster cards. */
data class PosterStyle(
    val width: Dp,
    val corner: Dp,
    val landscape: Boolean,
    val hideLabels: Boolean,
    val rated: Boolean,
    val template: String,
) {
    /** Card width for the current orientation; landscape cards are wider for the same feel. */
    val cardWidth: Dp get() = if (landscape) width * 1.8f else width

    companion object {
        fun from(s: AppSettings) = PosterStyle(
            width = s.posterWidth.dp.dp,
            corner = s.posterCorners.dp.dp,
            landscape = s.landscapePosters,
            hideLabels = s.hidePosterLabels,
            rated = s.ratedPosters,
            template = s.posterTemplate,
        )
    }
}

/** Rated artwork when the title has an ID and it's enabled, else the provider's own image. */
private fun artFor(style: PosterStyle, kind: LibraryKind, tmdbId: String?, imdbId: String?, landscape: Boolean, fallback: String?): Pair<String?, Boolean> {
    val rated = if (style.rated) PosterArt.url(style.template, kind, tmdbId, imdbId, landscape) else null
    return if (rated != null) rated to true else fallback to false
}

/**
 * Movies & series: Continue watching and one row per category on the home page, See all as a grid,
 * search across the whole library, and a details sheet that slides in for the selected title.
 */
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val library = vm.library
    val settings by vm.settings.collectAsState()
    val style = PosterStyle.from(settings)
    val kind by library.kind.collectAsState()
    val categories by library.categories.collectAsState()
    val rows by library.rows.collectAsState()
    val categoryId by library.categoryId.collectAsState()
    val query by library.query.collectAsState()
    val results by library.searchResults.collectAsState()
    val loading by library.loading.collectAsState()
    val error by library.error.collectAsState()
    val selected by library.selected.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val history by vm.history.entries.collectAsState()
    val movieProgress = remember(history) {
        history.filter { it.episodeId == null }.associate { it.key to it }
    }
    val sourceId by library.sourceId.collectAsState()
    fun progressOf(item: LibraryItem): WatchProgress? =
        if (item.kind == LibraryKind.MOVIES) movieProgress["m:$sourceId:${item.id}"] else null

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                LibraryTopBar(vm, kind, query)
                if (loading != null || error != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (loading != null) LoadingIndicator(Modifier.size(28.dp))
                        Text(
                            error ?: loading.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        query.isNotBlank() -> when {
                            results.isNotEmpty() -> PosterGrid(results, style, selected, ::progressOf, library::select, "search/$kind/$query")
                            loading == null -> LibraryEmpty("Nothing matches “$query”.")
                        }
                        categoryId != null -> {
                            val id = categoryId!!
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.padding(start = 16.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = library::closeCategory, shapes = IconButtonDefaults.shapes()) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to all rows")
                                    }
                                    Text(
                                        categories.firstOrNull { it.id == id }?.name.orEmpty(),
                                        style = MaterialTheme.typography.titleLargeEmphasized,
                                    )
                                }
                                val list = rows[id]
                                when {
                                    list == null -> Unit
                                    list.isEmpty() -> LibraryEmpty("This category is empty.")
                                    else -> PosterGrid(list, style, selected, ::progressOf, library::select, "cat/$kind/$id")
                                }
                            }
                        }
                        categories.isEmpty() && loading == null && error == null ->
                            LibraryEmpty("This provider has no ${kind.label.lowercase()}.")
                        else -> LibraryHome(vm, style, categories.map { it.id to it.name }, rows, continueWatching, selected, ::progressOf)
                    }
                }
            }
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                // Keep showing the last title while the sheet slides out.
                val shown = remember { mutableStateOf<LibraryItem?>(null) }
                if (selected != null) shown.value = selected
                shown.value?.let { LibraryDetails(vm, it, style) }
            }
        }
    }
}

@Composable
private fun LibraryTopBar(vm: AppViewModel, kind: LibraryKind, query: String) {
    val library = vm.library
    val sourceId by library.sourceId.collectAsState()
    val sources by vm.sources.collectAsState()
    val xtream = sources.filter { it.type == SourceType.XTREAM }
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WithTooltip("Close (Esc)", above = false) {
            IconButton(onClick = library::close, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close library")
            }
        }
        Text("Movies & series", style = MaterialTheme.typography.titleLargeEmphasized)
        ConnectedChoiceGroup(
            options = LibraryKind.entries,
            selected = kind,
            label = { it.label },
            onSelect = library::setKind,
            modifier = Modifier.width(240.dp),
        )
        if (xtream.size > 1) {
            var open by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) {
                    Text(xtream.firstOrNull { it.id == sourceId }?.name ?: "Source", maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
                    xtream.forEach { source ->
                        DropdownMenuItem(text = { Text(source.name) }, onClick = { library.setSource(source.id); open = false })
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PillSearchField(
            value = query,
            onValueChange = library::search,
            placeholder = "Search all ${kind.label.lowercase()}",
            modifier = Modifier.width(360.dp),
        )
    }
}

@Composable
private fun LibraryEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- home rows ---------------------------------------------------------------------------

@Composable
private fun LibraryHome(
    vm: AppViewModel,
    style: PosterStyle,
    categories: List<Pair<String, String>>,
    rows: Map<String, List<LibraryItem>>,
    continueWatching: List<WatchProgress>,
    selected: LibraryItem?,
    progressOf: (LibraryItem) -> WatchProgress?,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                RowSection("Continue watching") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.key }) { entry ->
                            ContinueCard(entry, style, onPlay = { vm.resume(entry) }, onRemove = { vm.forget(entry) })
                        }
                    }
                }
            }
        }
        items(categories, key = { it.first }) { (id, name) ->
            LaunchedEffect(id) { vm.library.ensureRow(id) }
            val list = rows[id]
            if (list != null && list.isEmpty()) return@items // nothing worth a row
            RowSection(
                title = name,
                onSeeAll = if (list != null && list.size > 1) ({ vm.library.openCategory(id) }) else null,
            ) {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (list == null) {
                        // Placeholders while the row loads.
                        items(6) { PosterPlaceholder(style) }
                    } else {
                        items(list.take(ROW_ITEMS), key = { "${it.kind}:${it.id}" }) { item ->
                            PosterCard(item, style, selected = item.id == selected?.id, progress = progressOf(item)) {
                                vm.library.select(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowSection(title: String, onSeeAll: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll, shapes = ButtonDefaults.shapes()) {
                    Text("See all")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
            }
        }
        content()
    }
}

@Composable
private fun PosterGrid(
    items: List<LibraryItem>,
    style: PosterStyle,
    selected: LibraryItem?,
    progressOf: (LibraryItem) -> WatchProgress?,
    onSelect: (LibraryItem) -> Unit,
    resetKey: String,
) {
    val state = rememberLazyGridState()
    LaunchedEffect(resetKey) { state.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(style.cardWidth),
        state = state,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.size, key = { "${items[it].kind}:${items[it].id}" }) { index ->
            val item = items[index]
            PosterCard(item, style, selected = item.id == selected?.id, progress = progressOf(item), fillWidth = true) { onSelect(item) }
        }
    }
}

// ---- cards -------------------------------------------------------------------------------

@Composable
private fun PosterPlaceholder(style: PosterStyle) {
    Box(
        Modifier
            .width(style.cardWidth)
            .aspectRatio(if (style.landscape) 16f / 9f else 2f / 3f)
            .clip(RoundedCornerShape(style.corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun PosterCard(
    item: LibraryItem,
    style: PosterStyle,
    selected: Boolean,
    progress: WatchProgress?,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(style.corner)
    val (image, rated) = artFor(
        style, item.kind, item.tmdbId, item.imdbId, style.landscape,
        fallback = if (style.landscape) item.backdrop ?: item.poster else item.poster,
    )
    val isNew = item.added > 0 && nowMillis() / 1000 - item.added < NEW_FOR_SECONDS
    Column(
        (if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(style.cardWidth)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = scheme.surfaceContainerHigh,
            border = if (selected) BorderStroke(3.dp, scheme.primary) else null,
            modifier = Modifier.fillMaxWidth().aspectRatio(if (style.landscape) 16f / 9f else 2f / 3f),
        ) {
            Box {
                Icon(
                    Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
                if (image != null) {
                    AsyncImage(model = image, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                // Rated artwork already carries ratings and badges; add ours only on plain posters.
                if (!rated) {
                    Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isNew) Badge("New", scheme.primaryContainer, scheme.onPrimaryContainer)
                    }
                    item.rating?.let { rating ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = scheme.scrim.copy(alpha = 0.7f),
                            contentColor = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = FavoriteGold, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(rating.take(3), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                progress?.let { p ->
                    if (p.finished) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Watched",
                            tint = scheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(22.dp)
                                .background(scheme.scrim.copy(alpha = 0.6f), RoundedCornerShape(50)),
                        )
                    } else if (p.fraction >= WatchProgress.STARTED_AT) {
                        LinearProgressIndicator(
                            progress = { p.fraction },
                            color = MaterialTheme.colorScheme.error,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            drawStopIndicator = {},
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).height(4.dp),
                        )
                    }
                }
            }
        }
        if (!style.hideLabels) {
            Column(Modifier.padding(horizontal = 2.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.year?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Landscape card: artwork, "Next up" / "N% watched" badge, S·E label, title and a progress bar. */
@Composable
private fun ContinueCard(entry: WatchProgress, style: PosterStyle, onPlay: () -> Unit, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val (image, _) = artFor(style, entry.kind, entry.tmdbId, entry.imdbId, landscape = true, fallback = entry.backdrop ?: entry.poster)
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(style.corner.coerceAtLeast(8.dp)),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.width(style.width * 2.4f).aspectRatio(16f / 9f).hoverable(hover),
    ) {
        Box {
            if (image != null) {
                AsyncImage(model = image, contentDescription = entry.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
                ),
            )
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Badge(
                    if (entry.nextUp) "Next up" else "${(entry.fraction * 100).toInt()}% watched",
                    container = scheme.scrim.copy(alpha = 0.7f),
                    content = Color.White,
                )
            }
            if (hovered) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    WithTooltip("Remove from Continue watching") {
                        FilledTonalIconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (entry.episodeId != null) {
                    Text("S${entry.season} E${entry.episode}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                }
                Text(entry.title, style = MaterialTheme.typography.titleMediumEmphasized, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.episodeTitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!entry.nextUp) {
                LinearProgressIndicator(
                    progress = { entry.fraction },
                    color = scheme.error,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    drawStopIndicator = {},
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                )
            }
        }
    }
}

// ---- details side sheet ----------------------------------------------------------------

@Composable
private fun LibraryDetails(vm: AppViewModel, item: LibraryItem, style: PosterStyle) {
    val movie by vm.library.movieDetails.collectAsState()
    val series by vm.library.seriesDetails.collectAsState()
    val detailsLoading by vm.library.detailsLoading.collectAsState()
    val history by vm.history.entries.collectAsState()
    val sourceId by vm.library.sourceId.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = Modifier.width(DetailsWidth).fillMaxHeight(),
    ) {
        var seasonIndex by remember(item.id) { mutableIntStateOf(0) }
        val seasons = series?.seasons.orEmpty()
        val season = seasons.getOrNull(seasonIndex)
        val movieProgress = history.firstOrNull { it.key == "m:$sourceId:${item.id}" }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box {
                    val (hero, _) = artFor(style, item.kind, item.tmdbId, item.imdbId, landscape = true, fallback = movie?.backdrop ?: series?.backdrop ?: item.backdrop ?: item.poster)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        if (hero != null) {
                            AsyncImage(model = hero, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        WithTooltip("Close details") {
                            FilledTonalIconButton(onClick = vm.library::deselect, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Default.Close, contentDescription = "Close details")
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmallEmphasized)
                    val meta = listOfNotNull(item.year, movie?.duration, item.rating?.let { "★ ${it.take(3)}" })
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (item.kind == LibraryKind.MOVIES) {
                item {
                    val resumable = movieProgress != null && !movieProgress.finished && movieProgress.fraction >= WatchProgress.STARTED_AT
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.playMovie(item, movie) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(if (resumable) "Resume from ${formatPosition(movieProgress.positionMs)}" else "Play")
                        }
                        if (resumable) {
                            FilledTonalButton(onClick = { vm.playMovie(item, movie, fromStart = true) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Start over")
                            }
                        }
                    }
                }
            }
            if (detailsLoading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            }
            movie?.let { item { MovieInfo(it) } }
            series?.let { details ->
                item { SeriesInfo(details) }
                if (seasons.size > 1) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            seasons.forEachIndexed { index, s ->
                                FilterChip(selected = index == seasonIndex, onClick = { seasonIndex = index }, label = { Text(s.name) })
                            }
                        }
                    }
                }
                if (season != null) {
                    items(season.episodes, key = { it.id }) { episode ->
                        val progress = history.firstOrNull { it.key == "e:$sourceId:${episode.id}" }
                        EpisodeRow(episode, progress, onPlay = { vm.playEpisode(item, details, season, episode) })
                    }
                } else if (!detailsLoading) {
                    item {
                        Text("No episodes listed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun formatPosition(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun MovieInfo(details: MovieDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details.genre?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        details.plot?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Credit("Director", details.director)
        Credit("Cast", details.cast)
        Credit("Released", details.releaseDate)
    }
}

@Composable
private fun SeriesInfo(details: SeriesDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details.genre?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        details.plot?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis) }
        Credit("Cast", details.cast)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Credit(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EpisodeRow(episode: Episode, progress: WatchProgress?, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onPlay, shape = MaterialTheme.shapes.large, color = scheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.width(96.dp).height(54.dp).clip(MaterialTheme.shapes.small).background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = scheme.onSurfaceVariant)
                if (episode.image != null) {
                    AsyncImage(model = episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (progress != null && !progress.finished && !progress.nextUp && progress.fraction >= WatchProgress.STARTED_AT) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        color = scheme.error,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        drawStopIndicator = {},
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "E${episode.number}" + episode.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.duration?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
            }
            if (progress?.finished == true) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Watched", tint = scheme.primary)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play episode", tint = scheme.primary)
            }
        }
    }
}
