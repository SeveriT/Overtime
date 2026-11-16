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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.model.Episode
import fi.severi.overtime.model.LibraryKind
import fi.severi.overtime.model.WatchProgress

/**
 * Takes the channel list's place while a movie or episode plays: artwork, title and details, and for
 * series the season's episodes to jump between. Live TV returns to the channels.
 */
@Composable
fun VodSidebar(vm: AppViewModel, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context by vm.vodContext.collectAsState()
    val playback by vm.currentVod.collectAsState()
    val settings by vm.settings.collectAsState()
    val history by vm.history.entries.collectAsState()
    val style = PosterStyle.from(settings)
    val scheme = MaterialTheme.colorScheme

    Column(modifier.background(scheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Now playing", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
            WithTooltip("Movies & series (L)", above = false) {
                IconButton(onClick = vm::openLibrary, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = "Movies & series")
                }
            }
            WithTooltip("Settings", above = false) {
                IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
        FilledTonalButton(
            onClick = vm::stopPlayback,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Outlined.LiveTv, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Live TV")
        }

        val ctx = context
        val series = ctx?.series
        val seasons = series?.seasons.orEmpty()
        var seasonIndex by remember(ctx?.item?.id, seasons.size) {
            mutableIntStateOf(seasons.indexOfFirst { it.number == ctx?.season }.coerceAtLeast(0))
        }
        val season = seasons.getOrNull(seasonIndex)

        val listState = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val item = ctx?.item
                        val (hero, _) = if (item != null) {
                            artFor(style, item.kind, item.tmdbId ?: ctx.movie?.tmdbId ?: series?.tmdbId, item.imdbId ?: ctx.movie?.imdbId ?: series?.imdbId,
                                landscape = true, fallback = ctx.movie?.backdrop ?: series?.backdrop ?: item.backdrop ?: item.poster)
                        } else {
                            (playback?.poster) to false
                        }
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerHigh),
                        ) {
                            if (hero != null) Artwork(hero, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                        Text(playback?.title ?: item?.name.orEmpty(), style = MaterialTheme.typography.titleLargeEmphasized)
                        playback?.subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.primary) }
                        val meta = listOfNotNull(item?.year, ctx?.movie?.duration, item?.rating?.let { "★ ${it.take(3)}" })
                        if (meta.isNotEmpty()) {
                            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        (ctx?.movie?.genre ?: series?.genre)?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        }
                    }
                }

                when {
                    ctx == null -> Unit
                    ctx.item.kind == LibraryKind.MOVIES -> ctx.movie?.let { movie ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                movie.plot?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                movie.cast?.let {
                                    Text("Cast", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } ?: item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                    series == null -> item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                    else -> {
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
                                val progress = history.firstOrNull { it.key == "e:${ctx.sourceId}:${episode.id}" }
                                SidebarEpisode(
                                    episode = episode,
                                    playing = episode.id == ctx.episodeId,
                                    progress = progress,
                                    onPlay = { vm.playEpisode(ctx.item, series, season, episode) },
                                )
                            }
                        }
                    }
                }
            }
            ListScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

/** Drawer-style pill for an episode: the playing one uses the selected tone. */
@Composable
private fun SidebarEpisode(episode: Episode, playing: Boolean, progress: WatchProgress?, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onPlay,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (playing) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (playing) scheme.onSecondaryContainer else scheme.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                when {
                    playing -> Icons.Default.GraphicEq
                    progress?.finished == true -> Icons.Default.CheckCircle
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    playing -> "Playing"
                    progress?.finished == true -> "Watched"
                    else -> "Play"
                },
                tint = if (playing || progress?.finished == true) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "E${episode.number}" + episode.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = if (playing) MaterialTheme.typography.bodyMediumEmphasized else MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.duration?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
                if (!playing && progress != null && !progress.finished && !progress.nextUp && progress.started) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(3.dp),
                    )
                }
            }
        }
    }
}
