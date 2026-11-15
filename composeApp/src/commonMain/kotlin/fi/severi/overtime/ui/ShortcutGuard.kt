package fi.severi.overtime.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Turns the app's keyboard shortcuts off while something needs the keyboard: an open dialog
 * (Settings, Sources) or a focused text field. Each holder acquires on start and releases when done.
 */
internal object ShortcutGuard {
    private val holds = MutableStateFlow(0)

    val active: Boolean get() = holds.value > 0

    fun acquire() = holds.update { it + 1 }

    fun release() = holds.update { (it - 1).coerceAtLeast(0) }
}

/** Holds [ShortcutGuard] while this composable is shown (dialogs). */
@Composable
internal fun BlockShortcuts() {
    DisposableEffect(Unit) {
        ShortcutGuard.acquire()
        onDispose { ShortcutGuard.release() }
    }
}

/**
 * For text fields outside dialogs: shortcuts are off while the field has focus (so typing "f" doesn't
 * go fullscreen), and Esc leaves the field so they come back.
 */
@Composable
internal fun Modifier.typingBlocksShortcuts(): Modifier {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (focused) ShortcutGuard.release() }
    }
    return this
        .onFocusChanged { state ->
            if (state.isFocused != focused) {
                focused = state.isFocused
                if (focused) ShortcutGuard.acquire() else ShortcutGuard.release()
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                focusManager.clearFocus()
                true
            } else {
                false
            }
        }
}
