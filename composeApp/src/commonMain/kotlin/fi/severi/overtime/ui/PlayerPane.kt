package fi.severi.overtime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import fi.severi.overtime.player.MediaTrack
import fi.severi.overtime.player.TrackInfo
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.IconButtonWidthOption
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import fi.severi.overtime.model.WatchProgress
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.formatClock
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.player.MediaProgress
import fi.severi.overtime.player.REWIND_STEP_MS
import fi.severi.overtime.player.TimeshiftState
import fi.severi.overtime.player.VideoSurface
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 3_000L
private const val VOLUME_FLYOUT_MS = 1_500L

/** Shared look for the floating toolbar and the volume popup. */
@Composable
private fun FloatingPill(modifier: Modifier = Modifier, shape: Shape = CircleShape, content: @Composable () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun PlayerPane(
    vm: AppViewModel,
    fullscreen: Boolean,
    sidebarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by vm.player.state.collectAsState()
    val channel by vm.currentChannel.collectAsState()
    val vod by vm.currentVod.collectAsState()
    val settings by vm.settings.collectAsState()
    val volume by vm.volume.collectAsState()
    val upNextEntry by vm.upNext.collectAsState()
    val countdown by vm.upNextCountdown.collectAsState()
    val autoplay = settings.autoplayNext
    val clock by vm.clock.collectAsState()
    val hasContent = channel != null || vod != null

    var lastActivity by remember { mutableStateOf(nowMillis()) }
    val focusManager = LocalFocusManager.current
    var overlayVisible by remember { mutableStateOf(true) }
    // Measured so the Up next card can sit just above the controls while they show.
    var toolbarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // A toolbar menu is a popup: the mouse over it doesn't count as activity here, so hold the controls
    // up while one is open (hiding them would close the menu mid-drag), then count down from its closing.
    var menuOpen by remember { mutableStateOf(false) }
    val keepVisible = !hasContent || state.paused || state.error != null || state.ended || menuOpen

    // Keyboard channel changes reveal the controls just like moving the mouse.
    LaunchedEffect(channel?.id, vod) { lastActivity = nowMillis() }

    // Volume changes from the keyboard show a short popup; the toolbar slider covers it when visible.
    var volumeFlyoutVisible by remember { mutableStateOf(false) }
    var volumeInitialised by remember { mutableStateOf(false) }
    LaunchedEffect(volume, state.muted) {
        if (!volumeInitialised) {
            volumeInitialised = true
            return@LaunchedEffect
        }
        volumeFlyoutVisible = true
        delay(VOLUME_FLYOUT_MS)
        volumeFlyoutVisible = false
    }

    LaunchedEffect(lastActivity, keepVisible) {
        overlayVisible = true
        if (!keepVisible) {
            delay(OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) lastActivity = nowMillis()
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        lastActivity = nowMillis()
                        // Clicking the picture leaves a search field, so shortcuts work again.
                        focusManager.clearFocus()
                    },
                    onDoubleTap = { vm.toggleFullscreen() },
                )
            },
    ) {
        VideoSurface(vm.player, Modifier.fillMaxSize())

        when {
            !hasContent -> CenterMessage("Pick a channel from the list")
            state.ended && upNextEntry != null -> CenterMessage("Finished · press play to watch again, or N for the next episode")
            state.ended -> CenterMessage("Finished · press play to watch again")
            state.error != null -> CenterMessage(state.error!!, isError = true)
            state.buffering != null && !state.playing -> ContainedLoadingIndicator(
                Modifier.align(Alignment.Center).size(72.dp),
            )
        }

        // During the end credits: the next episode in the lower right corner, always just above where the
        // controls sit, so it stays put whether they're showing or not.
        val upNextLift = toolbarHeight + TOOLBAR_BOTTOM_GAP
        AnimatedVisibility(
            visible = upNextEntry != null && countdown != null,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = upNextLift),
        ) {
            // Holds the last values while fading out.
            val shownNext = remember { mutableStateOf(upNextEntry) }
            val shownSeconds = remember { mutableStateOf(countdown) }
            upNextEntry?.let { shownNext.value = it }
            countdown?.let { shownSeconds.value = it }
            shownNext.value?.let { next ->
                UpNextCard(
                    next = next,
                    seconds = shownSeconds.value ?: 0,
                    autoplay = autoplay,
                    onPlay = vm::playNextEpisode,
                    onCancel = vm::dismissUpNext,
                )
            }
        }

        AnimatedVisibility(
            visible = volumeFlyoutVisible && !overlayVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            VolumeFlyout(vm, volume = volume, muted = state.muted)
        }

        // While watching delayed with the controls hidden, keep the delay visible.
        val timeshift = state.timeshift
        AnimatedVisibility(
            visible = timeshift != null && !timeshift.isLive && !overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
        ) {
            FloatingPill {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        "−" + formatDuration(timeshift?.behindMs ?: 0),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Too narrow for one row of buttons (about 880 dp with everything shown, plus a margin).
                val compactToolbar = maxWidth < COMPACT_TOOLBAR_WIDTH
                channel?.let { ch ->
                    val (now, next) = remember(ch.id, clock) { vm.nowNext(ch, clock) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                            .padding(start = 28.dp, end = 96.dp, top = 24.dp, bottom = 24.dp)
                            .align(Alignment.TopStart),
                    ) {
                        Text(
                            ch.name,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(ch.group, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
                        if (now != null) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "${formatClock(now.start)}–${formatClock(now.stop)}  ${now.title}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (next != null) {
                            Text(
                                "Next ${formatClock(next.start)}  ${next.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                vod?.let { playback ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                            .padding(start = 28.dp, end = 96.dp, top = 24.dp, bottom = 24.dp)
                            .align(Alignment.TopStart),
                    ) {
                        Text(
                            playback.title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        playback.subtitle?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }

                if (hasContent) {
                    val statsVisible by vm.statsVisible.collectAsState()
                    Box(Modifier.align(Alignment.TopEnd).padding(24.dp)) {
                        WithTooltip(if (statsVisible) "Hide stream info (I)" else "Stream info (I)", above = false) {
                            FilledTonalIconToggleButton(
                                checked = statsVisible,
                                onCheckedChange = { vm.toggleStats() },
                                shapes = IconButtonDefaults.toggleableShapes(),
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = "Stream info")
                            }
                        }
                    }
                }

                CompositionLocalProvider(
                    LocalMenuOpenChange provides { open ->
                        menuOpen = open
                        lastActivity = nowMillis()
                    },
                ) {
                PlayerToolbar(
                    vm = vm,
                    paused = state.paused,
                    muted = state.muted,
                    volume = volume,
                    fullscreen = fullscreen,
                    sidebarVisible = sidebarVisible,
                    timeshift = state.timeshift,
                    media = state.media,
                    onDemand = vod != null,
                    compact = compactToolbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = TOOLBAR_BOTTOM_GAP)
                        .onSizeChanged { toolbarHeight = with(density) { it.height.toDp() } },
                )
                }
            }
        }

        // Stream info stays up independently of the auto-hiding controls, like mpv's stats overlay.
        val statsVisible by vm.statsVisible.collectAsState()
        AnimatedVisibility(
            visible = statsVisible && hasContent,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f),
            // Below the info button, which sits in the top-right corner with the controls.
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = 24.dp),
        ) {
            StreamInfoPanel(vm)
        }
    }
}

