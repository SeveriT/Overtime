package fi.severi.overtime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import fi.severi.overtime.model.WindowBounds
import fi.severi.overtime.player.REWIND_STEP_MS
import fi.severi.overtime.player.VideoSurface
import fi.severi.overtime.player.VlcPlayer
import fi.severi.overtime.ui.OvertimeTheme
import fi.severi.overtime.ui.accentColorOf
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val PIP_WIDTH = 400
private const val PIP_HEIGHT = 225
private const val PIP_MARGIN = 24
private const val PIP_MIN_WIDTH = 240
private const val PIP_MIN_HEIGHT = 135

/** How long opening waits for the first picture to fit the window to, and how often it checks. */
private const val FIT_WAIT_MS = 10_000L
private const val FIT_POLL_MS = 100L
private const val PIXEL_ASPECT_SETTLE_MS = 600L

/** Tallest a fitted window gets, as a share of the screen height. */
private const val MAX_SCREEN_SHARE = 0.6

/** Pixels the mouse must move before a press on the video becomes a drag. */
private const val DRAG_SLOP = 3

/**
 * Picture-in-picture: the video in a small borderless window that stays on top of other windows. Drag it
 * to move, drag its edges to resize; hovering shows the controls, and a double-click goes back to the
 * full window ([onBack]). A dialog-type window, so it doesn't add a second taskbar button.
 */
@Composable
fun PipWindow(vm: AppViewModel, icon: Painter?, onBack: () -> Unit, onClose: () -> Unit) {
    val start = remember { vm.settings.value.pipWindow?.takeIf(::isOnScreen) ?: defaultPipBounds() }
    val state = rememberDialogState(
        position = WindowPosition(start.x.dp, start.y.dp),
        size = DpSize(start.width.dp, start.height.dp),
    )
    val shortcuts by vm.shortcuts.collectAsState()
    DialogWindow(
        onCloseRequest = onClose,
        state = state,
        title = "Overtime",
        icon = icon,
        undecorated = true,
        resizable = true,
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@DialogWindow false
            val combo = event.toCombo() ?: return@DialogWindow false
            fun pressed(action: Shortcut) = combo in shortcuts.getValue(action)
            val onDemand = vm.currentVod.value != null
            when {
                pressed(Shortcut.PIP) || pressed(Shortcut.BACK) -> { onBack(); true }
                pressed(Shortcut.PLAY_PAUSE) -> { vm.player.togglePause(); true }
                pressed(Shortcut.MUTE) -> { vm.player.toggleMute(); true }
                pressed(Shortcut.VOLUME_UP) -> { vm.adjustVolume(+5); true }
                pressed(Shortcut.VOLUME_DOWN) -> { vm.adjustVolume(-5); true }
                pressed(Shortcut.PREVIOUS) -> { if (onDemand) vm.seekBy(-REWIND_STEP_MS) else vm.step(-1); true }
                pressed(Shortcut.NEXT) -> { if (onDemand) vm.seekBy(REWIND_STEP_MS) else vm.step(1); true }
                pressed(Shortcut.CHANNEL_UP) -> { vm.step(-1); true }
                pressed(Shortcut.CHANNEL_DOWN) -> { vm.step(1); true }
                else -> false
            }
        },
    ) {
        LaunchedEffect(window) {
            window.isAlwaysOnTop = true
            window.minimumSize = Dimension(PIP_MIN_WIDTH, PIP_MIN_HEIGHT)
            TitleBar.roundSmallCorners(window)
            // Fit the window to the video's shape, once its first picture is in.
            val vlc = vm.player as? VlcPlayer ?: return@LaunchedEffect
            val aspect = vlc.displayAspect ?: withTimeoutOrNull(FIT_WAIT_MS) {
                while (vlc.displayAspect == null) delay(FIT_POLL_MS)
                // The pixel aspect is read from the stream on the player's next tick.
                delay(PIXEL_ASPECT_SETTLE_MS)
                vlc.displayAspect
            } ?: return@LaunchedEffect
            fitToAspect(window, aspect)
        }
        // Remember where it was, for next time.
        DisposableEffect(window) {
            onDispose {
                val b = window.bounds
                vm.updateSettings { it.copy(pipWindow = WindowBounds(b.x, b.y, b.width, b.height)) }
            }
        }
        val settings by vm.settings.collectAsState()
        OvertimeTheme(accent = accentColorOf(settings.accentColor)) {
            PipContent(vm, window, onBack, onClose)
        }
    }
}

@Composable
private fun PipContent(vm: AppViewModel, window: Window, onBack: () -> Unit, onClose: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val state by vm.player.state.collectAsState()
    val channel by vm.currentChannel.collectAsState()
    val vod by vm.currentVod.collectAsState()
    val onDemand = vod != null
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .hoverable(hover)
            .moveWindowOnDrag(window)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onBack() }) },
    ) {
        VideoSurface(vm.player, Modifier.fillMaxSize())
        AnimatedVisibility(hovered, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                // Top: what's on, mute, back to the full window, close.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        vod?.title ?: channel?.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    PipButton(
                        if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        if (state.muted) "Unmute" else "Mute",
                    ) { vm.player.toggleMute() }
                    // Fits the window to the video again, e.g. after switching to a channel of another shape.
                    PipButton(Icons.Default.FitScreen, "Fit the window to the video") {
                        (vm.player as? VlcPlayer)?.displayAspect?.let { fitToAspect(window, it) }
                    }
                    PipButton(Icons.Default.OpenInFull, "Back to the full window") { onBack() }
                    PipButton(Icons.Default.Close, "Close (pauses)") { onClose() }
                }
                // Middle: previous, play/pause, next (10 s back and forward in movies).
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDemand) {
                        PipButton(Icons.Default.Replay10, "Back 10 seconds") { vm.seekBy(-REWIND_STEP_MS) }
                    } else {
                        PipButton(Icons.Default.SkipPrevious, "Previous channel") { vm.step(-1) }
                    }
                    PipButton(
                        if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (state.paused) "Play" else "Pause",
                        large = true,
                    ) { vm.player.togglePause() }
                    if (onDemand) {
                        PipButton(Icons.Default.Forward10, "Forward 10 seconds") { vm.seekBy(REWIND_STEP_MS) }
                    } else {
                        PipButton(Icons.Default.SkipNext, "Next channel") { vm.step(1) }
                    }
                }
            }
        }
        ResizeEdges(window)
    }
}

