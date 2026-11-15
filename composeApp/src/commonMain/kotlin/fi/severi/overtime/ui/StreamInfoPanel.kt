package fi.severi.overtime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.player.StreamStats
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val REFRESH_MS = 1_000L
private const val COPIED_MS = 2_000L

/**
 * Stream details card: resolution and frame rate up top as the headline, then codec, bitrate,
 * dropped frames, audio and how the stream is delivered. Refreshes every second while shown.
 */
@Composable
fun StreamInfoPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf<StreamStats?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = vm.player.streamStats()
            delay(REFRESH_MS)
        }
    }

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        modifier = modifier.width(320.dp),
    ) {
        // Scrolls when the window is too short for stats, picture controls and the copy button.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Stream info",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::toggleStats, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.Close, contentDescription = "Close stream info", modifier = Modifier.size(20.dp))
                }
            }

            val s = stats
            Column(Modifier.padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Headline: what the user mainly wants — resolution and frame rate.
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(resolutionText(s), style = MaterialTheme.typography.headlineSmallEmphasized.tabular())
                        Text("Resolution", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text(
                            fpsText(s),
                            style = MaterialTheme.typography.headlineSmallEmphasized.tabular(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("Frame rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                InfoRow("Video", s?.videoCodec)
                InfoRow("Declared fps", s?.frameRate?.let(::formatFps))
                InfoRow("Bitrate", s?.bitrateKbps?.let(::formatBitrate))
                InfoRow(
                    "Dropped frames",
                    s?.droppedFrames?.let { dropped -> s.displayedFrames?.let { "$dropped of $it" } ?: "$dropped" },
                )
                InfoRow("Audio", audioText(s))
                InfoRow("Source", s?.source)
                if (s?.bufferMs != null) {
                    InfoRow("Rewind buffer", "${formatMinutes(s.bufferMs)} · ${formatBytes(s.bufferBytes ?: 0)}")
                }
                val url by vm.streamUrl.collectAsState()
                url?.let { CopyUrlButton(it) }
            }
        }
    }
}

/** Copies the stream address (it can hold login details, so it isn't shown). Confirms for a moment. */
@Composable
private fun CopyUrlButton(url: String) {
    @Suppress("DEPRECATION") // LocalClipboard's ClipEntry has no common text constructor yet.
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    FilledTonalButton(
        onClick = {
            clipboard.setText(AnnotatedString(url))
            copied = true
        },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Icon(
            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(if (copied) "Copied" else "Copy stream URL")
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium.tabular(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private fun resolutionText(s: StreamStats?): String {
    val w = s?.width ?: return "—"
    val h = s.height ?: return "—"
    return "${w}×$h"
}

/** Measured rate when available (what is really shown), else the declared one. */
private fun fpsText(s: StreamStats?): String {
    val fps = s?.measuredFps?.takeIf { it > 0.5 } ?: s?.frameRate ?: return "—"
    return "${formatFps(fps)} fps"
}

private fun formatFps(fps: Double): String {
    // Snap near-integer rates (49.9 measured → 50); keep real fractional rates like 29.97.
    val rounded = fps.roundToInt()
    return if (kotlin.math.abs(fps - rounded) < 0.15) "$rounded" else ((fps * 100).roundToInt() / 100.0).toString()
}

private fun formatBitrate(kbps: Int): String =
    if (kbps >= 1000) "${(kbps / 100) / 10.0} Mbit/s" else "$kbps kbit/s"

private fun audioText(s: StreamStats?): String? {
    val codec = s?.audioCodec ?: return null
    val channels = when (s.audioChannels) {
        null -> null
        1 -> "mono"
        2 -> "stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${s.audioChannels} ch"
    }
    val rate = s.audioSampleRate?.let { "${it / 1000.0} kHz".replace(".0 kHz", " kHz") }
    return listOfNotNull(codec, channels, rate).joinToString(" · ")
}

private fun formatMinutes(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${(bytes / 100_000_000) / 10.0} GB"
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    else -> "${bytes / 1_000} kB"
}
