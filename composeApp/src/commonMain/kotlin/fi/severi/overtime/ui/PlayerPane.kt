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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.IconButtonWidthOption
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.formatClock
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.player.VideoSurface
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 3_000L
private const val VOLUME_FLYOUT_MS = 1_500L

/** Shared look for the floating toolbar and the volume popup. */
@Composable
private fun FloatingPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
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
    val settings by vm.settings.collectAsState()
    val clock by vm.clock.collectAsState()

    var lastActivity by remember { mutableStateOf(nowMillis()) }
    var overlayVisible by remember { mutableStateOf(true) }
    val keepVisible = channel == null || state.paused || state.error != null

    // Keyboard channel changes reveal the controls just like moving the mouse.
    LaunchedEffect(channel?.id) { lastActivity = nowMillis() }

    // Volume changes from the keyboard show a short popup; the toolbar slider covers it when visible.
    var volumeFlyoutVisible by remember { mutableStateOf(false) }
    var volumeInitialised by remember { mutableStateOf(false) }
    LaunchedEffect(settings.volume, state.muted) {
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
                    onTap = { lastActivity = nowMillis() },
                    onDoubleTap = { vm.toggleFullscreen() },
                )
            },
    ) {
        VideoSurface(vm.player, Modifier.fillMaxSize())

        when {
            channel == null -> CenterMessage("Pick a channel from the list")
            state.error != null -> CenterMessage(state.error!!, isError = true)
            state.buffering != null && !state.playing -> ContainedLoadingIndicator(
                Modifier.align(Alignment.Center).size(72.dp),
            )
        }

        AnimatedVisibility(
            visible = volumeFlyoutVisible && !overlayVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) {
            VolumeFlyout(vm, volume = settings.volume, muted = state.muted)
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                channel?.let { ch ->
                    val (now, next) = remember(ch.id, clock) { vm.nowNext(ch, clock) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                            .padding(horizontal = 28.dp, vertical = 24.dp)
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

                PlayerToolbar(
                    vm = vm,
                    paused = state.paused,
                    muted = state.muted,
                    volume = settings.volume,
                    fullscreen = fullscreen,
                    sidebarVisible = sidebarVisible,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }
        }
    }
}

/**
 * Floating toolbar in the M3 Expressive "Back · Edit · Next" pattern: tonal neighbours around a wide
 * filled play/pause, then mute toggle and volume, all with press shape-morphing.
 */
@Composable
private fun PlayerToolbar(
    vm: AppViewModel,
    paused: Boolean,
    muted: Boolean,
    volume: Int,
    fullscreen: Boolean,
    sidebarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    FloatingPill(modifier) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolbarButton(
                if (sidebarVisible) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                if (sidebarVisible) "Hide channel list (S)" else "Show channel list (S)",
            ) { vm.toggleSidebar() }

            VerticalDivider(Modifier.size(width = 1.dp, height = 28.dp), color = MaterialTheme.colorScheme.outlineVariant)

            ToolbarButton(Icons.Default.SkipPrevious, "Previous channel (←)") { vm.step(-1) }
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
            ToolbarButton(Icons.Default.SkipNext, "Next channel (→)") { vm.step(1) }

            VerticalDivider(Modifier.size(width = 1.dp, height = 28.dp), color = MaterialTheme.colorScheme.outlineVariant)

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

            ToolbarButton(
                if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                if (fullscreen) "Exit fullscreen (F)" else "Fullscreen (F)",
            ) { vm.toggleFullscreen() }
        }
    }
}

@Composable
private fun ToolbarButton(icon: ImageVector, tooltip: String, onClick: () -> Unit) {
    WithTooltip(tooltip) {
        FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(icon, contentDescription = tooltip)
        }
    }
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
            Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(volumeIcon(volume, muted), contentDescription = null, modifier = Modifier.size(22.dp))
            VolumeSlider(volume, muted, onVolumeChange = vm::setVolume, modifier = Modifier.width(150.dp))
            Text(
                if (muted) "Muted" else volume.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp),
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
