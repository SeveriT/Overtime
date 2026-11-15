package fi.severi.overtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.model.Episode
import fi.severi.overtime.model.LibraryItem
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.MovieDetails
import fi.severi.overtime.model.SeriesDetails
import fi.severi.overtime.model.SourceType

private val CategoryColumnWidth = 280.dp
private val DetailsWidth = 380.dp

/**
 * Movies & series of an Xtream source: categories as drawer items, posters in an adaptive grid, and a
 * side sheet with details and Play (seasons and episodes for series).
 */
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val library = vm.library
    val kind by library.kind.collectAsState()
    val categories by library.categories.collectAsState()
    val categoryId by library.categoryId.collectAsState()
    val items by library.items.collectAsState()
    val query by library.query.collectAsState()
    val results by library.searchResults.collectAsState()
    val loading by library.loading.collectAsState()
    val error by library.error.collectAsState()
    val selected by library.selected.collectAsState()
    val searching = query.isNotBlank()
    val shown = if (searching) results else items

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
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.width(CategoryColumnWidth).fillMaxHeight(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(categories, key = { it.id }) { category ->
                            NavigationDrawerItem(
                                label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = !searching && category.id == categoryId,
                                onClick = { library.openCategory(category.id) },
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            shown.isNotEmpty() -> PosterGrid(shown, selected, onSelect = library::select, resetKey = "$kind/$categoryId/$query")
                            loading == null && searching -> LibraryEmpty("Nothing matches “$query”.")
                            loading == null && categories.isEmpty() && error == null -> LibraryEmpty("This provider has no ${kind.label.lowercase()}.")
                            loading == null && error == null -> LibraryEmpty("This category is empty.")
                        }
                    }
                }
            }
            LibraryDetails(vm, selected)
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

@Composable
private fun PosterGrid(items: List<LibraryItem>, selected: LibraryItem?, onSelect: (LibraryItem) -> Unit, resetKey: String) {
    val state = rememberLazyGridState()
    LaunchedEffect(resetKey) { state.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        state = state,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { "${it.kind}:${it.id}" }) { item ->
            PosterCard(item, selected = item.id == selected?.id && item.kind == selected.kind, onClick = { onSelect(item) })
        }
    }
}

@Composable
private fun PosterCard(item: LibraryItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium).background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Movie, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                if (item.poster != null) {
                    AsyncImage(model = item.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.year?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

// ---- details side sheet ----------------------------------------------------------------

@Composable
private fun LibraryDetails(vm: AppViewModel, item: LibraryItem?) {
    val movie by vm.library.movieDetails.collectAsState()
    val series by vm.library.seriesDetails.collectAsState()
    val detailsLoading by vm.library.detailsLoading.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = Modifier.width(DetailsWidth).fillMaxHeight(),
    ) {
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Pick a title to see its details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Surface
        }
        var seasonIndex by remember(item.id) { mutableIntStateOf(0) }
        val seasons = series?.seasons.orEmpty()
        val season = seasons.getOrNull(seasonIndex)

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                DetailsHeader(item, backdrop = movie?.backdrop ?: series?.backdrop, duration = movie?.duration)
            }
            if (item.kind == LibraryKind.MOVIES) {
                item {
                    Button(onClick = { vm.playMovie(item) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Play")
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
                        EpisodeRow(episode, onPlay = { vm.playEpisode(item, season, episode) })
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

@Composable
private fun DetailsHeader(item: LibraryItem, backdrop: String?, duration: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val image = backdrop ?: item.poster
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(item.name, style = MaterialTheme.typography.headlineSmallEmphasized)
        val meta = listOfNotNull(item.year, duration, item.rating?.let { "★ ${it.take(3)}" })
        if (meta.isNotEmpty()) {
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
private fun EpisodeRow(episode: Episode, onPlay: () -> Unit) {
    Surface(onClick = onPlay, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.width(96.dp).height(54.dp).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (episode.image != null) {
                    AsyncImage(model = episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "E${episode.number}" + episode.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.duration?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play episode", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