@Composable
private fun PipButton(icon: ImageVector, description: String, large: Boolean = false, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
        ),
        modifier = if (large) Modifier.size(56.dp) else Modifier,
    ) {
        Icon(icon, contentDescription = description, modifier = if (large) Modifier.size(32.dp) else Modifier)
    }
}

/**
 * Moves [window] with the mouse once a press has moved past [DRAG_SLOP]. Uses screen positions (as
 * Compose's own window dragging does), since the window moves under the pointer while dragging.
 */
private fun Modifier.moveWindowOnDrag(window: Window): Modifier = pointerInput(window) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val mouseStart = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        val windowStart = window.location
        var dragging = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val mouse = MouseInfo.getPointerInfo()?.location ?: break
            val dx = mouse.x - mouseStart.x
            val dy = mouse.y - mouseStart.y
            if (!dragging && abs(dx) + abs(dy) > DRAG_SLOP) dragging = true
            if (dragging) {
                window.location = Point(windowStart.x + dx, windowStart.y + dy)
                change.consume()
            }
        }
    }
}

/** Grab zones along the borderless window's edges and corners that resize it. */
@Composable
private fun BoxScope.ResizeEdges(window: Window) {
    val edge = 6.dp
    val corner = 12.dp
    // (left, top, right, bottom): which sides of the window this zone moves.
    Box(Modifier.align(Alignment.CenterStart).width(edge).fillMaxHeight().resizer(window, Cursor.W_RESIZE_CURSOR, left = true))
    Box(Modifier.align(Alignment.CenterEnd).width(edge).fillMaxHeight().resizer(window, Cursor.E_RESIZE_CURSOR, right = true))
    Box(Modifier.align(Alignment.TopCenter).height(edge).fillMaxWidth().resizer(window, Cursor.N_RESIZE_CURSOR, top = true))
    Box(Modifier.align(Alignment.BottomCenter).height(edge).fillMaxWidth().resizer(window, Cursor.S_RESIZE_CURSOR, bottom = true))
    Box(Modifier.align(Alignment.TopStart).size(corner).resizer(window, Cursor.NW_RESIZE_CURSOR, left = true, top = true))
    Box(Modifier.align(Alignment.TopEnd).size(corner).resizer(window, Cursor.NE_RESIZE_CURSOR, right = true, top = true))
    Box(Modifier.align(Alignment.BottomStart).size(corner).resizer(window, Cursor.SW_RESIZE_CURSOR, left = true, bottom = true))
    Box(Modifier.align(Alignment.BottomEnd).size(corner).resizer(window, Cursor.SE_RESIZE_CURSOR, right = true, bottom = true))
}

private fun Modifier.resizer(
    window: Window,
    cursor: Int,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
): Modifier = pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(window) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val mouseStart = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        val start = window.bounds
        val min = window.minimumSize
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            val mouse = MouseInfo.getPointerInfo()?.location ?: break
            val dx = mouse.x - mouseStart.x
            val dy = mouse.y - mouseStart.y
            // Moving the left or top edge keeps the opposite edge where it is.
            val width = max(min.width, start.width + when { left -> -dx; right -> dx; else -> 0 })
            val height = max(min.height, start.height + when { top -> -dy; bottom -> dy; else -> 0 })
            val x = if (left) start.x + start.width - width else start.x
            val y = if (top) start.y + start.height - height else start.y
            window.bounds = Rectangle(x, y, width, height)
        }
    }
}

/**
 * Resizes [window] to the video's shape: a wide picture keeps the window's width, a tall one its height
 * (so switching between shapes doesn't shrink it step by step), within the minimum size and 60 % of the
 * screen. The edges nearest the screen's sides stay put, so a window in a corner stays in that corner.
 */
private fun fitToAspect(window: Window, aspect: Float) {
    val b = window.bounds
    val min = window.minimumSize
    val screen = window.graphicsConfiguration.bounds
    var width: Int
    var height: Int
    if (aspect >= 1f) {
        width = b.width
        height = (width / aspect).roundToInt()
    } else {
        height = b.height
        width = (height * aspect).roundToInt()
    }
    if (width < min.width) { width = min.width; height = (width / aspect).roundToInt() }
    if (height < min.height) { height = min.height; width = (height * aspect).roundToInt() }
    val maxHeight = (screen.height * MAX_SCREEN_SHARE).roundToInt()
    if (height > maxHeight) { height = maxHeight; width = (height * aspect).roundToInt() }
    val x = if (b.centerX > screen.centerX) b.x + b.width - width else b.x
    val y = if (b.centerY > screen.centerY) b.y + b.height - height else b.y
    window.bounds = Rectangle(x, y, width, height)
}

/** Bottom-right corner of the main screen's work area (above the taskbar), 16:9. */
private fun defaultPipBounds(): WindowBounds {
    val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return WindowBounds(
        x = area.x + area.width - PIP_WIDTH - PIP_MARGIN,
        y = area.y + area.height - PIP_HEIGHT - PIP_MARGIN,
        width = PIP_WIDTH,
        height = PIP_HEIGHT,
    )
}
