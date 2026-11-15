package fi.severi.overtime.player

import kotlin.test.Test
import kotlin.test.assertEquals

class CodecNamesTest {
    @Test
    fun namesVideoCodecsWithProfileAndLevel() {
        assertEquals("H.264 High L4", describeVideoCodec("h264", "H264 - MPEG-4 AVC (part 10)", 100, 40))
        assertEquals("H.264 Main L3.1", describeVideoCodec("h264", null, 77, 31))
        assertEquals("HEVC Main 10 L4.1", describeVideoCodec("hevc", null, 2, 123))
        assertEquals("MPEG-2", describeVideoCodec("mpgv", null, -1, -1))
        assertEquals("Something", describeVideoCodec("xyz1", "Something - extra", 0, 0))
    }

    @Test
    fun namesAudioCodecs() {
        assertEquals("AAC", describeAudioCodec("mp4a", "MPEG AAC Audio"))
        assertEquals("Dolby Digital (AC-3)", describeAudioCodec("a52 ", null))
        assertEquals("Vorbis", describeAudioCodec("vorb", "Vorbis"))
    }
}
