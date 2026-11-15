package fi.severi.overtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.GROUP_ALL
import fi.severi.overtime.model.GROUP_FAVORITES
import fi.severi.overtime.model.GroupEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSidebar(
    vm: AppViewModel,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
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

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Overtime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.refreshAll() }, enabled = busy == null && sources.isNotEmpty()) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onOpenSources) { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Sources") }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        if (sources.size > 1) {
            PickerRow(
                label = sources.firstOrNull { it.id == selectedSourceId }?.name ?: "Source",
                options = sources.map { it.id to it.name },
                onPick = vm::selectSource,
            )
        }

        // Title row: "Groups" with the sort button, or a back arrow with the open group's name.
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(start = if (showGroupList) 16.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showGroupList) {
                Text(
                    "Groups",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text("Group order: ${settings.groupSort.label}") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { vm.cycleGroupSort() }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Change group order")
                    }
                }
            } else {
                IconButton(onClick = { vm.backToGroups() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to groups")
                }
                Text(
                    selectedGroup,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            placeholder = { Text(if (showGroupList) "Search all channels" else "Search in this group") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            sources.isEmpty() -> EmptyMessage("Add a playlist or Xtream login to get started.")
            sourceChannels.isEmpty() -> EmptyMessage(busy ?: "No channels here yet. Try refreshing.")
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy != null) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
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
    LazyColumn(Modifier.weight(1f), state = state) {
        items(groups, key = { it.name }) { group ->
            GroupRow(
                group = group,
                playing = group.name == playingGroup,
                onClick = { onOpen(group.name) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun GroupRow(group: GroupEntry, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (group.name) {
            GROUP_ALL -> Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            GROUP_FAVORITES -> Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        }
        Text(
            group.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (playing) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = "Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            group.count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
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

    LazyColumn(Modifier.weight(1f), state = listState) {
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
private fun PickerRow(
    label: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onPick(id); open = false })
            }
        }
    }
}

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
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(36.dp))
            } else {
                Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
            Icon(
                if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
