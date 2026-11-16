package fi.severi.overtime

import fi.severi.overtime.data.LocalFile
import fi.severi.overtime.data.LocalLibraryParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalLibraryParserTest {
    private fun file(name: String, vararg folders: String, ownArt: String? = null, folderArt: String? = null, parentArt: String? = null, inFolder: Int = 1) =
        LocalFile("C:/Media/${folders.joinToString("/")}/$name", name, folders.toList(), ownArt, folderArt, parentArt, inFolder)

    @Test
    fun episodesFromFileNames() {
        val ep = LocalLibraryParser.episodeOf(file("The.Office.US.S02E05.Halloween.1080p.WEB-DL.x264.mkv", "TV"))!!
        assertEquals("The Office US", ep.show)
        assertEquals(2, ep.season)
        assertEquals(5, ep.episode)
        assertEquals("Halloween", ep.title)

        val alt = LocalLibraryParser.episodeOf(file("Seinfeld 4x11 The Contest.avi", "TV"))!!
        assertEquals("Seinfeld", alt.show)
        assertEquals(4 to 11, alt.season to alt.episode)
    }

    @Test
    fun showNameFromFoldersWhenTheFileIsJustTheEpisode() {
        val bare = LocalLibraryParser.episodeOf(file("S01E03.mkv", "Breaking Bad", "Season 1"))!!
        assertEquals("Breaking Bad", bare.show)
        assertEquals(1 to 3, bare.season to bare.episode)

        val numbered = LocalLibraryParser.episodeOf(file("05 - Gray Matter.mkv", "Breaking Bad", "Season 1"))!!
        assertEquals("Breaking Bad", numbered.show)
        assertEquals(1 to 5, numbered.season to numbered.episode)
        assertEquals("Gray Matter", numbered.title)
    }

    @Test
    fun moviesGetATidyTitleAndYear() {
        val movie = LocalLibraryParser.movieOf(file("Blade.Runner.2049.2017.2160p.UHD.BluRay.x265.mkv", "Movies"))
        assertEquals("Blade Runner 2049", movie.name)
        assertEquals("2017", movie.year)

        val plain = LocalLibraryParser.movieOf(file("Inception (2010).mp4", "Movies"))
        assertEquals("Inception", plain.name)
        assertEquals("2010", plain.year)

        val noYear = LocalLibraryParser.movieOf(file("Home_Video_Christmas.mkv", "Videos"))
        assertEquals("Home Video Christmas", noYear.name)
        assertNull(noYear.year)
        // "Season 1"-less numbered files aren't mistaken for episodes.
        assertNull(LocalLibraryParser.episodeOf(file("01 Intro.mkv", "Videos")))
    }

    @Test
    fun groupsSeriesAndPicksArtwork() {
        val index = LocalLibraryParser.parse(
            listOf(
                file("Show.S01E02.mkv", "Show", "Season 1", parentArt = "file:///show.jpg"),
                file("Show.S01E01.mkv", "Show", "Season 1", parentArt = "file:///show.jpg"),
                file("show s02e01.mkv", "Show", "Season 2"),
                file("Alone (2020).mkv", "Movies", "Alone", folderArt = "file:///alone.jpg"),
                file("A (2001).mkv", "Movies", folderArt = "file:///shared.jpg", inFolder = 2),
            ),
        )
        assertEquals(1, index.shows.size)
        val (show, details) = index.shows.single()
        assertEquals("file:///show.jpg", show.poster)
        assertEquals(listOf(1, 2), details.seasons.map { it.number })
        assertEquals(listOf(1, 2), details.seasons.first().episodes.map { it.number })
        assertEquals(listOf("A", "Alone"), index.movies.map { it.name })
        // A folder poster belongs to a movie only when it's the folder's only video.
        assertEquals(null, index.movies.first { it.name == "A" }.poster)
        assertEquals("file:///alone.jpg", index.movies.first { it.name == "Alone" }.poster)
    }
}
