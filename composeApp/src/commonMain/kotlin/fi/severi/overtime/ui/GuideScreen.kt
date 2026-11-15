package fi.severi.overtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.formatClock
import fi.severi.overtime.data.formatDay
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.data.startOfLocalDay
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.EpgData
import fi.severi.overtime.model.Programme
import fi.severi.overtime.player.VideoSurface
import kotlin.math.max
import kotlin.math.roundToInt

private const val HOUR_MS = 3_600_000L
private const val HALF_HOUR_MS = 1_800_000L
private const val DAY_MS = 86_400_000L

/** One hour of guide width; wide enough for typical programme titles. */
private val HourWidth = 240.dp
private val ChannelColumnWidth = 220.dp
private val RowHeight = 64.dp
private val SideSheetWidth = 360.dp

private data class Selection(val channel: Channel, val programme: Programme)

/**
 * Full programme guide: channels down the side, a time grid across, the live picture and details of the
 * selected programme in a side sheet. Only rows on screen are built and each draws only the programmes
 * inside the visible time window, so it stays quick with thousands of channels.
 */
@Composable
fun GuideScreen(vm: AppViewModel) {
    val epg by vm.epgForSelectedSource.collectAsState()
    val sourceChannels by vm.sourceChannels.collectAsState()
    val settings by vm.settings.collectAsState()
    val guideGroup by vm.guideGroup.collectAsState()
    val onlyWithData by vm.guideOnlyWithData.collectAsState()
    val start by vm.guideStart.collectAsState()
    val clock by vm.clock.collectAsState()
    var selection by remember { mutableStateOf<Selection?>(null) }

    val channels = remember(sourceChannels, guideGroup, onlyWithData, epg, settings.favorites) {
        vm.channelsIn(guideGroup, sourceChannels, settings.favorites)
            .let { list -> if (onlyWithData) list.filter { epg.programmesFor(it).isNotEmpty() } else list }
    }
    // Scrollable range: what the guide covers, but always including a little before and after now.
    val (rangeStart, rangeEnd) = remember(epg, clock / HOUR_MS) {
        val now = nowMillis()
        val span = epg.span
        val from = minOf(span?.first ?: now, now - 2 * HOUR_MS).coerceAtLeast(now - DAY_MS)
        val to = maxOf(span?.second ?: now, now + 3 * HOUR_MS)
        (from / HALF_HOUR_MS * HALF_HOUR_MS) to to
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                GuideTopBar(vm, start, rangeStart, rangeEnd, onlyWithData, guideGroup)
                when {
                    epg.isEmpty -> GuideEmpty("No programme guide for this source. Add an XMLTV guide URL in Settings → Manage sources.")
                    channels.isEmpty() -> GuideEmpty("No channels with guide data in this group.")
                    else -> GuideGrid(
                        vm = vm,
                        epg = epg,
                        channels = channels,
                        start = start,
                        rangeStart = rangeStart,
                        rangeEnd = rangeEnd,
                        now = clock,
                        selection = selection,
                        onSelect = { channel, programme -> selection = Selection(channel, programme) },
                    )
                }
            }
            GuideSideSheet(vm, selection, clock)
        }
    }
}

// ---- top bar -------------------------------------------------------------------------

@Composable
private fun GuideTopBar(
    vm: AppViewModel,
    start: Long,
    rangeStart: Long,
    rangeEnd: Long,
    onlyWithData: Boolean,
    guideGroup: String,
) {
    val groups by vm.groups.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WithTooltip("Close guide (Esc)", above = false) {
                IconButton(onClick = vm::closeGuide, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close guide")
                }
            }
            Text("Guide", style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.width(8.dp))
            GroupMenu(guideGroup, groups.map { it.name }, onPick = { vm.guideGroup.value = it })
            FilterChip(
                selected = onlyWithData,
                onClick = { vm.guideOnlyWithData.value = !onlyWithData },
                label = { Text("With guide only") },
                leadingIcon = if (onlyWithData) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
            Spacer(Modifier.weight(1f))
            WithTooltip("Earlier (←)", above = false) {
                IconButton(onClick = { vm.shiftGuide(-HOUR_MS) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Earlier")
                }
            }
            FilledTonalButton(onClick = vm::guideToNow, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Now")
            }
            WithTooltip("Later (→)", above = false) {
                IconButton(onClick = { vm.shiftGuide(HOUR_MS) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Later")
                }
            }
        }
        DayChips(start, rangeStart, rangeEnd, onPick = { dayStart ->
            // Keep the same time of day when switching days.
            vm.guideStart.value = dayStart + (start - startOfLocalDay(start))
        })
    }
}

@Composable
private fun GroupMenu(current: String, groups: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) {
            Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            groups.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onPick(name); open = false })
            }
        }
    }
}

