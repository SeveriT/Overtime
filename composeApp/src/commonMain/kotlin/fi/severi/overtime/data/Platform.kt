package fi.severi.overtime.data

import fi.severi.overtime.model.EpgData
import io.ktor.client.HttpClient

/** Small key/value file storage in the per-user app data directory. */
expect class FileStore() {
    val directory: String
    fun readText(name: String): String?
    fun writeText(name: String, text: String)
    fun readBytes(name: String): ByteArray?
    fun writeBytes(name: String, bytes: ByteArray)
    fun delete(name: String)

    /** When [name] was last written (epoch ms), or null if it doesn't exist. */
    fun modifiedAt(name: String): Long?
}

expect fun readLocalFile(path: String): String

expect fun createHttpClient(userAgent: String): HttpClient

/** Parses an XMLTV document (plain or gzip-compressed). */
expect fun parseXmltv(bytes: ByteArray): EpgData

expect fun nowMillis(): Long

/** Formats an epoch-millis instant as local HH:mm. */
expect fun formatClock(epochMillis: Long): String

/** Local midnight at or before [epochMillis]. */
expect fun startOfLocalDay(epochMillis: Long): Long

/** Short local date such as "Sat 15 Nov". */
expect fun formatDay(epochMillis: Long): String
