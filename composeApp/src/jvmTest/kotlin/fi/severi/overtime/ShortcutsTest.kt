package fi.severi.overtime

import androidx.compose.ui.input.key.Key
import fi.severi.overtime.model.KeyCombo
import fi.severi.overtime.ui.label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutsTest {
    private fun key(k: Key, shift: Boolean = false) = KeyCombo(k.keyCode, shift = shift)

    @Test
    fun defaultsMatchTheOldFixedKeys() {
        val keys = Shortcuts.resolve(emptyMap())
        assertEquals(listOf(key(Key.F), key(Key.F11)), keys.getValue(Shortcut.FULLSCREEN))
        assertEquals(listOf(key(Key.DirectionLeft, shift = true)), keys.getValue(Shortcut.REWIND))
        assertEquals(listOf(key(Key.DirectionLeft)), keys.getValue(Shortcut.GUIDE_EARLIER))
    }

    @Test
    fun assigningMovesTheKeyAndOnlyStoresChanges() {
        // Give M (mute) to Play / pause as a second key: it leaves Mute.
        val (overrides, tookFrom) = Shortcuts.assign(emptyMap(), Shortcut.PLAY_PAUSE, slot = 1, combo = key(Key.M))
        assertEquals(Shortcut.MUTE, tookFrom)
        val keys = Shortcuts.resolve(overrides)
        assertEquals(listOf(key(Key.Spacebar), key(Key.M)), keys.getValue(Shortcut.PLAY_PAUSE))
        assertEquals(emptyList(), keys.getValue(Shortcut.MUTE))
        assertEquals(setOf("PLAY_PAUSE", "MUTE"), overrides.keys)

        // Putting things back as they were leaves no overrides at all.
        val back = Shortcuts.assign(Shortcuts.remove(overrides, Shortcut.PLAY_PAUSE, key(Key.M)), Shortcut.MUTE, 0, key(Key.M)).first
        assertEquals(emptyMap(), back)
    }

    @Test
    fun guideAndPlayerKeysDontClash() {
        // ← is both "previous channel" and "guide: earlier"; changing one doesn't touch the other.
        val (overrides, tookFrom) = Shortcuts.assign(emptyMap(), Shortcut.GUIDE_NOW, 0, key(Key.DirectionLeft))
        assertEquals(Shortcut.GUIDE_EARLIER, tookFrom)
        assertEquals(listOf(key(Key.DirectionLeft)), Shortcuts.resolve(overrides).getValue(Shortcut.PREVIOUS))
    }

    @Test
    fun replacingAKeyAndResetting() {
        val overrides = Shortcuts.assign(emptyMap(), Shortcut.FULLSCREEN, 0, key(Key.Enter)).first
        assertEquals(listOf(key(Key.Enter), key(Key.F11)), Shortcuts.resolve(overrides).getValue(Shortcut.FULLSCREEN))
        assertNull(Shortcuts.assign(emptyMap(), Shortcut.FULLSCREEN, 0, key(Key.Enter)).second)
        assertEquals(emptyMap(), Shortcuts.reset(overrides, Shortcut.FULLSCREEN))
    }

    @Test
    fun labelsReadLikeKeys() {
        assertEquals("Shift + Left", key(Key.DirectionLeft, shift = true).label())
        assertTrue(key(Key.PageUp).label().contains("Up"))
        assertEquals("F11", key(Key.F11).label())
    }
}