@Composable
private fun DayChips(start: Long, rangeStart: Long, rangeEnd: Long, onPick: (Long) -> Unit) {
    val today = startOfLocalDay(nowMillis())
    val days = remember(rangeStart, rangeEnd, today) {
        generateSequence(startOfLocalDay(rangeStart)) { startOfLocalDay(it + DAY_MS + HOUR_MS) }
            .takeWhile { it < rangeEnd }
            .take(14)
            .toList()
    }
    val viewedDay = startOfLocalDay(start + HOUR_MS) // the day most of the view is in
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            val label = when (day) {
                today -> "Today"
                startOfLocalDay(today + DAY_MS + HOUR_MS) -> "Tomorrow"
                startOfLocalDay(today - DAY_MS + HOUR_MS) -> "Yesterday"
                else -> formatDay(day)
            }
            FilterChip(
                selected = day == viewedDay,
                onClick = { onPick(day) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun GuideEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- grid --------------------------------------------------------------------------

@Composable
private fun GuideGrid(
    vm: AppViewModel,
    epg: EpgData,
    channels: List<Channel>,
    start: Long,
    rangeStart: Long,
    rangeEnd: Long,
    now: Long,
    selection: Selection?,
    onSelect: (Channel, Programme) -> Unit,
) {
    val current by vm.currentChannel.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pxPerMs = with(density) { HourWidth.toPx() } / HOUR_MS
        val channelColumnPx = with(density) { ChannelColumnWidth.toPx() }
        val viewportMs = ((constraints.maxWidth - channelColumnPx) / pxPerMs).toLong().coerceAtLeast(HALF_HOUR_MS)
        val maxStart = (rangeEnd - viewportMs).coerceAtLeast(rangeStart)
        val viewStart = start.coerceIn(rangeStart, maxStart)
        val viewEnd = viewStart + viewportMs

        // Shift+wheel, horizontal touchpad scrolling and mouse drags all pan the time axis.
        val latest by rememberUpdatedState(Triple(viewStart, rangeStart, maxStart))
        fun pan(deltaPx: Float) {
            val (from, min, max) = latest
            vm.guideStart.value = (from - (deltaPx / pxPerMs).toLong()).coerceIn(min, max)
        }
        val scrollState = rememberScrollableState { delta -> pan(delta); delta }
        val dragState = rememberDraggableState { delta -> pan(delta) }

        Column(
            Modifier
                .fillMaxSize()
                .scrollable(scrollState, Orientation.Horizontal)
                .draggable(dragState, Orientation.Horizontal),
        ) {
            TimeRuler(viewStart, viewEnd, pxPerMs)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(channels, key = { it.id }) { channel ->
                        GuideRow(
                            channel = channel,
                            programmes = epg.programmesFor(channel),
                            viewStart = viewStart,
                            viewEnd = viewEnd,
                            pxPerMs = pxPerMs,
                            now = now,
                            playing = channel.id == current?.id,
                            selected = selection?.takeIf { it.channel.id == channel.id }?.programme,
                            onPlay = {
                                vm.play(channel)
                                vm.closeGuide()
                            },
                            onSelect = { onSelect(channel, it) },
                        )
                    }
                }
                // Now line across all rows.
                if (now in viewStart..viewEnd) {
                    val x = channelColumnPx + (now - viewStart) * pxPerMs
                    Box(
                        Modifier
                            .offset { IntOffset(x.roundToInt() - 1, 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRuler(viewStart: Long, viewEnd: Long, pxPerMs: Float) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatDay(viewStart),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ChannelColumnWidth).padding(start = 24.dp),
        )
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            var tick = (viewStart / HALF_HOUR_MS) * HALF_HOUR_MS
            while (tick <= viewEnd) {
                if (tick >= viewStart - HALF_HOUR_MS) {
                    val x = ((tick - viewStart) * pxPerMs).roundToInt()
                    val onHour = tick % HOUR_MS == 0L
                    Text(
                        formatClock(tick),
                        style = if (onHour) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelMedium,
                        color = if (onHour) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart).offset { IntOffset(x + 8, 0) },
                    )
                }
                tick += HALF_HOUR_MS
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programmes: List<Programme>,
    viewStart: Long,
    viewEnd: Long,
    pxPerMs: Float,
    now: Long,
    playing: Boolean,
    selected: Programme?,
    onPlay: () -> Unit,
    onSelect: (Programme) -> Unit,
) {
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().height(RowHeight).padding(vertical = 2.dp)) {
        // Channel cell: click to watch.
        Surface(
            onClick = onPlay,
            shape = MaterialTheme.shapes.medium,
            color = if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.width(ChannelColumnWidth).fillMaxHeight().padding(start = 8.dp, end = 4.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (channel.logo != null) {
                        AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    channel.name,
                    style = if (playing) MaterialTheme.typography.bodyMediumEmphasized else MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Programmes in the visible window.
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            if (programmes.isEmpty()) {
                Text(
                    "No guide data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                )
            } else {
                var index = programmes.binarySearchBy(viewStart) { it.stop }.let { if (it < 0) -it - 1 else it }
                while (index < programmes.size && programmes[index].start < viewEnd) {
                    val programme = programmes[index]
                    val x = (programme.start - viewStart) * pxPerMs
                    val widthPx = (programme.stop - programme.start) * pxPerMs
                    ProgrammeCell(
                        programme = programme,
                        now = now,
                        selected = programme == selected,
                        // Keep the title readable when the programme began left of the view.
                        stickyPx = max(0f, -x),
                        onClick = { onSelect(programme) },
                        modifier = Modifier
                            .offset { IntOffset(x.roundToInt(), 0) }
                            .width(with(density) { (widthPx - 4).coerceAtLeast(1f).toDp() })
                            .fillMaxHeight(),
                    )
                    index++
                }
            }
        }
    }
}

@Composable
private fun ProgrammeCell(
    programme: Programme,
    now: Long,
    selected: Boolean,
    stickyPx: Float,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val airing = now in programme.start until programme.stop
    val past = programme.stop <= now
    val container = when {
        selected -> scheme.primaryContainer
        airing -> scheme.secondaryContainer
        past -> scheme.surfaceContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        selected -> scheme.onPrimaryContainer
        airing -> scheme.onSecondaryContainer
        past -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = container, contentColor = content, modifier = modifier) {
        val sticky = with(LocalDensity.current) { stickyPx.toDp() }
        Column(
            Modifier.padding(start = 12.dp + sticky, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                programme.title,
                style = if (airing) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatClock(programme.start)} – ${formatClock(programme.stop)}",
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

// ---- side sheet ----------------------------------------------------------------------

@Composable
private fun GuideSideSheet(vm: AppViewModel, selection: Selection?, now: Long) {
    val current by vm.currentChannel.collectAsState()
    val playerState by vm.player.state.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = Modifier.width(SideSheetWidth).fillMaxHeight(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // What's playing now, still live while browsing.
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).background(Color.Black),
            ) {
                VideoSurface(vm.player, Modifier.fillMaxSize())
                current?.let { ch ->
                    Text(
                        ch.name,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (selection == null) {
                Text(
                    "Select a programme to see its details. Click a channel name to watch it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val (channel, programme) = selection
            val airing = now in programme.start until programme.stop
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(programme.title, style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(channel.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    "${dayWord(programme.start)} ${formatClock(programme.start)} – ${formatClock(programme.stop)} · " +
                        "${(programme.stop - programme.start) / 60_000} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                airing -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { ((now - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${(programme.stop - now) / 60_000} min left", style = MaterialTheme.typography.labelMedium)
                }
                programme.start > now -> Text("Starts in ${durationWords(programme.start - now)}", style = MaterialTheme.typography.labelMedium)
                else -> Text("Ended", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (current?.id != channel.id) vm.play(channel)
                        vm.closeGuide()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Watch")
                }
                // The start is still in the rewind buffer: offer to jump back to it.
                val timeshift = playerState.timeshift
                val canRestart = airing && current?.id == channel.id && timeshift != null && programme.start >= timeshift.bufferStart
                if (canRestart) {
                    FilledTonalButton(
                        onClick = {
                            vm.seekTo(programme.start)
                            vm.closeGuide()
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Watch from start")
                    }
                }
            }

            if (programme.description.isNotBlank()) {
                Text(
                    programme.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

private fun dayWord(time: Long): String {
    val today = startOfLocalDay(nowMillis())
    return when (startOfLocalDay(time)) {
        today -> "Today"
        startOfLocalDay(today + DAY_MS + HOUR_MS) -> "Tomorrow"
        startOfLocalDay(today - DAY_MS + HOUR_MS) -> "Yesterday"
        else -> formatDay(time)
    }
}

private fun durationWords(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
