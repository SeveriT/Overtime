package fi.severi.overtime.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.FileStore
import fi.severi.overtime.data.Repository
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.player.PlayerState
import fi.severi.overtime.player.VideoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In a narrow window, showing the sidebar squeezes the player so its toolbar wraps onto two rows (and
 * hiding it unwraps them). Clicking Show/Hide sidebar used to rebuild the hovered button and its
 * tooltip mid-click, and Compose failed with "RootNodeOwner is already disposed".
 */
class NarrowSidebarTest {
    private class FakePlayer : VideoPlayer {
        override val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState(playing = true, volume = 70))
        override fun play(url: String, networkCachingMs: Int, userAgent: String, rewindBufferMs: Long, startMs: Long, onDemand: Boolean) = Unit
        override fun stop() = Unit
        override fun togglePause() = Unit
        override fun setVolume(volume: Int) = Unit
        override fun toggleMute() = Unit
        override fun release() = Unit
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun sidebarButtonTogglesInANarrowWindow() {
        val dataDir = Files.createTempDirectory("overtime-narrow").toFile()
        System.setProperty("overtime.dataDir", dataDir.absolutePath)
        // A local folder counts as set up, so the Sources dialog doesn't open over the player.
        File(dataDir, "settings.json").writeText(
            Json.encodeToString(AppSettings.serializer(), AppSettings(localFolders = listOf(dataDir.absolutePath), refreshOnLaunch = false)),
        )
        val vm = AppViewModel(Repository(FileStore()), FakePlayer())
        val scene = ImageComposeScene(1000, 700, Density(1f)) { App(vm) }
        var time = 0L
        fun frames(count: Int) = repeat(count) {
            Thread.sleep(16)
            time += 16_000_000L
            scene.render(time)
        }
        try {
            frames(40)
            scene.sendPointerEvent(PointerEventType.Move, Offset(500f, 350f)) // shows the controls
            frames(30)
            repeat(4) { round ->
                // Show sidebar sits at the start of the one-row toolbar; Hide at the start of the second row.
                val at = if (vm.sidebarVisible.value) Offset(463f, 644f) else Offset(148f, 640f)
                val expected = !vm.sidebarVisible.value
                scene.sendPointerEvent(PointerEventType.Move, at)
                frames(70) // long enough for the tooltip to appear
                scene.sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
                frames(2)
                scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
                frames(30)
                assertEquals(expected, vm.sidebarVisible.value, "round $round")
            }
        } finally {
            scene.close()
            vm.shutdown()
            dataDir.deleteRecursively()
        }
    }
}
