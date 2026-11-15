package fi.severi.overtime.data

import fi.severi.overtime.model.PosterWidth
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings saved by older versions must keep loading after options change. */
class SettingsCompatTest {
    private fun load(json: String) = withDataDir { dir ->
        File(dir, "settings.json").writeText(json)
        Repository(FileStore()).loadSettings()
    }

    private fun <T> withDataDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("overtime-settings").toFile()
        System.setProperty("overtime.dataDir", dir.absolutePath)
        try {
            return block(dir)
        } finally {
            System.clearProperty("overtime.dataDir")
            dir.deleteRecursively()
        }
    }

    @Test
    fun mapsRemovedPosterSizes() {
        assertEquals(PosterWidth.SMALL, load("""{"posterWidth": "DENSE"}""").posterWidth)
        assertEquals(PosterWidth.MEDIUM, load("""{"posterWidth": "BALANCED"}""").posterWidth)
        assertEquals(PosterWidth.LARGE, load("""{"posterWidth": "LARGE"}""").posterWidth)
    }

    @Test
    fun unknownValueFallsBackWithoutLosingTheRest() {
        val settings = load("""{"posterWidth": "GIGANTIC", "volume": 42, "accentColor": "#FFB1C8"}""")
        assertEquals(PosterWidth.MEDIUM, settings.posterWidth)
        assertEquals(42, settings.volume)
        assertEquals("#FFB1C8", settings.accentColor)
    }
}
