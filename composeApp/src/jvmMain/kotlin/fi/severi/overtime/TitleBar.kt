package fi.severi.overtime

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * Colours the native Windows title bar so it blends with the app, keeping native snapping,
 * resizing and caption buttons. Needs Windows 11; on older versions the calls fail and are ignored.
 */
object TitleBar {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWCP_ROUNDSMALL = 3

    @Suppress("FunctionName")
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: DwmApi? by lazy {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) null
        else runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()
    }

    @Suppress("FunctionName")
    private interface User32Api : Library {
        fun ShowWindow(hwnd: Pointer, command: Int): Boolean
        fun SetForegroundWindow(hwnd: Pointer): Boolean
    }

    private const val SW_RESTORE = 9

    private val user32: User32Api? by lazy {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) null
        else runCatching { Native.load("user32", User32Api::class.java) }.getOrNull()
    }

    /**
     * Brings a minimised window back the way clicking its taskbar button does: to its maximised or normal
     * size, whichever it had. (Restoring it through Java can leave a maximised window un-maximised at its
     * maximised bounds, with the title bar above the screen.) Returns false if that isn't available.
     */
    fun restoreMinimized(window: Window): Boolean {
        val api = user32 ?: return false
        return runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return false
            api.ShowWindow(hwnd, SW_RESTORE)
            api.SetForegroundWindow(hwnd)
            true
        }.getOrDefault(false)
    }

    /** [darkMode] picks light (true) or dark (false) caption button glyphs. */
    fun apply(window: Window, caption: Color, text: Color, darkMode: Boolean) {
        val api = dwm ?: return
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            api.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(if (darkMode) 1 else 0), 4)
            api.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, IntByReference(caption.toColorRef()), 4)
            api.DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, IntByReference(caption.toColorRef()), 4)
            api.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, IntByReference(text.toColorRef()), 4)
        }
    }

    /** Gives a borderless window Windows 11's small rounded corners (ignored on older Windows). */
    fun roundSmallCorners(window: Window) {
        val api = dwm ?: return
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            api.DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, IntByReference(DWMWCP_ROUNDSMALL), 4)
        }
    }

    /** Windows COLORREF is 0x00BBGGRR. */
    private fun Color.toColorRef(): Int {
        val argb = toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
