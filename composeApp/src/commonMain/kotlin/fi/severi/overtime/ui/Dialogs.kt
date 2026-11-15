package fi.severi.overtime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.model.GroupSort
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.SourceType

/** M3 dialog: extra-large corners, high container tone, scrollable body with fixed action row. */
@Composable
private fun DialogFrame(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(600.dp),
        ) {
            Column(Modifier.padding(top = 24.dp, bottom = 20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Two-line list item with a trailing switch; the whole row toggles. */
@Composable
private fun SwitchRow(title: String, supporting: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row handles clicks; the switch only shows state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SourcesDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val sources by vm.sources.collectAsState()
    val busy by vm.busy.collectAsState()

    var type by remember { mutableStateOf(SourceType.M3U_URL) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }

    /** Source being edited; null while adding a new one. Editing keeps the id, so favourites survive. */
    var editingId by remember { mutableStateOf<String?>(null) }

    val canSave = url.isNotBlank() && (type != SourceType.XTREAM || (username.isNotBlank() && password.isNotBlank()))

    fun resetForm() {
        editingId = null
        name = ""; url = ""; username = ""; password = ""; epgUrl = ""
    }

    fun edit(source: Source) {
        editingId = source.id
        type = source.type
        name = source.name
        url = source.url
        username = source.username
        password = source.password
        epgUrl = source.epgUrl
    }

    fun save() {
        val source = Source(
            id = editingId ?: nowMillis().toString(36),
            name = name.trim().ifEmpty { defaultName(type, url) },
            type = type,
            url = url.trim(),
            username = username.trim(),
            password = password.trim(),
            epgUrl = epgUrl.trim(),
        )
        if (editingId != null) vm.updateSource(source) else vm.addSource(source)
        resetForm()
    }

    DialogFrame(
        title = "Sources",
        onDismiss = onDismiss,
        actions = {
            if (editingId != null) {
                TextButton(onClick = ::resetForm, shapes = ButtonDefaults.shapes()) { Text("Cancel edit") }
            } else {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Close") }
            }
            Button(onClick = ::save, enabled = canSave, shapes = ButtonDefaults.shapes()) {
                Icon(
                    if (editingId != null) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (editingId != null) "Save changes" else "Add source")
            }
        },
    ) {
        if (sources.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(sources, key = { it.id }) { source ->
                    val editing = source.id == editingId
                    Surface(
                        shape = MaterialTheme.shapes.largeIncreased,
                        color = if (editing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val playlistGuides = remember(source, busy) { vm.playlistGuideCount(source.id) }
                                val guideNote = when {
                                    source.epgUrl.isNotBlank() -> " · custom guide"
                                    source.type != SourceType.XTREAM && playlistGuides == 1 -> " · guide from playlist"
                                    source.type != SourceType.XTREAM && playlistGuides > 1 -> " · $playlistGuides guides from playlist"
                                    else -> ""
                                }
                                Text(
                                    "${source.type.label} · ${source.url}$guideNote",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            WithTooltip("Edit") {
                                FilledTonalIconButton(onClick = { edit(source) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                                }
                            }
                            WithTooltip("Refresh") {
                                FilledTonalIconButton(
                                    onClick = { vm.refreshSource(source.id) },
                                    enabled = busy == null,
                                    shapes = IconButtonDefaults.shapes(),
                                ) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                            }
                            WithTooltip("Remove") {
                                OutlinedIconButton(
                                    onClick = { vm.removeSource(source.id) },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
        }

        SectionHeader(if (editingId != null) "Edit source" else "Add source")
        ConnectedChoiceGroup(
            options = SourceType.entries,
            selected = type,
            label = { it.label },
            onSelect = { type = it },
        )
        OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            url, { url = it },
            label = {
                Text(
                    when (type) {
                        SourceType.M3U_URL -> "Playlist URL"
                        SourceType.M3U_FILE -> "Playlist file path"
                        SourceType.XTREAM -> "Server (http://host:port)"
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (type == SourceType.XTREAM) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedTextField(
            epgUrl, { epgUrl = it },
            label = { Text("XMLTV guide URL (optional)") },
            supportingText = {
                Text(
                    if (type == SourceType.XTREAM) "Leave empty to use the provider's own guide. Separate several URLs with commas."
                    else "Leave empty to use the guide the playlist names, if any. Separate several URLs with commas.",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun defaultName(type: SourceType, url: String): String = when (type) {
    SourceType.M3U_FILE -> url.trim().substringAfterLast('\\').substringAfterLast('/')
    else -> url.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').ifEmpty { "Source" }
}

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit, onOpenSources: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val sources by vm.sources.collectAsState()
    var userAgent by remember { mutableStateOf(settings.userAgent) }
    var caching by remember { mutableStateOf(settings.networkCachingMs.toString()) }
    var groupSort by remember { mutableStateOf(settings.groupSort) }
    var rewindEnabled by remember { mutableStateOf(settings.rewindEnabled) }

    // Colours preview live; closing without saving puts the originals back.
    val originalSidebar = remember { settings.sidebarColor }
    val originalAccent = remember { settings.accentColor }
    val cancel = {
        vm.updateSettings { it.copy(sidebarColor = originalSidebar, accentColor = originalAccent) }
        onDismiss()
    }

    DialogFrame(
        title = "Settings",
        onDismiss = cancel,
        actions = {
            TextButton(onClick = cancel, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    vm.updateSettings {
                        it.copy(
                            userAgent = userAgent.trim().ifEmpty { it.userAgent },
                            networkCachingMs = caching.toIntOrNull()?.coerceIn(0, 60_000) ?: it.networkCachingMs,
                            groupSort = groupSort,
                            rewindEnabled = rewindEnabled,
                        )
                    }
                    onDismiss()
                },
            ) { Text("Save") }
        },
    ) {
        SectionHeader("Sources")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (sources.size) {
                    0 -> "No playlists or logins added yet"
                    1 -> sources.first().name
                    else -> "${sources.size} sources: ${sources.joinToString { it.name }}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onOpenSources, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Manage sources")
            }
        }

        SectionHeader("Appearance")
        FieldLabel("Accent colour")
        ColorPicker(
            presets = AccentPresets,
            current = accentColorOf(settings.accentColor),
            onPick = { color -> vm.updateSettings { it.copy(accentColor = if (color == DefaultAccent) null else color.toHex()) } },
            supportingText = "Buttons, sliders, selections and progress.",
        )
        FieldLabel("Sidebar and title bar colour")
        ColorPicker(
            presets = SidebarPresets,
            current = sidebarColorOf(settings.sidebarColor),
            onPick = { color -> vm.updateSettings { it.copy(sidebarColor = if (color == DefaultSidebarColor) null else color.toHex()) } },
            supportingText = "Light colours switch the sidebar text to dark.",
        )
        FieldLabel("Channel group order")
        ConnectedChoiceGroup(
            options = GroupSort.entries,
            selected = groupSort,
            label = { it.label },
            onSelect = { groupSort = it },
        )

        SectionHeader("Playback")
        SwitchRow(
            title = "Live rewind",
            supporting = "Keeps the last ${settings.rewindMinutes} minutes of the channel you watch so you can pause, " +
                "rewind and jump back to live. Uses some disk space while watching.",
            checked = rewindEnabled,
            onCheckedChange = { rewindEnabled = it },
        )
        OutlinedTextField(userAgent, { userAgent = it }, label = { Text("HTTP user agent") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            caching, { caching = it.filter(Char::isDigit) },
            label = { Text("Network caching (ms)") },
            supportingText = { Text("Higher is smoother on flaky connections, lower starts faster.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader("Info")
        Text(
            "Shortcuts: ← → or Page Up/Down change channel · Shift+← → rewind / forward 10 s · End back to live · " +
                "↑ ↓ volume · Space pause · M mute · S channel list · I stream info · G guide (← → move through time, Home now) · F or F11 fullscreen · Esc back / leave fullscreen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Data folder: ${vm.dataDirectory}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
