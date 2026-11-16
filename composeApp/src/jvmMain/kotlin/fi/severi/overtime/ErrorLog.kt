package fi.severi.overtime

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import fi.severi.overtime.data.FileStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Unexpected errors go to `%APPDATA%\Overtime\error.log`, so a problem on someone else's PC can be
 * looked into afterwards. The log is kept small: older entries are dropped past [MAX_BYTES].
 */
object ErrorLog {
    private const val MAX_BYTES = 256 * 1024

    val file: File by lazy { File(FileStore().directory, "error.log") }

    @Synchronized
    fun write(error: Throwable, where: String) {
        runCatching {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            val entry = "---- ${LocalDateTime.now()} ($where), Java ${System.getProperty("java.version")}, " +
                "${System.getProperty("os.name")} ${System.getProperty("os.version")}\n$trace\n"
            val old = file.takeIf { it.isFile }?.readText().orEmpty()
            file.writeText((old + entry).takeLast(MAX_BYTES))
        }
    }

    /** Logs errors on any thread that nothing else handles. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(error, "thread ${thread.name}")
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Compose UI errors: logged, then shown with where the log is. "RootNodeOwner is already disposed"
     * comes from Compose itself when an event reaches a window part that is being torn down (a closing
     * menu or dialog); nothing is lost by carrying on, so it isn't shown.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    val windowHandlerFactory = WindowExceptionHandlerFactory { window ->
        WindowExceptionHandler { error ->
            write(error, "window")
            if (isHarmless(error)) return@WindowExceptionHandler
            JOptionPane.showMessageDialog(
                window,
                "Overtime ran into an error and has to close:\n${error.message ?: error::class.simpleName}\n\n" +
                    "Details were saved to\n${file.absolutePath}",
                "Overtime",
                JOptionPane.ERROR_MESSAGE,
            )
            window.dispose()
            exitProcess(1)
        }
    }

    private fun isHarmless(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is IllegalStateException && it.message?.contains("already disposed") == true }
}
