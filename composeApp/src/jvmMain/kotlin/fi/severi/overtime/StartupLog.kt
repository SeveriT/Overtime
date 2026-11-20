package fi.severi.overtime

import fi.severi.overtime.data.FileStore
import java.io.File
import java.lang.management.ManagementFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * How long each part of starting up took, one line per start in `%APPDATA%\Overtime\startup.log`, so a
 * slow start (after a reboot, an update, a busy PC) shows where the time went. Keeps the last [MAX_LINES].
 *
 *     2026-11-20 16:55  total 17.2 s  | java 1.4 s, vlc 12.1 s (plugin cache built), data 2.3 s, window 1.4 s
 */
object StartupLog {
    private const val MAX_LINES = 50

    /** When the process began: includes the launcher and the Java runtime starting up. */
    private val processStart: Long = ProcessHandle.current().info().startInstant()
        .map { it.toEpochMilli() }
        .orElse(ManagementFactory.getRuntimeMXBean().startTime)

    private val marks = mutableListOf<Pair<String, Long>>()
    private val notes = mutableListOf<String>()
    private var written = false

    /** The part named [stage] has just finished. */
    @Synchronized
    fun mark(stage: String) {
        marks += stage to System.currentTimeMillis()
    }

    /** Something worth knowing about this start, shown after the stage it happened in. */
    @Synchronized
    fun note(text: String) {
        notes += text
    }

    /** The window has drawn its first frame: writes the line (once). */
    @Synchronized
    fun finish() {
        if (written) return
        written = true
        mark("window")
        runCatching {
            var previous = processStart
            val parts = marks.joinToString(", ") { (stage, at) ->
                "$stage ${seconds(at - previous)}".also { previous = at }
            }
            val extra = if (notes.isEmpty()) "" else "  (${notes.joinToString("; ")})"
            val started = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val line = "$started  total ${seconds(marks.last().second - processStart)}  | $parts$extra"
            val file = File(FileStore().directory, "startup.log")
            val old = if (file.isFile) file.readLines() else emptyList()
            file.writeText((old + line).takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        }
    }

    private fun seconds(ms: Long) = "%.1f s".format(ms / 1000.0)
}
