package fi.severi.overtime.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Shows the platform's left-right resize cursor while hovering. */
expect fun Modifier.horizontalResizeCursor(): Modifier

/**
 * Vertical drag handle between two panes, after the M3 pane-expansion handle: a slim pill that grows and
 * brightens on hover and while dragging. [onDrag] gets horizontal deltas in pixels; double-click resets.
 */
@Composable
fun PaneDragHandle(
    onDrag: (Float) -> Unit,
    onDragStopped: () -> Unit,
    onReset: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = hovered || dragged
    val pillColor by animateColorAsState(
        when {
            dragged -> MaterialTheme.colorScheme.primary
            hovered -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.outlineVariant
        },
    )
    val pillWidth by animateDpAsState(if (active) 6.dp else 4.dp)
    val pillHeight by animateDpAsState(if (active) 64.dp else 48.dp)

    Box(
        modifier
            .width(12.dp)
            .fillMaxHeight()
            .background(background)
            .hoverable(interaction)
            .horizontalResizeCursor()
            .draggable(
                state = rememberDraggableState(onDrag),
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
                onDragStopped = { onDragStopped() },
            )
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(pillWidth).height(pillHeight).background(pillColor, CircleShape))
    }
}
