package fi.severi.overtime.data

import fi.severi.overtime.model.LibraryKind

/** Fills a poster-artwork URL template (see DEFAULT_POSTER_TEMPLATE) for one title. */
object PosterArt {
    private val placeholder = Regex("""\{(tmdb_id|imdb_id|id|type|shape)(\?)?\}""")

    /**
     * Returns null when the title has no usable ID, or when the template needs an ID marked as required
     * (no `?`) that the title doesn't have.
     */
    fun url(template: String, kind: LibraryKind, tmdbId: String?, imdbId: String?, landscape: Boolean): String? {
        val tmdb = tmdbId?.trim()?.takeIf { it.isNotEmpty() && it != "0" }
        val imdb = imdbId?.trim()?.let { if (it.startsWith("tt")) it else if (it.all(Char::isDigit) && it.isNotEmpty()) "tt$it" else null }
        if (tmdb == null && imdb == null) return null
        var missingRequired = false
        val filled = placeholder.replace(template.trim()) { match ->
            val optional = match.groupValues[2] == "?"
            val value = when (match.groupValues[1]) {
                "tmdb_id" -> tmdb
                "imdb_id" -> imdb
                "id" -> imdb ?: "tmdb:$tmdb"
                "type" -> if (kind == LibraryKind.MOVIES) "movie" else "series"
                else -> if (landscape) "landscape" else "poster"
            }
            if (value == null && !optional) missingRequired = true
            value.orEmpty()
        }
        return filled.takeUnless { missingRequired || it.isBlank() }
    }
}
