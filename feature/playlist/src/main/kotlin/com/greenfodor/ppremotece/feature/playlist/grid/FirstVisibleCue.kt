package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

/** The lazy item index of cue [cueIndex], or of the first item when it is the first cue or not found. */
internal fun itemIndexOf(cueIndex: Int, cues: List<CueUi>, headerItems: Int): Int =
    cues.indexOfFirst { it.index == cueIndex }.takeIf { it > 0 }?.plus(headerItems) ?: 0

/** Scrolls with [scrollToItem] to the lazy item of each cue index that [requests] emits. */
@Composable
internal fun ScrollToCueRequests(
    requests: Flow<Int>,
    cues: List<CueUi>,
    headerItems: Int,
    scrollToItem: suspend (Int) -> Unit
) {
    val currentCues by rememberUpdatedState(cues)
    val currentScrollToItem by rememberUpdatedState(scrollToItem)
    LaunchedEffect(requests, headerItems) {
        requests.collect { cueIndex -> currentScrollToItem(itemIndexOf(cueIndex, currentCues, headerItems)) }
    }
}

/**
 * While [isScrolling] and when scrolling stops, reports the cue of the first visible lazy item as
 * [SlideGridAction.OnFirstVisibleCueChange].
 */
@Composable
internal fun ReportFirstVisibleCue(
    firstVisibleItem: () -> Int,
    isScrolling: () -> Boolean,
    cues: List<CueUi>,
    headerItems: Int,
    onAction: (SlideGridAction) -> Unit
) {
    val currentCues by rememberUpdatedState(cues)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(headerItems) {
        var wasScrolling = false
        snapshotFlow { firstVisibleItem() to isScrolling() }
            .mapNotNull { (item, scrolling) ->
                val report = scrolling || wasScrolling
                wasScrolling = scrolling
                item.takeIf { report }
            }
            .mapNotNull { item -> currentCues.getOrNull((item - headerItems).coerceAtLeast(0))?.index }
            .distinctUntilChanged()
            .collect { currentOnAction(SlideGridAction.OnFirstVisibleCueChange(it)) }
    }
}