/**
 * Floating toolbar in the M3 Expressive "Back · Edit · Next" pattern: tonal neighbours around a wide
 * filled play/pause, then mute toggle and volume, all with press shape-morphing. While the stream is
 * recorded it grows a rewind row (timeline, behind-live time, live marker) and ±10 s buttons.
 */
@Composable
private fun PlayerToolbar(
    vm: AppViewModel,
    paused: Boolean,
    muted: Boolean,
    volume: Int,
    fullscreen: Boolean,
    sidebarVisible: Boolean,
    timeshift: TimeshiftState?,
    media: MediaProgress?,
    onDemand: Boolean,
    /** Buttons in two rows, for a narrow player. */
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val twoRows = timeshift != null || media != null || compact
    val shape = if (twoRows) MaterialTheme.shapes.extraLargeIncreased else CircleShape
    FloatingPill(modifier, shape) {
        Column(
            Modifier.width(IntrinsicSize.Max).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                timeshift != null -> TimeshiftBar(
                    state = timeshift,
                    onSeek = vm::seekTo,
                    onGoLive = vm::goLive,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                )
                media != null -> MediaBar(
                    progress = media,
                    onSeek = vm.player::seekMedia,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                )
            }
            ToolbarButtons(vm, paused, muted, volume, fullscreen, sidebarVisible, timeshift, media, onDemand, compact)
        }
    }
}

