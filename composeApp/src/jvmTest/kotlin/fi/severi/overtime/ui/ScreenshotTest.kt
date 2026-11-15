package fi.severi.overtime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.FileStore
import fi.severi.overtime.data.Repository
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.SourceType
import fi.severi.overtime.player.PlayerState
import fi.severi.overtime.player.TimeshiftState
import fi.severi.overtime.player.VideoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test

/**
 * Renders the main screens off-screen to PNGs with demo data and a stand-in player, so UI changes can be
 * reviewed without starting VLC. Enable with OVERTIME_SCREENSHOTS=<output dir>.
 */
/** A full five-minute buffer with the picture [behindMs] behind live. */
private fun timeshift(behindMs: Long): TimeshiftState {
    val live = System.currentTimeMillis()
    return TimeshiftState(bufferStart = live - 300_000, liveEdge = live, position = live - behindMs, liveToleranceMs = 5_500)
}

class ScreenshotTest {
    private class FakePlayer : VideoPlayer {
        val mutableState = MutableStateFlow(PlayerState(playing = true, volume = 70, timeshift = timeshift(behindMs = 2_000)))
        override val state: StateFlow<PlayerState> = mutableState
        override fun play(url: String, networkCachingMs: Int, userAgent: String, rewindBufferMs: Long, startMs: Long) = Unit
        override fun stop() = Unit
        override fun togglePause() = Unit
        override fun setVolume(volume: Int) = Unit
        override fun toggleMute() = Unit
        override fun release() = Unit
    }

    @Test
    fun renderScreens() {
        val outDir = System.getenv("OVERTIME_SCREENSHOTS")?.let(::File) ?: return
        outDir.mkdirs()
        val dataDir = Files.createTempDirectory("overtime-shots").toFile()
        System.setProperty("overtime.dataDir", dataDir.absolutePath)
        seed(dataDir)

        val player = FakePlayer()
        val vm = AppViewModel(Repository(FileStore()), player)
        Thread.sleep(1_500) // cached EPG loads in the background

        shoot(outDir, "1_groups", 1280, 800) { App(vm) }

        vm.openGroup("Sport")
        shoot(outDir, "2_channels", 1280, 800) { App(vm) }

        shoot(outDir, "3_settings", 700, 1100) {
            OvertimeTheme { SettingsDialog(vm, onDismiss = {}, onOpenSources = {}) }
        }

        vm.updateSettings { it.copy(accentColor = "#FFB1C8", sidebarColor = "#2E1219") }
        shoot(outDir, "4_custom_colours", 1280, 800) { App(vm) }

        vm.updateSettings { it.copy(accentColor = null, sidebarColor = null) }
        player.mutableState.value = player.mutableState.value.copy(paused = true, timeshift = timeshift(behindMs = 83_000))
        shoot(outDir, "5_rewound", 1280, 800) { App(vm) }

        player.mutableState.value = player.mutableState.value.copy(paused = false, timeshift = null)
        shoot(outDir, "6_no_rewind", 1280, 800) { App(vm) }

        vm.shutdown()
        dataDir.deleteRecursively()
    }

    private fun shoot(dir: File, name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width, height, Density(1f), content = content)
        var image = scene.render(0)
        // Let flows, effects and enter animations settle.
        for (frame in 1..60) {
            Thread.sleep(16)
            image = scene.render(frame * 16_000_000L)
        }
        File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    private fun seed(dir: File) {
        val json = Json { encodeDefaults = true }
        val source = Source("demo", "Demo IPTV", SourceType.M3U_URL, "http://example.invalid/list.m3u")
        val groups = listOf("TV" to listOf("Yle TV1", "Yle TV2", "MTV3", "Nelonen"),
            "Sport" to listOf("Viaplay Sport 1", "Viaplay Sport 2", "V Sport Golf", "Eurosport 1", "MTV Urheilu 1", "Sky Sports F1"),
            "Finland - Ruutu+" to listOf("Ruutu+ Sport 1", "Ruutu+ Sport 2"),
            "United Kingdom - Sport" to listOf("Sky Sports Main Event", "TNT Sports 1", "BBC One"))
        val channels = groups.flatMap { (group, names) ->
            names.map { Channel("demo:${it.hashCode()}", "demo", it, group, "http://example.invalid/$it.ts", epgId = it) }
        }
        File(dir, "sources.json").writeText(json.encodeToString(ListSerializer(Source.serializer()), listOf(source)))
        File(dir, "channels_demo.json").writeText(json.encodeToString(ListSerializer(Channel.serializer()), channels))
        val playing = channels.first { it.name == "Viaplay Sport 1" }
        File(dir, "settings.json").writeText(
            json.encodeToString(
                AppSettings.serializer(),
                AppSettings(lastChannelId = playing.id, lastSourceId = "demo", favorites = setOf(playing.id, channels[0].id), volume = 70),
            ),
        )
        // A guide where every channel is 40% into an hour-long programme.
        val fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        val start = ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(24)
        val xml = buildString {
            append("<tv>")
            channels.forEachIndexed { i, ch ->
                append("<programme channel=\"${ch.name}\" start=\"${fmt.format(start)}\" stop=\"${fmt.format(start.plusHours(1))}\">")
                append("<title>${listOf("Premier League: Arsenal v Spurs", "Liiga: HIFK – Tappara", "News", "Formula 1 Qualifying")[i % 4]}</title></programme>")
            }
            append("</tv>")
        }
        File(dir, "epg_demo.xml").writeText(xml)
    }
}
