package fi.severi.overtime

import fi.severi.overtime.data.FileStore
import fi.severi.overtime.data.Repository
import fi.severi.overtime.model.AppSettings
import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.GROUP_ALL
import fi.severi.overtime.model.GROUP_FAVORITES
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.SourceType
import fi.severi.overtime.model.favoriteGroupKey
import fi.severi.overtime.player.PlayerState
import fi.severi.overtime.player.VideoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Starred groups come first, keeping their usual order; the rest follow in that same order. */
class FavoriteGroupsTest {
    private class SilentPlayer : VideoPlayer {
        override val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState())
        override fun play(url: String, networkCachingMs: Int, userAgent: String, rewindBufferMs: Long, startMs: Long, onDemand: Boolean) = Unit
        override fun stop() = Unit
        override fun togglePause() = Unit
        override fun setVolume(volume: Int) = Unit
        override fun toggleMute() = Unit
        override fun release() = Unit
    }

    @Test
    fun starredGroupsLeadInPlaylistOrder() {
        val dir = Files.createTempDirectory("overtime-groups").toFile()
        try {
            val json = Json { encodeDefaults = true }
            // Playlist order: Sport, News, Kids, Movies.
            val channels = listOf("Sport", "News", "Kids", "Movies").mapIndexed { i, group ->
                Channel("src:$i", "src", "Channel $i", group, "http://example.invalid/$i.ts")
            }
            File(dir, "sources.json").writeText(
                json.encodeToString(ListSerializer(Source.serializer()), listOf(Source("src", "Test", SourceType.M3U_URL, "http://example.invalid"))),
            )
            File(dir, "channels_src.json").writeText(json.encodeToString(ListSerializer(Channel.serializer()), channels))
            File(dir, "settings.json").writeText(
                json.encodeToString(
                    AppSettings.serializer(),
                    AppSettings(lastSourceId = "src", refreshOnLaunch = false, favoriteGroups = setOf(favoriteGroupKey("src", "Movies"))),
                ),
            )
            System.setProperty("overtime.dataDir", dir.absolutePath)

            val vm = AppViewModel(Repository(FileStore()), SilentPlayer())
            assertEquals(listOf(GROUP_ALL, GROUP_FAVORITES, "Movies", "Sport", "News", "Kids"), vm.groups.value.map { it.name })

            // Starring News puts it ahead of Movies again, as in the playlist.
            vm.toggleFavoriteGroup("News")
            val expected = listOf(GROUP_ALL, GROUP_FAVORITES, "News", "Movies", "Sport", "Kids")
            runBlocking { withTimeout(2_000) { vm.groups.first { list -> list.map { it.name } == expected } } }
            assertEquals(listOf(false, false, true, true, false, false), vm.groups.value.map { it.favorite })
            vm.shutdown()
        } finally {
            System.clearProperty("overtime.dataDir")
            dir.deleteRecursively()
        }
    }
}
