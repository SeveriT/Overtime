package fi.severi.overtime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.ui.OvertimeTheme
import fi.severi.overtime.ui.contentColorFor
import fi.severi.overtime.ui.isLightColor
import fi.severi.overtime.ui.sidebarColorOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Image

/** Window and taskbar icon; the exe and installer use composeApp/icons/overtime.ico. */
private fun loadAppIcon(): Painter? = runCatching {
    val bytes = object {}.javaClass.getResourceAsStream("/overtime_icon.png")!!.use { it.readBytes() }
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

fun main() = application {
    val appIcon = remember { loadAppIcon() }
    val playerResult = remember { runCatching { VlcPlayer() } }
    val vm = remember { playerResult.getOrNull()?.let { AppViewModel(Repository(FileStore()), it) } }

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    val fullscreen by (vm?.fullscreen ?: MutableStateFlow(false)).collectAsState()
    val settingsFlow = remember { vm?.settings ?: MutableStateFlow(AppSettings()) }
    val settings by settingsFlow.collectAsState()
    val titleBarColor = sidebarColorOf(settings.sidebarColor)
    LaunchedEffect(fullscreen) {
        windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    Window(
        onCloseRequest = {
            vm?.shutdown()
            exitApplication()
        },
        state = windowState,
        title = "Overtime",
        icon = appIcon,
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown || vm == null) return@Window false
            when (event.key) {
                Key.F, Key.F11 -> { vm.toggleFullscreen(); true }
                Key.Escape -> when {
                    fullscreen -> { vm.setFullscreen(false); true }
                    !vm.showGroupList.value -> { vm.backToGroups(); true }
                    else -> false
                }
                Key.Spacebar -> { vm.player.togglePause(); true }
                Key.M -> { vm.player.toggleMute(); true }
                Key.S -> { vm.toggleSidebar(); true }
                Key.PageUp, Key.DirectionLeft -> { vm.step(-1); true }
                Key.PageDown, Key.DirectionRight -> { vm.step(1); true }
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
