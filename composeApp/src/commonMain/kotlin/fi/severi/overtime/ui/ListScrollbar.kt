package fi.severi.overtime.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Lists longer than this get a visible scrollbar. */
internal const val SCROLLBAR_MIN_ITEMS = 30

/** A thin scrollbar for [state], shown only once the list has more than [SCROLLBAR_MIN_ITEMS] items. */
@Composable
internal expect fun ListScrollbar(state: LazyListState, modifier: Modifier = Modifier)
