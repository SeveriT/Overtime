package fi.severi.overtime.player

/** Friendly names for libVLC fourccs, with H.264/HEVC profile and level when known. */
internal fun describeVideoCodec(fourcc: String?, description: String?, profile: Int, level: Int): String? {
    val code = fourcc?.trim()?.lowercase()
    val name = when (code) {
        "h264", "avc1" -> "H.264"
        "hevc", "h265", "hev1", "hvc1" -> "HEVC"
        "mpgv", "mp2v" -> "MPEG-2"
        "mp4v" -> "MPEG-4"
        "av01" -> "AV1"
        "vp90" -> "VP9"
        else -> description?.substringBefore(" - ")?.takeIf { it.isNotBlank() } ?: fourcc
    } ?: return null
    val profileName = when (code) {
        "h264", "avc1" -> h264Profiles[profile]
        "hevc", "h265", "hev1", "hvc1" -> hevcProfiles[profile]
        else -> null
    }
    val levelName = when {
        level <= 0 -> null
        code in setOf("hevc", "h265", "hev1", "hvc1") -> "%.1f".format(level / 30.0).removeSuffix(".0") // HEVC: level × 30
        else -> "%.1f".format(level / 10.0).removeSuffix(".0")
    }
    return listOfNotNull(name, profileName, levelName?.let { "L$it" }).joinToString(" ")
}

internal fun describeAudioCodec(fourcc: String?, description: String?): String? = when (fourcc?.trim()?.lowercase()) {
    "mp4a" -> "AAC"
    "a52" -> "Dolby Digital (AC-3)"
    "eac3" -> "Dolby Digital Plus (E-AC-3)"
    "mpga", "mp3" -> "MPEG audio"
    "opus" -> "Opus"
    else -> description?.takeIf { it.isNotBlank() } ?: fourcc
}

private val h264Profiles = mapOf(
    66 to "Baseline",
    77 to "Main",
    88 to "Extended",
    100 to "High",
    110 to "High 10",
    122 to "High 4:2:2",
    244 to "High 4:4:4",
)

private val hevcProfiles = mapOf(1 to "Main", 2 to "Main 10")
