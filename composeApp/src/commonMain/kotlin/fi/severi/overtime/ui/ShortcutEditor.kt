package fi.severi.overtime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import fi.severi.overtime.Shortcut
import fi.severi.overtime.Shortcuts
import fi.severi.overtime.model.KeyCombo
import fi.severi.overtime.toCombo
import kotlinx.coroutines.delay

/** The name of a key on this platform, e.g. "Page Up". */
internal expect fun keyName(keyCode: Long): String

internal fun KeyCombo.label(): String =
    buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        add(keyName(key))
    }.joinToString(" + ")

/**
 * Keys for every action: click a key to change it, + to add a second one, × to remove one. A key given
 * to another action is taken off the one that had it, with a note saying so.
 */
@Composable
internal fun ShortcutEditor(overrides: Map<String, List<KeyCombo>>, onChange: (Map<String, List<KeyCombo>>) -> Unit, twoColumns: Boolean) {
    var capturing by remember { mutableStateOf<Pair<Shortcut, Int>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note) {
        if (note != null) {
            delay(NOTE_MS)
            note = null
        }
    }
    val keys = Shortcuts.resolve(overrides)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            note ?: "Click a key to change it.",
            style = MaterialTheme.typography.bodySmall,
            color = if (note != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(emptyMap()) }, enabled = overrides.isNotEmpty(), shapes = ButtonDefaults.shapes()) {
            Text("Reset all")
        }
    }

    @Composable
    fun ShortcutRow(action: Shortcut) {
        val actionKeys = keys.getValue(action)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(action.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            fun capture(slot: Int): (KeyCombo) -> Unit = { combo ->
                val (updated, tookFrom) = Shortcuts.assign(overrides, action, slot, combo)
                onChange(updated)
                note = tookFrom?.let { "${combo.label()} was moved here from “${it.label}”." }
                capturing = null
            }
            actionKeys.forEachIndexed { slot, combo ->
                if (capturing == action to slot) {
                    CaptureChip(onCombo = capture(slot), onCancel = { capturing = null })
                } else {
                    InputChip(
                        selected = false,
                        onClick = { capturing = action to slot },
                        label = { Text(combo.label()) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove ${combo.label()}",
                                modifier = Modifier.size(16.dp).clickable { onChange(Shortcuts.remove(overrides, action, combo)) },
                            )
                        },
                    )
                }
            }
            if (actionKeys.size < Shortcuts.MAX_KEYS) {
                if (capturing == action to actionKeys.size) {
                    CaptureChip(onCombo = capture(actionKeys.size), onCancel = { capturing = null })
                } else {
                    WithTooltip("Add a key") {
                        IconButton(onClick = { capturing = action to actionKeys.size }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Add, contentDescription = "Add a key for ${action.label}")
                        }
                    }
                }
            }
            val changed = actionKeys != action.defaults
            WithTooltip("Default: " + action.defaults.joinToString(", ") { it.label() }.ifEmpty { "none" }) {
                IconButton(
                    onClick = { onChange(Shortcuts.reset(overrides, action)) },
                    enabled = changed,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset ${action.label}")
                }
            }
        }
    }

    val actions = Shortcut.entries
    if (twoColumns) {
        val half = (actions.size + 1) / 2
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f)) { actions.take(half).forEach { ShortcutRow(it) } }
            Column(Modifier.weight(1f)) { actions.drop(half).forEach { ShortcutRow(it) } }
        }
    } else {
        Column { actions.forEach { ShortcutRow(it) } }
    }
}

/** Waits for the next key press (with modifiers) and hands it over; clicking it or leaving cancels. */
@Composable
private fun CaptureChip(onCombo: (KeyCombo) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AssistChip(
        onClick = onCancel,
        label = { Text("Press a key…") },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier
            .focusRequester(focus)
            .onFocusChanged { state ->
                if (state.isFocused) focused = true else if (focused) onCancel()
            }
            .onPreviewKeyEvent { event ->
                // Every key is ours while waiting, Esc and Space included; a lone Shift/Ctrl/Alt waits on.
                if (event.type == KeyEventType.KeyDown) event.toCombo()?.let(onCombo)
                true
            },
    )
}

private const val NOTE_MS = 4_000L
