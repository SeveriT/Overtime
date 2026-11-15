package fi.severi.overtime

import fi.severi.overtime.data.FileStore
import fi.severi.overtime.data.Repository
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.SourceType
import fi.severi.overtime.player.PlayerState
import fi.severi.overtime.player.VideoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards app startup: building the view model with a remembered last channel resumes it. This once
 * crashed ("Failed to launch JVM") because play() ran before properties further down the class existed.
 */
class StartupTest {
    private class FakePlayer : VideoPlayer {
        val played = mutableListOf<String>()
        override val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState())
        override fun play(url: String, networkCachingMs: Int, userAgent: String, rewindBufferMs: Long, startMs: Long) {
            played += url
        }
        override fun stop() = Unit
        override fun togglePause() = Unit
        override fun setVolume(volume: Int) = Unit
        override fun toggleMute() = Unit
        override fun release() = Unit
    }

    @Test
    fun resumesLastChannelOnStartup() {
        val dir = Files.createTempDirectory("overtime-startup").toFile()
        try {
            val json = Json { encodeDefaults = true }
            val channel = Channel("src:1", "src", "Yle TV1", "TV", "http://example.invalid/1.ts")
            File(dir, "sources.json").writeText(
                json.encodeToString(ListSerializer(Source.serializer()), listOf(Source("src", "Test", SourceType.M3U_URL, "http://example.invalid"))),
            )
            File(dir, "channels_src.json").writeText(json.encodeToString(ListSerializer(Channel.serializer()), listOf(channel)))
            File(dir, "settings.json").writeText(json.encodeToString(AppSettings.serializer(), AppSettings(lastChannelId = channel.id, lastSourceId = "src")))
            System.setProperty("overtime.dataDir", dir.absolutePath)

            val player = FakePlayer()
            val vm = AppViewModel(Repository(FileStore()), player)
            assertEquals(channel, vm.currentChannel.value)
            assertEquals(listOf(channel.url), player.played)
            vm.shutdown()
        } finally {
            System.clearProperty("overtime.dataDir")
            dir.deleteRecursively()
        }
    }
}
