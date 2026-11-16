package fi.severi.overtime.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Arrows at the ends of a horizontal row, shown while there's more in that direction; each click slides
 * the row by most of its width. A mouse has no sideways wheel, so without these the cards past the edge
 * of a narrow window were out of reach.
 */
@Composable
internal fun BoxScope.RowScrollArrows(state: ScrollableState, viewportWidth: () -> Int, size: Dp = 40.dp) {
    val scope = rememberCoroutineScope()
    fun page(direction: Int) {
        scope.launch { state.animateScrollBy(direction * viewportWidth() * PAGE_FRACTION) }
    }
    if (state.canScrollBackward) {
        RowArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Scroll left", size, Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) { page(-1) }
    }
    if (state.canScrollForward) {
        RowArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Scroll right", size, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { page(1) }
    }
}

@Composable
private fun RowArrow(icon: ImageVector, description: String, size: Dp, modifier: Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = scheme.surfaceContainerHighest.copy(alpha = if (hovered) 0.95f else 0.75f),
            contentColor = scheme.onSurface,
        ),
        modifier = modifier.size(size).hoverable(hover),
    ) {
        Icon(icon, contentDescription = description)
    }
}

private const val PAGE_FRACTION = 0.8f
