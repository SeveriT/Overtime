package fi.severi.overtime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
