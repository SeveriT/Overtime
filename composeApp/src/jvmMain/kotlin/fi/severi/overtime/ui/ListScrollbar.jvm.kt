package fi.severi.overtime.ui

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal actual fun ListScrollbar(state: LazyListState, modifier: Modifier) {
    val long by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount > SCROLLBAR_MIN_ITEMS } }
    if (!long) return
    val scheme = MaterialTheme.colorScheme
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.padding(end = 2.dp, top = 4.dp, bottom = 4.dp),
        style = ScrollbarStyle(
            minimalHeight = 48.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(3.dp),
            hoverDurationMillis = 300,
            unhoverColor = scheme.onSurfaceVariant.copy(alpha = 0.3f),
            hoverColor = scheme.onSurfaceVariant.copy(alpha = 0.6f),
        ),
    )
}