/** Seek bar for movies and episodes: elapsed time, a continuous slider, time remaining. */
@Composable
private fun MediaBar(progress: MediaProgress, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val length = progress.durationMs.toFloat().coerceAtLeast(1f)
    val value = (dragValue ?: progress.positionMs.toFloat()).coerceIn(0f, length)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            formatDuration(value.toLong()),
            style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = value,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..length,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Position" },
        )
        Text(
            "−" + formatDuration((length - value).toLong()),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun ToolbarButtons(
    vm: AppViewModel,
    paused: Boolean,
    muted: Boolean,
    volume: Int,
    fullscreen: Boolean,
    sidebarVisible: Boolean,
    timeshift: TimeshiftState?,
    media: MediaProgress?,
    onDemand: Boolean,
    compact: Boolean,
) {
    val args = ToolbarArgs(
        paused = paused,
        muted = muted,
        volume = volume,
        fullscreen = fullscreen,
        sidebarVisible = sidebarVisible,
        timeshift = timeshift,
        onDemand = onDemand,
        canSkip = timeshift != null || media != null,
        // Movies seek with plain arrows; live TV uses Shift so the arrows can change channel.
        skipKey = if (onDemand) "" else "Shift+",
    )
    // Movable, so a narrow window switching between one and two rows moves the buttons instead of
    // rebuilding them: rebuilding the button under the mouse (e.g. Show sidebar, which narrows the
    // player) tore down its tooltip mid-click and Compose failed with "RootNodeOwner is already disposed".
    val navigation = remember(vm) { movableContentOf { a: ToolbarArgs ->
        val paused = a.paused
        val muted = a.muted
        val volume = a.volume
        val fullscreen = a.fullscreen
        val sidebarVisible = a.sidebarVisible
        val timeshift = a.timeshift
        val onDemand = a.onDemand
        val canSkip = a.canSkip
        val skipKey = a.skipKey
            ToolbarButton(
                if (sidebarVisible) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                if (sidebarVisible) "Hide sidebar (S)" else "Show sidebar (S)",
            ) { vm.toggleSidebar() }
            if (!onDemand) ToolbarButton(Icons.Outlined.ViewTimeline, "Programme guide (G)") { vm.openGuide() }
    } }
    val transport = remember(vm) { movableContentOf { a: ToolbarArgs ->
        val paused = a.paused
        val muted = a.muted
        val volume = a.volume
        val fullscreen = a.fullscreen
        val sidebarVisible = a.sidebarVisible
        val timeshift = a.timeshift
        val onDemand = a.onDemand
        val canSkip = a.canSkip
        val skipKey = a.skipKey
            if (!onDemand) ToolbarButton(Icons.Default.SkipPrevious, "Previous channel (←)") { vm.step(-1) }
            if (canSkip) {
                ToolbarButton(Icons.Default.Replay10, "Back 10 seconds (${skipKey}←)") { vm.seekBy(-REWIND_STEP_MS) }
            }
            WithTooltip(if (paused) "Play (Space)" else "Pause (Space)") {
                FilledIconButton(
                    onClick = { vm.player.togglePause() },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonWidthOption.Wide)),
                ) {
                    Icon(
                        if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (paused) "Play" else "Pause",
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
            }
            if (canSkip) {
                ToolbarButton(
                    Icons.Default.Forward10,
                    "Forward 10 seconds (${skipKey}→)",
                    enabled = timeshift?.isLive != true,
                ) { vm.seekBy(REWIND_STEP_MS) }
            }
            val next by vm.upNext.collectAsState()
            next?.let { episode ->
                ToolbarButton(Icons.Default.SkipNext, "Next episode: S${episode.season} E${episode.episode} (N)") { vm.playNextEpisode() }
            }
            if (!onDemand) ToolbarButton(Icons.Default.SkipNext, "Next channel (→)") { vm.step(1) }
            ToolbarButton(Icons.Default.Stop, if (onDemand) "Stop and open Movies & series" else "Stop") {
                if (onDemand) vm.stopToLibrary() else vm.stopPlayback()
            }
    } }
    val outputs = remember(vm) { movableContentOf { a: ToolbarArgs ->
        val paused = a.paused
        val muted = a.muted
        val volume = a.volume
        val fullscreen = a.fullscreen
        val sidebarVisible = a.sidebarVisible
        val timeshift = a.timeshift
        val onDemand = a.onDemand
        val canSkip = a.canSkip
        val skipKey = a.skipKey

            WithTooltip(if (muted) "Unmute (M)" else "Mute (M)") {
                FilledTonalIconToggleButton(
                    checked = muted,
                    onCheckedChange = { vm.player.toggleMute() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(volumeIcon(volume, muted), contentDescription = if (muted) "Unmute" else "Mute")
                }
            }
            VolumeSlider(volume, muted, onVolumeChange = vm::setVolume, modifier = Modifier.width(150.dp))

            TracksButton(vm)
            PictureButton(vm)
            ToolbarButton(Icons.Default.PictureInPictureAlt, "Picture-in-picture (P)") { vm.enterPip() }
            ToolbarButton(
                if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                if (fullscreen) "Exit fullscreen (F)" else "Fullscreen (F)",
            ) { vm.toggleFullscreen() }
    } }
    val divider: @Composable () -> Unit = {
        VerticalDivider(Modifier.size(width = 1.dp, height = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (compact) {
        // Narrow window: playback controls on their own row, the rest below.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { transport(args) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                navigation(args)
                divider()
                outputs(args)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            navigation(args)
            divider()
            transport(args)
            divider()
            outputs(args)
        }
    }
}

/** Space between the controls and the bottom of the player. */
private val TOOLBAR_BOTTOM_GAP = 24.dp

/** Below this player width the toolbar buttons wrap onto two rows. */
private val COMPACT_TOOLBAR_WIDTH = 904.dp

/**
 * In the end credits: the next episode, the time until it starts (with autoplay), Play now (N) and
 * Cancel. The thumbnail's corners follow the card's, inset by the padding, so it sits evenly inside.
 */
@Composable
internal fun UpNextCard(
    next: WatchProgress,
    seconds: Int,
    autoplay: Boolean,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = 10.dp
    Surface(
        shape = RoundedCornerShape(UP_NEXT_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        // Set explicitly: a translucent container doesn't map to a theme role, so it can't be derived.
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        modifier = modifier.padding(24.dp).width(400.dp),
    ) {
        Row(Modifier.padding(padding).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            next.backdrop?.let {
                Artwork(
                    it,
                    contentDescription = null,
                    modifier = Modifier
                        .width(128.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(UP_NEXT_CORNER - padding))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (autoplay) "Up next in $seconds s" else "Up next",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "S${next.season} E${next.episode}" + next.episodeTitle?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPlay,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(start = 10.dp, end = 12.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Play now", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                        Spacer(Modifier.size(6.dp))
                        KeyCap("N")
                    }
                    TextButton(
                        onClick = onCancel,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(32.dp),
                    ) { Text("Cancel", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

/** A small keyboard-key label, e.g. the shortcut on a button. */
@Composable
private fun KeyCap(key: String) {
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    // Centred by the letter itself, not by its text line: the middle of the capital (its baseline less
    // half the cap height) goes to the middle of the box. Line-box centring left it sitting low.
    val capHeightPx = with(density) { style.fontSize.toPx() } * CAP_HEIGHT_RATIO
    Layout(
        content = { Text(key, style = style, maxLines = 1, softWrap = false) },
        modifier = Modifier
            .size(20.dp)
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
    ) { measurables, constraints ->
        val text = measurables.first().measure(Constraints())
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val baseline = text[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: text.height
        val y = (height / 2f + capHeightPx / 2f - baseline).roundToInt()
        layout(width, height) { text.place((width - text.width) / 2, y) }
    }
}

/** Height of a capital letter relative to the font size, for typical UI sans-serif fonts. */
private const val CAP_HEIGHT_RATIO = 0.7f

private val UP_NEXT_CORNER = 22.dp

/** Toolbar menus report opening and closing so the controls stay up meanwhile. */
private val LocalMenuOpenChange = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/** Brightness, contrast and saturation in a menu; highlighted while the picture is adjusted. */
@Composable
private fun PictureButton(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val onMenuOpenChange = LocalMenuOpenChange.current
    DisposableEffect(open) {
        onMenuOpenChange(open)
        onDispose { if (open) onMenuOpenChange(false) }
    }
    val picture by vm.picture.collectAsState()
    Box {
        WithTooltip("Picture: brightness, contrast, saturation") {
            FilledTonalIconToggleButton(
                checked = !picture.isDefault,
                onCheckedChange = { open = true },
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Picture settings")
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            Column(Modifier.width(340.dp).padding(start = 16.dp, end = 8.dp, bottom = 8.dp)) {
                PictureControls(vm)
            }
        }
    }
}

/** Audio and subtitle track menu. Tracks are read when it opens, since streams announce them late. */
@Composable
private fun TracksButton(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val onMenuOpenChange = LocalMenuOpenChange.current
    DisposableEffect(open) {
        onMenuOpenChange(open)
        onDispose { if (open) onMenuOpenChange(false) }
    }
    var info by remember { mutableStateOf<TrackInfo?>(null) }
    Box {
        ToolbarButton(Icons.Outlined.Subtitles, "Audio & subtitles (B / V)") {
            info = vm.player.tracks()
            open = true
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            val tracks = info
            TrackSection("Audio", tracks?.audio.orEmpty().filter { it.id >= 0 }, tracks?.audioSelected) {
                vm.player.selectAudioTrack(it)
                open = false
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
            // Always offer Off, even if the stream lists no subtitles yet.
            val subtitles = tracks?.subtitles.orEmpty().let { list -> if (list.any { it.id < 0 }) list else listOf(MediaTrack(-1, "Off")) + list }
            TrackSection("Subtitles", subtitles, tracks?.subtitleSelected ?: -1) {
                vm.player.selectSubtitleTrack(it)
                open = false
            }
        }
    }
}

@Composable
private fun TrackSection(title: String, tracks: List<MediaTrack>, selected: Int?, onPick: (Int) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (tracks.isEmpty()) {
        Text(
            "None in this stream",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    tracks.forEach { track ->
        DropdownMenuItem(
            text = { Text(track.name) },
            onClick = { onPick(track.id) },
            leadingIcon = {
                if (track.id == selected) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.size(24.dp))
            },
        )
    }
}

/** What the toolbar buttons show, passed into their movable content. */
private data class ToolbarArgs(
    val paused: Boolean,
    val muted: Boolean,
    val volume: Int,
    val fullscreen: Boolean,
    val sidebarVisible: Boolean,
    val timeshift: TimeshiftState?,
    val onDemand: Boolean,
    val canSkip: Boolean,
    val skipKey: String,
)

@Composable
private fun ToolbarButton(icon: ImageVector, tooltip: String, enabled: Boolean = true, onClick: () -> Unit) {
    WithTooltip(tooltip) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(icon, contentDescription = tooltip)
        }
    }
}

/**
 * Rewind row: how far behind live, a continuous slider over the buffered window (oldest → live edge),
 * and the live marker — a LIVE badge in the error roles when following live, an assist chip to jump
 * back otherwise. Dragging previews the time and seeks on release.
 */
@Composable
private fun TimeshiftBar(
    state: TimeshiftState,
    onSeek: (Long) -> Unit,
    onGoLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val length = state.lengthMs.toFloat().coerceAtLeast(1f)
    val live = dragValue == null && state.isLive
    // At live the gap to the edge wobbles by a segment as data arrives; keep the knob at the end instead.
    val value = (dragValue ?: if (live) length else (state.position - state.bufferStart).toFloat()).coerceIn(0f, length)
    val behind = if (dragValue != null) (length - value).toLong() else state.behindMs

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            // Live: how far back you can go. Behind: how far behind live you are.
            "−" + formatDuration(if (live) state.lengthMs else behind),
            style = if (live) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelLargeEmphasized,
            color = if (live) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp).semantics { contentDescription = if (live) "Rewind available" else "Behind live" },
        )
        Slider(
            value = value,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(state.bufferStart + it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..length,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Rewind position" },
        )
        if (live) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(
                    Modifier.height(32.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    Text("LIVE", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        } else {
            WithTooltip("Back to live (End)") {
                AssistChip(
                    onClick = onGoLive,
                    label = { Text("Go live") },
                    leadingIcon = {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    },
                )
            }
        }
    }
}

/** m:ss, or h:mm:ss past an hour. */
private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

private fun volumeIcon(volume: Int, muted: Boolean): ImageVector = when {
    muted || volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
    volume < 50 -> Icons.AutoMirrored.Filled.VolumeDown
    else -> Icons.AutoMirrored.Filled.VolumeUp
}

/** Same pill and same slider as the toolbar's volume control, so the two bars match. */
@Composable
private fun VolumeFlyout(vm: AppViewModel, volume: Int, muted: Boolean) {
    FloatingPill {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(volumeIcon(volume, muted), contentDescription = null, modifier = Modifier.size(24.dp))
            VolumeSlider(volume, muted, onVolumeChange = vm::setVolume, modifier = Modifier.width(150.dp))
            Text(
                if (muted) "Muted" else volume.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.CenterMessage(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isError) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.6f),
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
    )
}
