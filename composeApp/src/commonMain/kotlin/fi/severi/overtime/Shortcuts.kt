package fi.severi.overtime

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import fi.severi.overtime.model.KeyCombo

/** Where a shortcut applies. Guide shortcuts win over player ones while the guide is open. */
enum class ShortcutGroup { PLAYER, GUIDE }

/** Every keyboard action, with its default keys. The user's own keys are kept in AppSettings.shortcuts. */
enum class Shortcut(val label: String, val group: ShortcutGroup, val defaults: List<KeyCombo>) {
    PLAY_PAUSE("Play / pause", ShortcutGroup.PLAYER, listOf(combo(Key.Spacebar))),
    PREVIOUS("Previous channel (10 s back in movies)", ShortcutGroup.PLAYER, listOf(combo(Key.DirectionLeft))),
    NEXT("Next channel (10 s forward in movies)", ShortcutGroup.PLAYER, listOf(combo(Key.DirectionRight))),
    CHANNEL_UP("Previous channel", ShortcutGroup.PLAYER, listOf(combo(Key.PageUp))),
    CHANNEL_DOWN("Next channel", ShortcutGroup.PLAYER, listOf(combo(Key.PageDown))),
    REWIND("Back 10 seconds", ShortcutGroup.PLAYER, listOf(combo(Key.DirectionLeft, shift = true))),
    FORWARD("Forward 10 seconds", ShortcutGroup.PLAYER, listOf(combo(Key.DirectionRight, shift = true))),
    GO_LIVE("Back to live", ShortcutGroup.PLAYER, listOf(combo(Key.MoveEnd))),
    VOLUME_UP("Volume up", ShortcutGroup.PLAYER, listOf(combo(Key.DirectionUp))),
    VOLUME_DOWN("Volume down", ShortcutGroup.PLAYER, listOf(combo(Key.DirectionDown))),
    MUTE("Mute", ShortcutGroup.PLAYER, listOf(combo(Key.M))),
    AUDIO_TRACK("Next audio track", ShortcutGroup.PLAYER, listOf(combo(Key.B))),
    SUBTITLES("Next subtitle track", ShortcutGroup.PLAYER, listOf(combo(Key.V))),
    NEXT_EPISODE("Next episode", ShortcutGroup.PLAYER, listOf(combo(Key.N))),
    FULLSCREEN("Fullscreen", ShortcutGroup.PLAYER, listOf(combo(Key.F), combo(Key.F11))),
    PIP("Picture-in-picture", ShortcutGroup.PLAYER, listOf(combo(Key.P))),
    SIDEBAR("Show / hide sidebar", ShortcutGroup.PLAYER, listOf(combo(Key.S))),
    STREAM_INFO("Stream info", ShortcutGroup.PLAYER, listOf(combo(Key.I))),
    GUIDE("Programme guide", ShortcutGroup.PLAYER, listOf(combo(Key.G))),
    LIBRARY("Movies & series", ShortcutGroup.PLAYER, listOf(combo(Key.L))),
    BACK("Back, close, leave fullscreen", ShortcutGroup.PLAYER, listOf(combo(Key.Escape))),
    GUIDE_EARLIER("Guide: 30 min earlier", ShortcutGroup.GUIDE, listOf(combo(Key.DirectionLeft))),
    GUIDE_LATER("Guide: 30 min later", ShortcutGroup.GUIDE, listOf(combo(Key.DirectionRight))),
    GUIDE_EARLIER_MORE("Guide: 3 h earlier", ShortcutGroup.GUIDE, listOf(combo(Key.DirectionLeft, shift = true))),
    GUIDE_LATER_MORE("Guide: 3 h later", ShortcutGroup.GUIDE, listOf(combo(Key.DirectionRight, shift = true))),
    GUIDE_NOW("Guide: jump to now", ShortcutGroup.GUIDE, listOf(combo(Key.MoveHome))),
}

private fun combo(key: Key, shift: Boolean = false) = KeyCombo(key.keyCode, shift = shift)

/** Turns the saved overrides into keys per action, and edits them. Overrides equal to the defaults are dropped. */
object Shortcuts {
    /** Keys an action can have at most. */
    const val MAX_KEYS = 2

    fun resolve(overrides: Map<String, List<KeyCombo>>): Map<Shortcut, List<KeyCombo>> =
        Shortcut.entries.associateWith { overrides[it.name] ?: it.defaults }

    /**
     * Puts [combo] on [action] in [slot] (replacing that key, or adding one when [slot] is past the end),
     * and takes it off any other action of the same group. Returns the new overrides and that other action.
     */
    fun assign(
        overrides: Map<String, List<KeyCombo>>,
        action: Shortcut,
        slot: Int,
        combo: KeyCombo,
    ): Pair<Map<String, List<KeyCombo>>, Shortcut?> {
        val current = resolve(overrides)
        val result = overrides.toMutableMap()
        val previousOwner = Shortcut.entries.firstOrNull { it != action && it.group == action.group && combo in current.getValue(it) }
        previousOwner?.let { result[it.name] = current.getValue(it) - combo }
        val keys = current.getValue(action).toMutableList()
        if (slot < keys.size) keys[slot] = combo else keys += combo
        result[action.name] = keys.distinct().take(MAX_KEYS)
        return normalize(result) to previousOwner
    }

    fun remove(overrides: Map<String, List<KeyCombo>>, action: Shortcut, combo: KeyCombo): Map<String, List<KeyCombo>> =
        normalize(overrides + (action.name to (resolve(overrides).getValue(action) - combo)))

    fun reset(overrides: Map<String, List<KeyCombo>>, action: Shortcut): Map<String, List<KeyCombo>> = overrides - action.name

    private fun normalize(overrides: Map<String, List<KeyCombo>>): Map<String, List<KeyCombo>> =
        overrides.filter { (name, keys) -> Shortcut.entries.firstOrNull { it.name == name }?.defaults != keys }
}

private val modifierKeys = setOf(
    Key.ShiftLeft, Key.ShiftRight, Key.CtrlLeft, Key.CtrlRight, Key.AltLeft, Key.AltRight, Key.MetaLeft, Key.MetaRight,
)

/** The key and modifiers of a key press, or null for a modifier key on its own. */
fun KeyEvent.toCombo(): KeyCombo? =
    if (key in modifierKeys) null else KeyCombo(key.keyCode, shift = isShiftPressed, ctrl = isCtrlPressed, alt = isAltPressed)
