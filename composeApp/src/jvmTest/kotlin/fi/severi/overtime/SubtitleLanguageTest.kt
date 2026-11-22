package fi.severi.overtime

import fi.severi.overtime.model.SubtitleLanguage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleLanguageTest {
    @Test
    fun matchesTrackNamesVlcShows() {
        val finnish = SubtitleLanguage.FINNISH
        assertTrue(finnish.matches("Track 3 - [Finnish]"))
        assertTrue(finnish.matches("Suomi"))
        assertTrue(finnish.matches("fin"))
        assertTrue(finnish.matches("Subtitles (fi)"))
        // Audio tracks name it the same three ways (any case): the one "Finnish" option covers them all.
        assertTrue(finnish.matches("Finnish 5.1"))
        assertTrue(finnish.matches("FIN - Stereo"))
        assertTrue(finnish.matches("suomi (AAC)"))
        assertFalse(finnish.matches("Track 2 - [English]"))
        // A code must be a whole word: "fin" is not inside "Infinity".
        assertFalse(finnish.matches("Infinity commentary"))
        assertTrue(SubtitleLanguage.GERMAN.matches("Track 4 - [deu]"))
        assertTrue(SubtitleLanguage.ENGLISH.matches("English (SDH)"))
    }
}
