package fi.severi.overtime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.data.nowMillis
import fi.severi.overtime.model.GroupSort
import fi.severi.overtime.model.Source
import fi.severi.overtime.model.SourceType

@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(560.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
            }
        }
    }
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

    val canAdd = url.isNotBlank() && (type != SourceType.XTREAM || (username.isNotBlank() && password.isNotBlank()))

    DialogFrame("Sources", onDismiss) {
        if (sources.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(sources, key = { it.id }) { source ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${source.type.label} · ${source.url}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { vm.refreshSource(source.id) }, enabled = busy == null) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { vm.removeSource(source.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Text("Add source", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SourceType.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = type == option,
                    onClick = { type = option },
                    shape = SegmentedButtonDefaults.itemShape(index, SourceType.entries.size),
                ) { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
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
        } else {
            OutlinedTextField(epgUrl, { epgUrl = it }, label = { Text("XMLTV guide URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Close") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = canAdd,
                onClick = {
                    val id = nowMillis().toString(36)
                    vm.addSource(
                        Source(
                            id = id,
                            name = name.trim().ifEmpty { defaultName(type, url) },
                            type = type,
                            url = url.trim(),
                            username = username.trim(),
                            password = password.trim(),
                            epgUrl = epgUrl.trim(),
                        ),
                    )
                    name = ""; url = ""; username = ""; password = ""; epgUrl = ""
                },
            ) { Text("Add") }
        }
    }
}

private fun defaultName(type: SourceType, url: String): String = when (type) {
    SourceType.M3U_FILE -> url.trim().substringAfterLast('\\').substringAfterLast('/')
    else -> url.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').ifEmpty { "Source" }
}

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var userAgent by remember { mutableStateOf(settings.userAgent) }
    var caching by remember { mutableStateOf(settings.networkCachingMs.toString()) }
    var groupSort by remember { mutableStateOf(settings.groupSort) }

    DialogFrame("Settings", onDismiss) {
        Text("Channel group order", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GroupSort.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = groupSort == option,
                    onClick = { groupSort = option },
                    shape = SegmentedButtonDefaults.itemShape(index, GroupSort.entries.size),
                ) { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        OutlinedTextField(userAgent, { userAgent = it }, label = { Text("HTTP user agent") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            caching, { caching = it.filter(Char::isDigit) },
            label = { Text("Network caching (ms)") },
            supportingText = { Text("Higher is smoother on flaky connections, lower starts faster.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Data folder: ${vm.dataDirectory}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Shortcuts: F or F11 fullscreen · Esc leave fullscreen · Space pause · M mute · Page Up/Down change channel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                vm.updateSettings {
                    it.copy(
                        userAgent = userAgent.trim().ifEmpty { it.userAgent },
                        networkCachingMs = caching.toIntOrNull()?.coerceIn(0, 60_000) ?: it.networkCachingMs,
                        groupSort = groupSort,
                    )
                }
                onDismiss()
            }) { Text("Save") }
        }
    }
}
