package fi.severi.overtime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPosition
import fi.severi.overtime.model.WindowBounds
import java.awt.Dimension
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
import fi.severi.overtime.ui.ShortcutGuard
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

/** Smallest window, frame included (590 is where the wrapped player controls just fit). */
private const val MIN_WINDOW_WIDTH = 590
private const val MIN_WINDOW_HEIGHT = 480

/** Time for the window to leave fullscreen before it is maximised again. */
private const val FULLSCREEN_EXIT_SETTLE_MS = 150L

/** True when a usable part of the saved window lies on a connected screen (a monitor may have been unplugged). */
internal fun isOnScreen(b: WindowBounds): Boolean = runCatching {
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

fun main() {
    StartupLog.mark("java")
    ErrorLog.install()
    runApp()
}

@OptIn(ExperimentalComposeUiApi::class)
private fun runApp() = application {
    val appIcon = remember { loadAppIcon() }
    val repo = remember { Repository(FileStore()) }
    // VLC takes the subtitle look at start-up, so it comes from the saved settings.
    val playerResult = remember { runCatching { VlcPlayer(repo.loadSettings().subtitles) }.also { StartupLog.mark("vlc") } }
    // Settings, sources and the cached channel lists.
    val vm = remember { playerResult.getOrNull()?.let { AppViewModel(repo, it) }.also { StartupLog.mark("data") } }

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

    // Picture-in-picture: the main window is minimised while the small one shows the video. Minimising it
    // while something plays switches to picture-in-picture too, and restoring it from the taskbar ends it.
    val pip by (vm?.pip ?: MutableStateFlow(false)).collectAsState()
    LaunchedEffect(pip) { if (pip) windowState.isMinimized = true }
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.isMinimized }.collect { minimized ->
            if (vm == null) return@collect
            if (minimized && !vm.pip.value && vm.isPlaying) vm.enterPip()
            if (!minimized && vm.pip.value) vm.leavePip()
        }
    }

    // Errors are logged to error.log; a harmless Compose teardown error is not shown.
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides ErrorLog.windowHandlerFactory) {
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
                // Settings or Sources open, or typing in a search field: the keys belong to that.
                if (ShortcutGuard.active) return@Window false
                val combo = event.toCombo() ?: return@Window false
                val keys = vm.shortcuts.value
                fun pressed(action: Shortcut) = combo in keys.getValue(action)
                // The library is a browsing page: only closing keys and playback basics apply.
                if (vm.library.visible.value) {
                    return@Window when {
                        pressed(Shortcut.BACK) || pressed(Shortcut.LIBRARY) -> { vm.library.close(); true }
                        pressed(Shortcut.PLAY_PAUSE) -> { vm.player.togglePause(); true }
                        pressed(Shortcut.FULLSCREEN) -> { vm.toggleFullscreen(); true }
                        pressed(Shortcut.PIP) -> { vm.enterPip(); true }
                        else -> false
                    }
                }
                // In the guide its own keys (moving through time) come first; the rest work as usual.
                if (vm.guideVisible.value) {
                    when {
                        pressed(Shortcut.GUIDE_EARLIER) -> { vm.shiftGuide(-GUIDE_STEP_MS); return@Window true }
                        pressed(Shortcut.GUIDE_LATER) -> { vm.shiftGuide(GUIDE_STEP_MS); return@Window true }
                        pressed(Shortcut.GUIDE_EARLIER_MORE) -> { vm.shiftGuide(-3 * GUIDE_STEP_MS); return@Window true }
                        pressed(Shortcut.GUIDE_LATER_MORE) -> { vm.shiftGuide(3 * GUIDE_STEP_MS); return@Window true }
                        pressed(Shortcut.GUIDE_NOW) -> { vm.guideToNow(); return@Window true }
                    }
                }
                val onDemand = vm.currentVod.value != null
                when {
                    pressed(Shortcut.FULLSCREEN) -> { vm.toggleFullscreen(); true }
                    pressed(Shortcut.PIP) -> { vm.enterPip(); true }
                    pressed(Shortcut.GUIDE) -> { vm.toggleGuide(); true }
                    pressed(Shortcut.LIBRARY) -> { if (vm.hasLibrary.value) vm.openLibrary(); true }
                    pressed(Shortcut.BACK) -> when {
                        vm.guideVisible.value -> { vm.closeGuide(); true }
                        fullscreen -> { vm.setFullscreen(false); true }
                        !vm.showGroupList.value -> { vm.backToGroups(); true }
                        else -> false
                    }
                    pressed(Shortcut.PLAY_PAUSE) -> { vm.player.togglePause(); true }
                    pressed(Shortcut.MUTE) -> { vm.player.toggleMute(); true }
                    pressed(Shortcut.SIDEBAR) -> { vm.toggleSidebar(); true }
                    pressed(Shortcut.STREAM_INFO) -> { vm.toggleStats(); true }
                    pressed(Shortcut.AUDIO_TRACK) -> { vm.cycleAudioTrack(); true }
                    pressed(Shortcut.SUBTITLES) -> { vm.cycleSubtitles(); true }
                    pressed(Shortcut.NEXT_EPISODE) -> { vm.playNextEpisode(); true }
                    // Movies seek with these; live TV changes channel.
                    pressed(Shortcut.PREVIOUS) -> { if (onDemand) vm.seekBy(-REWIND_STEP_MS) else vm.step(-1); true }
                    pressed(Shortcut.NEXT) -> { if (onDemand) vm.seekBy(REWIND_STEP_MS) else vm.step(1); true }
                    pressed(Shortcut.REWIND) -> { vm.seekBy(-REWIND_STEP_MS); true }
                    pressed(Shortcut.FORWARD) -> { vm.seekBy(REWIND_STEP_MS); true }
                    pressed(Shortcut.CHANNEL_UP) -> { vm.step(-1); true }
                    pressed(Shortcut.CHANNEL_DOWN) -> { vm.step(1); true }
                    pressed(Shortcut.GO_LIVE) -> { vm.goLive(); true }
                    pressed(Shortcut.VOLUME_UP) -> { vm.adjustVolume(+5); true }
                    pressed(Shortcut.VOLUME_DOWN) -> { vm.adjustVolume(-5); true }
                    else -> false
                }
            },
        ) {
            // Narrowest the player controls fit (rewind bar and two button rows), and tall enough for them
            // and the channel title. In the same scaled units as dp.
            LaunchedEffect(window) { window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT) }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                StartupLog.finish()
            }
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
        if (vm != null && pip) {
            PipWindow(
                vm,
                icon = appIcon,
                onBack = {
                    vm.leavePip()
                    windowState.isMinimized = false
                },
                onClose = { vm.closePip() },
            )
        }
    }
}
