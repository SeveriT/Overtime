package fi.severi.overtime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.severi.overtime.AppViewModel
import kotlin.math.roundToInt

/** Brightness, contrast and saturation sliders; changes show live and are saved on release. */
@Composable
internal fun PictureControls(vm: AppViewModel) {
    val picture by vm.picture.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Picture",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // Keeps its height when hidden so the sliders don't jump.
        TextButton(
            onClick = vm::resetPicture,
            enabled = !picture.isDefault,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.alpha(if (picture.isDefault) 0f else 1f),
        ) { Text("Reset") }
    }
    PictureSlider("Brightness", picture.brightness, -0.5f..0.5f, signedPercent(picture.brightness * 200f), vm::savePicture) {
        vm.adjustPicture(picture.copy(brightness = it))
    }
    PictureSlider("Contrast", picture.contrast, 0.5f..1.5f, "${(picture.contrast * 100).roundToInt()} %", vm::savePicture) {
        vm.adjustPicture(picture.copy(contrast = it))
    }
    PictureSlider("Saturation", picture.saturation, 0f..2f, "${(picture.saturation * 100).roundToInt()} %", vm::savePicture) {
        vm.adjustPicture(picture.copy(saturation = it))
    }
}

/**
 * "Only for this channel": while a live channel plays, its picture and sound can be its own instead of
 * the general settings. Shown at the top of the picture and sound menu.
 */
@Composable
internal fun ChannelAdjustSwitch(vm: AppViewModel) {
    val channel by vm.currentChannel.collectAsState()
    val on by vm.channelAdjustOn.collectAsState()
    val ch = channel ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Only for this channel", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (on) "${ch.name} keeps its own picture and sound" else "Changes apply to every channel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Long channel names wrap onto a second line rather than being cut off
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = on, onCheckedChange = vm::setChannelAdjust, modifier = Modifier.padding(end = 8.dp))
    }
}

/**
 * Sound settings under the picture ones: audio delay (lip sync), loudness levelling and a three-band
 * equaliser. Sliders apply live and save on release; the switch saves at once.
 */
@Composable
internal fun AudioControls(vm: AppViewModel) {
    val audio by vm.audio.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            "Sound",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = vm::resetAudio,
            enabled = !audio.isDefault,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.alpha(if (audio.isDefault) 0f else 1f),
        ) { Text("Reset") }
    }
    // Lip sync: snaps to 50 ms steps; + plays the sound later, − earlier.
    PictureSlider("Delay", audio.delayMs.toFloat(), -2000f..2000f, signedSeconds(audio.delayMs), vm::saveAudio) {
        vm.adjustAudio(audio.copy(delayMs = (it / 50f).roundToInt() * 50))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Loudness levelling", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Evens out loud adverts and quiet dialogue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = audio.levelling,
            onCheckedChange = {
                vm.adjustAudio(audio.copy(levelling = it))
                vm.saveAudio()
            },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    PictureSlider("Bass", audio.bass, -12f..12f, signedDb(audio.bass), vm::saveAudio) {
        vm.adjustAudio(audio.copy(bass = it.roundToInt().toFloat()))
    }
    PictureSlider("Voice", audio.voice, -12f..12f, signedDb(audio.voice), vm::saveAudio) {
        vm.adjustAudio(audio.copy(voice = it.roundToInt().toFloat()))
    }
    PictureSlider("Treble", audio.treble, -12f..12f, signedDb(audio.treble), vm::saveAudio) {
        vm.adjustAudio(audio.copy(treble = it.roundToInt().toFloat()))
    }
}

/** Audio delay as seconds with a sign: "+0.35 s", "−1.20 s", "0 s". */
private fun signedSeconds(ms: Int): String = when {
    ms == 0 -> "0 s"
    ms > 0 -> "+%.2f s".format(ms / 1000f)
    else -> "\u2212%.2f s".format(-ms / 1000f)
}

/** Equaliser gain: "+3 dB", "−6 dB", "0 dB". */
private fun signedDb(db: Float): String {
    val v = db.roundToInt()
    return when {
        v > 0 -> "+$v dB"
        v < 0 -> "\u2212${-v} dB"
        else -> "0 dB"
    }
}

/** Brightness reads as an offset from normal: −100 … +100 %. */
private fun signedPercent(value: Float): String {
    val v = value.roundToInt()
    return if (v > 0) "+$v %" else "$v %"
}

@Composable
private fun PictureSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onFinished: () -> Unit,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}
