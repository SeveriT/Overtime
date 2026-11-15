package fi.severi.overtime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.formatClock
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.player.VideoSurface
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 3_000L

@Composable
fun PlayerPane(
    vm: AppViewModel,
    fullscreen: Boolean,
    sidebarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val onToggleFullscreen = vm::toggleFullscreen
    val state by vm.player.state.collectAsState()
    val channel by vm.currentChannel.collectAsState()
    val settings by vm.settings.collectAsState()
    val clock by vm.clock.collectAsState()

    var lastActivity by remember { mutableStateOf(nowMillis()) }
    var overlayVisible by remember { mutableStateOf(true) }
    val keepVisible = channel == null || state.paused || state.error != null

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
                    onDoubleTap = { onToggleFullscreen() },
                )
            },
    ) {
        VideoSurface(vm.player, Modifier.fillMaxSize())

        when {
            channel == null -> CenterMessage("Pick a channel from the list")
            state.error != null -> CenterMessage(state.error!!, isError = true)
            state.buffering != null && !state.playing -> Box(Modifier.align(Alignment.Center)) {
                CircularProgressIndicator(color = Color.White)
            }
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
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .align(Alignment.TopStart),
                    ) {
                        Text(
                            ch.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(ch.group, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
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

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayButton(
                        if (sidebarVisible) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                        if (sidebarVisible) "Hide channel list" else "Show channel list",
                    ) { vm.toggleSidebar() }
                    Spacer(Modifier.width(8.dp))
                    OverlayButton(Icons.Default.SkipPrevious, "Previous channel") { vm.step(-1) }
                    OverlayButton(if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Play / pause") {
                        vm.player.togglePause()
                    }
                    OverlayButton(Icons.Default.SkipNext, "Next channel") { vm.step(1) }
                    Spacer(Modifier.width(8.dp))
                    OverlayButton(if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Mute") {
                        vm.player.toggleMute()
                    }
                    Slider(
                        value = settings.volume.toFloat(),
                        onValueChange = { vm.setVolume(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    OverlayButton(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Fullscreen") {
                        onToggleFullscreen()
                    }
                }
            }
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

@Composable
private fun OverlayButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}
