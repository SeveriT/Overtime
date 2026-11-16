package fi.severi.overtime.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalScannerTest {
    private val root: File = Files.createTempDirectory("overtime-local").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun touch(path: String) = File(root, path).apply { parentFile.mkdirs(); writeText("x") }

    @Test
    fun findsVideosAndArtwork() {
        touch("Movies/Inception (2010)/Inception (2010).mkv")
        touch("Movies/Inception (2010)/poster.jpg")
        touch("Movies/Loose/Heat.1995.mp4")
        touch("Movies/Loose/Ronin.1998.mp4")
        touch("Movies/Loose/folder.jpg")
        touch("TV/Dark/folder.png")
        touch("TV/Dark/Season 1/Dark.S01E01.mkv")
        touch("TV/Dark/Season 1/Dark.S01E01.srt") // subtitles: not a video
        touch("TV/Dark/Season 1/notes.txt")

        val index = LocalLibraryParser.parse(scanLocalFolders(listOf(File(root, "Movies").path, File(root, "TV").path)))
        assertEquals(listOf("Heat", "Inception", "Ronin"), index.movies.map { it.name })
        assertTrue(index.movies.first { it.name == "Inception" }.poster!!.endsWith("poster.jpg"))
        // Two movies share the Loose folder, so its folder.jpg belongs to neither.
        assertEquals(null, index.movies.first { it.name == "Heat" }.poster)
        val (show, details) = index.shows.single()
        assertEquals("Dark", show.name)
        assertTrue(show.poster!!.endsWith("folder.png"))
        assertEquals(1, details.seasons.single().episodes.size)
    }

    @Test
    fun missingFoldersAreSkipped() {
        assertEquals(emptyList(), scanLocalFolders(listOf(File(root, "nope").path)))
    }
}
