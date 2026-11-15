package fi.severi.overtime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPosition
import fi.severi.overtime.model.WindowBounds
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fi.severi.overtime.data.FileStore
import fi.severi.overtime.data.Repository
import fi.severi.overtime.player.VlcPlayer
import fi.severi.overtime.ui.App
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.player.REWIND_STEP_MS
import fi.severi.overtime.ui.OvertimeTheme
import fi.severi.overtime.ui.contentColorFor
import fi.severi.overtime.ui.isLightColor
import fi.severi.overtime.ui.sidebarColorOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Image

private const val GUIDE_STEP_MS = 30 * 60_000L

/** Time for the window to leave fullscreen before it is maximised again. */
private const val FULLSCREEN_EXIT_SETTLE_MS = 150L

/** True when a usable part of the saved window lies on a connected screen (a monitor may have been unplugged). */
private fun isOnScreen(b: WindowBounds): Boolean = runCatching {
    // AWT screen bounds use the same scaled units as Compose's dp window position.
    val rect = Rectangle(b.x, b.y, b.width, b.height)
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { device ->
        val visible = device.defaultConfiguration.bounds.intersection(rect)
        visible.width >= 200 && visible.height >= 100
    }
}.getOrDefault(false)

/** Window and taskbar icon; the exe and installer use composeApp/icons/overtime.ico. */
private fun loadAppIcon(): Painter? = runCatching {
    val bytes = object {}.javaClass.getResourceAsStream("/overtime_icon.png")!!.use { it.readBytes() }
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

fun main() = application {
    val appIcon = remember { loadAppIcon() }
    val repo = remember { Repository(FileStore()) }
    // VLC takes the subtitle look at start-up, so it comes from the saved settings.
    val playerResult = remember { runCatching { VlcPlayer(repo.loadSettings().subtitles) } }
    val vm = remember { playerResult.getOrNull()?.let { AppViewModel(repo, it) } }

    val saved = remember { vm?.settings?.value?.window }
    val windowState = rememberWindowState(
        placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = saved?.takeIf(::isOnScreen)?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition.PlatformDefault,
        size = DpSize((saved?.width ?: 1280).dp, (saved?.height ?: 800).dp),
    )
    // Size and position while floating, so a maximised or fullscreen window still remembers its normal bounds.
    var floating by remember { mutableStateOf(saved?.copy(maximized = false)) }
    // Briefly Floating while leaving fullscreen for a maximised window; those bounds aren't the normal size.
    var leavingFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(windowState) {
        snapshotFlow { Triple(windowState.placement, windowState.position, windowState.size) }.collect { (placement, pos, size) ->
            if (placement == WindowPlacement.Floating && pos.isSpecified && !leavingFullscreen) {
                floating = WindowBounds(pos.x.value.roundToInt(), pos.y.value.roundToInt(), size.width.value.roundToInt(), size.height.value.roundToInt())
            }
        }
    }
    val fullscreen by (vm?.fullscreen ?: MutableStateFlow(false)).collectAsState()
    val settingsFlow = remember { vm?.settings ?: MutableStateFlow(AppSettings()) }
    val settings by settingsFlow.collectAsState()
    val titleBarColor = sidebarColorOf(settings.sidebarColor)
    // Leaving fullscreen returns to whatever the window was before (maximised or floating).
    var beforeFullscreen by remember { mutableStateOf(windowState.placement) }
    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            if (windowState.placement != WindowPlacement.Fullscreen) beforeFullscreen = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        } else if (windowState.placement == WindowPlacement.Fullscreen) {
            // Compose's Maximized setter doesn't clear fullscreen, so leave it via Floating and maximise after.
            if (beforeFullscreen == WindowPlacement.Maximized) {
                leavingFullscreen = true
                windowState.placement = WindowPlacement.Floating
                delay(FULLSCREEN_EXIT_SETTLE_MS)
                windowState.placement = WindowPlacement.Maximized
                delay(FULLSCREEN_EXIT_SETTLE_MS)
                leavingFullscreen = false
            } else {
                windowState.placement = WindowPlacement.Floating
            }
        }
    }

    Window(
        onCloseRequest = {
            if (vm != null) {
                val placement = if (windowState.placement == WindowPlacement.Fullscreen) beforeFullscreen else windowState.placement
                floating?.let { bounds -> vm.updateSettings { it.copy(window = bounds.copy(maximized = placement == WindowPlacement.Maximized)) } }
            }
            vm?.shutdown()
            exitApplication()
        },
        state = windowState,
        title = "Overtime",
        icon = appIcon,
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown || vm == null) return@Window false
            // The library is a browsing page: only closing keys and playback basics apply.
            if (vm.library.visible.value) {
                return@Window when (event.key) {
                    Key.Escape, Key.L -> { vm.library.close(); true }
                    Key.Spacebar -> { vm.player.togglePause(); true }
                    Key.F11 -> { vm.toggleFullscreen(); true }
                    else -> false
                }
            }
            // In the guide, left/right move through time (30 min, or 3 h with Shift) instead of changing channel.
            if (vm.guideVisible.value) {
                val step = if (event.isShiftPressed) 3 * GUIDE_STEP_MS else GUIDE_STEP_MS
                when (event.key) {
                    Key.DirectionLeft -> { vm.shiftGuide(-step); return@Window true }
                    Key.DirectionRight -> { vm.shiftGuide(step); return@Window true }
                    Key.MoveHome -> { vm.guideToNow(); return@Window true }
                    else -> Unit
                }
            }
            when (event.key) {
                Key.F, Key.F11 -> { vm.toggleFullscreen(); true }
                Key.G -> { vm.toggleGuide(); true }
                Key.L -> { if (vm.hasLibrary.value) vm.openLibrary(); true }
                Key.Escape -> when {
                    vm.guideVisible.value -> { vm.closeGuide(); true }
                    fullscreen -> { vm.setFullscreen(false); true }
                    !vm.showGroupList.value -> { vm.backToGroups(); true }
                    else -> false
                }
                Key.Spacebar -> { vm.player.togglePause(); true }
                Key.M -> { vm.player.toggleMute(); true }
                Key.S -> { vm.toggleSidebar(); true }
                Key.I -> { vm.toggleStats(); true }
                Key.B -> { vm.cycleAudioTrack(); true }
                Key.V -> { vm.cycleSubtitles(); true }
                // Movies seek with plain arrows; live TV changes channel unless Shift is held.
                Key.DirectionLeft -> {
                    if (event.isShiftPressed || vm.currentVod.value != null) vm.seekBy(-REWIND_STEP_MS) else vm.step(-1)
                    true
                }
                Key.DirectionRight -> {
                    if (event.isShiftPressed || vm.currentVod.value != null) vm.seekBy(REWIND_STEP_MS) else vm.step(1)
                    true
                }
                Key.PageUp -> { vm.step(-1); true }
                Key.PageDown -> { vm.step(1); true }
                Key.MoveEnd -> { vm.goLive(); true }
                Key.DirectionUp -> { vm.adjustVolume(+5); true }
                Key.DirectionDown -> { vm.adjustVolume(-5); true }
                else -> false
            }
        },
    ) {
        // Re-applied after fullscreen changes in case the native window was recreated.
        LaunchedEffect(window, fullscreen, titleBarColor) {
            TitleBar.apply(
                window,
                caption = titleBarColor,
                text = contentColorFor(titleBarColor),
                darkMode = !isLightColor(titleBarColor),
            )
        }

        if (vm != null) {
            App(vm)
        } else {
            OvertimeTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        playerResult.exceptionOrNull()?.message ?: "Could not start the video player.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        }
    }
}
