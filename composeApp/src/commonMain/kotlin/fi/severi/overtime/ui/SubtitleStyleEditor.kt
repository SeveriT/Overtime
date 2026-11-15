package fi.severi.overtime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.severi.overtime.model.SubtitleEdge
import fi.severi.overtime.model.SubtitleFont
import fi.severi.overtime.model.SubtitleSize
import fi.severi.overtime.model.SubtitleStyle

/** An installed font by family name, for previewing what VLC will draw. */
internal expect fun systemFontFamily(name: String): FontFamily

private val SubtitleColors = listOf(
    ColorPreset("White", Color(0xFFFFFFFF)),
    ColorPreset("Yellow", Color(0xFFFFDE00)),
    ColorPreset("Light grey", Color(0xFFD0D0D0)),
    ColorPreset("Cyan", Color(0xFF7FE7FF)),
    ColorPreset("Green", Color(0xFFA8F0A0)),
)

/** Settings rows for the subtitle look, with a live preview on top. */
@Composable
internal fun SubtitleStyleEditor(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit) {
    SubtitlePreview(style)
    FieldLabel("Font")
    ChipChoice(SubtitleFont.entries, style.font, { it.label }) { onChange(style.copy(font = it)) }
    FieldLabel("Size")
    ChipChoice(SubtitleSize.entries, style.size, { it.label }) { onChange(style.copy(size = it)) }
    FieldLabel("Edge")
    ConnectedChoiceGroup(SubtitleEdge.entries, style.edge, { it.label }, { onChange(style.copy(edge = it)) })
    FieldLabel("Text colour")
    ColorPicker(
        presets = SubtitleColors,
        current = parseHexColor(style.color) ?: Color.White,
        onPick = { onChange(style.copy(color = it.toHex())) },
        supportingText = "Applies to plain subtitles (SRT and the like); styled ASS subtitles keep their own look.",
    )
    SwitchRow(
        title = "Bold",
        supporting = "Heavier letters, easier to read on busy pictures.",
        checked = style.bold,
        onCheckedChange = { onChange(style.copy(bold = it)) },
    )
}

/** A stand-in video frame with a sample line drawn roughly the way VLC will draw it. */
@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    val family = remember(style.font) { systemFontFamily(style.font.family) }
    val textStyle = TextStyle(
        fontFamily = family,
        fontSize = (20f * style.size.percent / 100f).sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        color = parseHexColor(style.color) ?: Color.White,
        shadow = when (style.edge) {
            SubtitleEdge.SHADOW -> Shadow(Color.Black.copy(alpha = 0.9f), Offset(2.5f, 2.5f), blurRadius = 3f)
            SubtitleEdge.OUTLINE -> Shadow(Color.Black.copy(alpha = 0.5f), Offset(1.5f, 1.5f), blurRadius = 2f)
            SubtitleEdge.BOX -> null
        },
    )
    val sample = "What a strike from the edge of the box!"
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.large)
            // A pitch under a bright sky: light and dark areas, like a real frame.
            .background(Brush.verticalGradient(listOf(Color(0xFFB8D4EA), Color(0xFF6E8FA8), Color(0xFF2F6B3A), Color(0xFF1E4A27)))),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "Preview",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        val box = if (style.edge == SubtitleEdge.BOX) {
            Modifier.background(Color.Black.copy(alpha = 0.69f), RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        } else {
            Modifier
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).then(box)) {
            if (style.edge == SubtitleEdge.OUTLINE) {
                Text(sample, style = textStyle.copy(color = Color.Black, shadow = null, drawStyle = Stroke(width = 4f, join = StrokeJoin.Round)))
            }
            Text(sample, style = textStyle)
        }
    }
}
