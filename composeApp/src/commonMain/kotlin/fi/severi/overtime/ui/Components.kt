package fi.severi.overtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The one volume bar used everywhere (player toolbar and the volume popup), so both look identical:
 * the M3 slider with its handle and stop indicator. Muted dims the active track.
 */
@Composable
fun VolumeSlider(volume: Int, muted: Boolean, onVolumeChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Slider(
        value = volume.toFloat(),
        onValueChange = { onVolumeChange(it.toInt()) },
        valueRange = 0f..100f,
        colors = if (muted) {
            SliderDefaults.colors(
                thumbColor = scheme.outline,
                activeTrackColor = scheme.outline,
                inactiveTrackColor = scheme.surfaceContainerHighest,
            )
        } else {
            SliderDefaults.colors(inactiveTrackColor = scheme.surfaceContainerHighest)
        },
        modifier = modifier.semantics { role = Role.Button },
    )
}

/**
 * M3 Expressive connected button group used as a single-choice selector: the checked item morphs
 * to a full pill and shows a check, like the "4 oz / 8 oz / 12 oz" group.
 */
@Composable
fun <T> ConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                // Unchecked items use the tonal container (like the reference's light segments) so they
                // stay visible on the dialog's high container tone; checked is the accent.
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                if (checked) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                }
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Pill-shaped filled search field (M3 search bar look) with a clear button. */
@Composable
fun PillSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Poster / backdrop image drawn as sharply as the source allows: decoded at its full size (not
 * pre-shrunk to the card) and scaled on the GPU with high-quality, mipmapped filtering, so small text
 * on rated artwork (rating strips, badges) stays legible.
 */
@Composable
fun Artwork(url: String, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalPlatformContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).size(CoilSize.ORIGINAL).build() }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        filterQuality = FilterQuality.High,
        modifier = modifier,
    )
}

/** Wraps [content] in a plain M3 tooltip. */
@Composable
fun WithTooltip(text: String, above: Boolean = true, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (above) TooltipAnchorPosition.Above else TooltipAnchorPosition.Below,
        ),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/**
 * Preset colour circles plus a custom #RRGGBB field. [onPick] fires for every valid choice so callers
 * can preview live. The selected circle gets an accent ring and a check.
 */
@Composable
fun ColorPicker(
    presets: List<ColorPreset>,
    current: Color,
    onPick: (Color) -> Unit,
    supportingText: String? = null,
) {
    var text by remember { mutableStateOf(current.toHex()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            presets.forEach { preset ->
                ColorSwatch(preset, selected = preset.color == current) {
                    text = preset.color.toHex()
                    onPick(preset.color)
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it == '#' || it.lowercaseChar() in "0123456789abcdef" }.take(7)
                parseHexColor(text)?.let(onPick)
            },
            label = { Text("Custom colour") },
            placeholder = { Text("#RRGGBB") },
            isError = parseHexColor(text) == null,
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.width(340.dp),
        )
    }
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
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColorFor(preset.color),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
