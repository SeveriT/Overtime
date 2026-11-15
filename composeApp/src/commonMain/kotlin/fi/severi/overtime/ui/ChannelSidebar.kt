package fi.severi.overtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.GROUP_ALL
import fi.severi.overtime.model.GROUP_FAVORITES
import fi.severi.overtime.model.GroupEntry

/** Inset of drawer items from the sidebar edges, per the M3 navigation drawer spec. */
private val ItemPadding = NavigationDrawerItemDefaults.ItemPadding

/**
 * Channel browser styled as an M3 navigation drawer: groups are pill drawer items with a count badge,
 * the open group lists channels as pill rows, the playing one highlighted with the selected tone.
 */
@Composable
fun ChannelSidebar(
    vm: AppViewModel,
    onOpenSettings: () -> Unit,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources by vm.sources.collectAsState()
    val selectedSourceId by vm.selectedSourceId.collectAsState()
    val sourceChannels by vm.sourceChannels.collectAsState()
    val groups by vm.groups.collectAsState()
    val selectedGroup by vm.selectedGroup.collectAsState()
    val showGroupList by vm.showGroupList.collectAsState()
    val query by vm.query.collectAsState()
    val current by vm.currentChannel.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val settings by vm.settings.collectAsState()

    val channels = remember(sourceChannels, selectedGroup, showGroupList, query, settings.favorites) {
        vm.visibleChannels(sourceChannels, selectedGroup, showGroupList, query, settings.favorites)
    }
    val searching = query.isNotBlank()

    // Lives outside the branches so the group list keeps its scroll position while a group is open.
    val groupListState = rememberLazyListState()

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Overtime",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.weight(1f),
            )
            WithTooltip("Refresh", above = false) {
                IconButton(
                    onClick = { vm.refreshAll() },
                    enabled = busy == null && sources.isNotEmpty(),
                    shapes = IconButtonDefaults.shapes(),
                ) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
            }
            WithTooltip("Settings", above = false) {
                IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        if (sources.size > 1) {
            SourcePicker(
                label = sources.firstOrNull { it.id == selectedSourceId }?.name ?: "Source",
                options = sources.map { it.id to it.name },
                onPick = vm::selectSource,
            )
        }

        PillSearchField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = if (showGroupList) "Search all channels" else "Search in this group",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )

        // Section title: "Groups" with the order button, or back + the open group's name.
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = if (showGroupList) 28.dp else 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showGroupList) {
                Text(
                    "Groups",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                WithTooltip("Group order: ${settings.groupSort.label}") {
                    IconButton(onClick = { vm.cycleGroupSort() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Change group order")
                    }
                }
            } else {
                WithTooltip("Back to groups (Esc)") {
                    FilledTonalIconButton(onClick = { vm.backToGroups() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to groups")
                    }
                }
                Text(
                    selectedGroup,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when {
            sources.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Add a playlist or Xtream login to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ExtendedFloatingActionButton(
                        onClick = onAddSource,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add source") },
                    )
                }
            }
            sourceChannels.isEmpty() -> EmptyMessage(if (busy != null) "Loading channels…" else "No channels here yet. Try refreshing.")
            showGroupList && !searching -> GroupList(
                groups = groups,
                playingGroup = current?.takeIf { it.sourceId == selectedSourceId }?.group,
                state = groupListState,
                onOpen = vm::openGroup,
            )
            channels.isEmpty() -> EmptyMessage(
                when {
                    searching -> "No channels match “$query”."
                    selectedGroup == GROUP_FAVORITES -> "No favorites yet. Star a channel to add it here."
                    else -> "This group is empty."
                },
            )
            else -> ChannelList(vm, channels, current, settings.favorites, selectedGroup, showGroupList, query)
        }

        // Status line
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (busy != null) LoadingIndicator(Modifier.size(28.dp))
            val summary = when {
                searching -> "${channels.size} results"
                showGroupList -> "${(groups.size - 2).coerceAtLeast(0)} groups · ${sourceChannels.size} channels"
                else -> "${channels.size} channels"
            }
            Text(
                text = error ?: busy ?: summary,
                style = MaterialTheme.typography.labelMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.EmptyMessage(text: String) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun ColumnScope.GroupList(
    groups: List<GroupEntry>,
    playingGroup: String?,
    state: LazyListState,
    onOpen: (String) -> Unit,
) {
    LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(bottom = 8.dp)) {
        items(groups, key = { it.name }) { group ->
            val playing = group.name == playingGroup
            NavigationDrawerItem(
                label = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                selected = playing,
                onClick = { onOpen(group.name) },
                icon = {
                    when {
                        playing -> Icon(Icons.Default.GraphicEq, contentDescription = "Playing")
                        group.name == GROUP_ALL -> Icon(Icons.Outlined.GridView, contentDescription = null)
                        group.name == GROUP_FAVORITES -> Icon(Icons.Default.Star, contentDescription = null, tint = FavoriteGold)
                        else -> Icon(Icons.Outlined.LiveTv, contentDescription = null)
                    }
                },
                badge = { Text(group.count.toString(), style = MaterialTheme.typography.labelLarge) },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                modifier = Modifier.padding(ItemPadding),
            )
        }
    }
}

@Composable
private fun ColumnScope.ChannelList(
    vm: AppViewModel,
    channels: List<Channel>,
    current: Channel?,
    favorites: Set<String>,
    selectedGroup: String,
    showGroupList: Boolean,
    query: String,
) {
    val clock by vm.clock.collectAsState()
    // Re-read EPG when it changes so now/next lines refresh.
    val epg by vm.epgForSelectedSource.collectAsState()

    // Opening a group starts with the playing channel in view, a little below the top.
    val listState = remember(selectedGroup, showGroupList) {
        val index = channels.indexOfFirst { it.id == current?.id }
        LazyListState(firstVisibleItemIndex = (index - 2).coerceAtLeast(0))
    }
    LaunchedEffect(query) { if (query.isNotBlank()) listState.scrollToItem(0) }

    LazyColumn(
        Modifier.weight(1f),
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(channels, key = { it.id }) { channel ->
            val (now, _) = remember(channel.id, clock, epg) { vm.nowNext(channel, clock) }
            ChannelRow(
                channel = channel,
                subtitle = now?.title ?: channel.group.takeIf { showGroupList },
                progress = now?.let { (clock - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) },
                selected = channel.id == current?.id,
                favorite = channel.id in favorites,
                onClick = { vm.play(channel) },
                onToggleFavorite = { vm.toggleFavorite(channel) },
            )
        }
    }
}

@Composable
private fun SourcePicker(label: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        FilledTonalButton(onClick = { open = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onPick(id); open = false })
            }
        }
    }
}

/** A channel as a drawer-style pill: logo, name, now playing with progress, favourite toggle. */
@Composable
private fun ChannelRow(
    channel: Channel,
    subtitle: String?,
    progress: Float?,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        selected = selected,
        shape = CircleShape,
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logo != null) {
                    AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(32.dp))
                } else {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    channel.name,
                    style = if (selected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onSecondaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        trackColor = scheme.onSurfaceVariant.copy(alpha = 0.22f),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp).height(4.dp),
                    )
                }
            }
            IconToggleButton(
                checked = favorite,
                onCheckedChange = { onToggleFavorite() },
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = FavoriteGold),
            ) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
