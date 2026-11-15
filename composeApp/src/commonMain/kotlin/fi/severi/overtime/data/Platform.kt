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
}

expect fun readLocalFile(path: String): String

expect fun createHttpClient(userAgent: String): HttpClient

/** Parses an XMLTV document (plain or gzip-compressed). */
expect fun parseXmltv(bytes: ByteArray): EpgData

expect fun nowMillis(): Long

/** Formats an epoch-millis instant as local HH:mm. */
expect fun formatClock(epochMillis: Long): String
