package fi.severi.overtime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Preset colour circles plus a Custom circle that opens a full RGB picker: a saturation/brightness
 * square, a hue bar and a #RRGGBB field, all in sync. [onPick] fires for every change (also while
 * dragging) so callers can preview live.
 */
@Composable
fun ColorPicker(
    presets: List<ColorPreset>,
    current: Color,
    onPick: (Color) -> Unit,
    supportingText: String? = null,
) {
    val isPreset = presets.any { it.color.toHex() == current.toHex() }
    var customOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            presets.forEach { preset ->
                ColorSwatch(preset, selected = preset.color.toHex() == current.toHex()) { onPick(preset.color) }
            }
            CustomSwatch(selected = !isPreset, open = customOpen, current = current) { customOpen = !customOpen }
        }
        supportingText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = customOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            RgbPicker(current, onPick, Modifier.widthIn(max = 420.dp))
        }
    }
}

/** Saturation/brightness square, hue bar and hex field. Keeps its own hue so it survives greys and black. */
@Composable
private fun RgbPicker(color: Color, onChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    var hue by remember { mutableStateOf(color.toHsv()[0]) }
    var saturation by remember { mutableStateOf(color.toHsv()[1]) }
    var value by remember { mutableStateOf(color.toHsv()[2]) }
    val picked = Color.hsv(hue, saturation, value)
    // A preset or typed hex changed the colour from outside: follow it.
    LaunchedEffect(color) {
        if (picked.toHex() != color.toHex()) {
            val hsv = color.toHsv()
            if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
        }
    }
    val emit = { onChange(Color.hsv(hue, saturation, value)) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SaturationValuePanel(hue, saturation, value) { s, v ->
            saturation = s
            value = v
            emit()
        }
        HueBar(hue) {
            hue = it
            emit()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HexField(color, onChange, Modifier.width(200.dp))
            Box(
                Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
            )
        }
    }
}

/** White → hue left to right, darkening to black downwards. */
@Composable
private fun SaturationValuePanel(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics { contentDescription = "Saturation and brightness" }
            .pointerInput(Unit) {
                trackDrag { position, size ->
                    latest((position.x / size.width).coerceIn(0f, 1f), 1f - (position.y / size.height).coerceIn(0f, 1f))
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 10.dp.toPx(), center = center, style = Stroke(width = 4.dp.toPx()))
        drawCircle(Color.White, radius = 10.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
    }
}

/** The colour wheel's hues laid out in a bar. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    val rainbow = remember { (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) } }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics { contentDescription = "Hue" }
            .pointerInput(Unit) {
                trackDrag { position, size -> latest((position.x / size.width).coerceIn(0f, 1f) * 359.9f) }
            },
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Brush.horizontalGradient(rainbow), cornerRadius = radius)
        val center = Offset(hue / 360f * size.width, size.height / 2)
        drawCircle(Color.hsv(hue, 1f, 1f), radius = size.height / 2 + 2.dp.toPx(), center = center)
        drawCircle(Color.White, radius = size.height / 2 + 2.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = size.height / 2 + 3.5.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
    }
}

/** Reports the pointer position on press and while dragging. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackDrag(onPosition: (Offset, IntSize) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPosition(down.position, size)
        drag(down.id) { change ->
            change.consume()
            onPosition(change.position, size)
        }
    }
}

/** #RRGGBB text that applies each valid entry and follows changes made elsewhere. */
@Composable
private fun HexField(color: Color, onChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(color.toHex()) }
    LaunchedEffect(color) {
        if (parseHexColor(text)?.toHex() != color.toHex()) text = color.toHex()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it == '#' || it.lowercaseChar() in "0123456789abcdef" }.take(7)
            parseHexColor(text)?.let(onChange)
        },
        label = { Text("Hex") },
        placeholder = { Text("#RRGGBB") },
        isError = parseHexColor(text) == null,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun ColorSwatch(preset: ColorPreset, selected: Boolean, onClick: () -> Unit) {
    WithTooltip(preset.name) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(preset.color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = contentColorFor(preset.color), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Rainbow circle that opens the RGB picker; filled with the custom colour once one is chosen. */
@Composable
private fun CustomSwatch(selected: Boolean, open: Boolean, current: Color, onClick: () -> Unit) {
    val rainbow = remember { Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.85f, 1f) }) }
    WithTooltip(if (open) "Hide custom colour" else "Custom colour") {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(current) else Modifier.background(rainbow))
                .border(
                    width = if (selected || open) 3.dp else 1.dp,
                    color = if (selected || open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) Icons.Default.Check else Icons.Outlined.Palette,
                contentDescription = null,
                tint = if (selected) contentColorFor(current) else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Hue (0..360), saturation and value (0..1). */
internal fun Color.toHsv(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
}
