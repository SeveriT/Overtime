package fi.severi.overtime.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

actual class FileStore actual constructor() {
    /** `-Dovertime.dataDir=...` redirects storage (used by tests so they never touch real settings). */
    actual val directory: String = System.getProperty("overtime.dataDir")
        ?: ((System.getenv("APPDATA") ?: System.getProperty("user.home")) + File.separator + "Overtime")

    private val dir = File(directory).apply { mkdirs() }

    actual fun readText(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()

    actual fun writeText(name: String, text: String) = writeBytes(name, text.encodeToByteArray())

    actual fun readBytes(name: String): ByteArray? = File(dir, name).takeIf { it.isFile }?.readBytes()

    actual fun writeBytes(name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    actual fun delete(name: String) {
        File(dir, name).delete()
    }

    actual fun modifiedAt(name: String): Long? = File(dir, name).takeIf { it.isFile }?.lastModified()
}

actual fun readLocalFile(path: String): String = File(path).readText()

actual fun createHttpClient(userAgent: String): HttpClient = HttpClient(CIO) {
    expectSuccess = true
    followRedirects = true
    install(UserAgent) { agent = userAgent }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 180_000
        socketTimeoutMillis = 60_000
    }
}

actual fun nowMillis(): Long = System.currentTimeMillis()

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

actual fun formatClock(epochMillis: Long): String =
    clockFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

actual fun startOfLocalDay(epochMillis: Long): Long =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

// English to match the rest of the UI.
private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH)

actual fun formatDay(epochMillis: Long): String =
    dayFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
