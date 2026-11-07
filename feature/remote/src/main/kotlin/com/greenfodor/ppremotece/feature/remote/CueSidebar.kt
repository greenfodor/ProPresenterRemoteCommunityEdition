package com.greenfodor.ppremotece.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.ui.CueCell
import com.greenfodor.ppremotece.core.domain.model.CueSource
import kotlinx.coroutines.flow.first

val CueSidebarWidth = 280.dp

/**
 * One column of the [cues] of [source]. On every change of [source] or [focus] it scrolls so that the cue at [focus] is
 * centred, held within the list's bounds, except when the change reaches the cue the user tapped in the list last
 * ([scrollsOnChange]); the first scroll of a new column is immediate, later ones animate.
 */
@Composable
internal fun CueSidebar(
    cues: List<SidebarCueUi>,
    source: CueSource?,
    focus: Int?,
    aspect: Float,
    onCueClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var ownTap by remember { mutableStateOf<Int?>(null) }
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(source, focus) {
        val tapped = ownTap
        ownTap = null
        val index = focus?.takeIf { scrollsOnChange(it, tapped) } ?: return@LaunchedEffect
        val layout = snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: layout.visibleItemsInfo.first()
        val offset = centringOffset(layout.viewportStartOffset, layout.viewportEndOffset, item.size)
        if (scrolled) listState.animateScrollToItem(index, offset) else listState.scrollToItem(index, offset)
        scrolled = true
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxHeight()
    ) {
        items(cues, key = { it.cue.index }) { row ->
            CueCell(
                number = row.cue.index + 1,
                groupName = row.cue.groupName,
                groupColor = row.cue.groupColor,
                fallbackText = row.cue.slideText.ifBlank { row.cue.groupName },
                aspect = aspect,
                onClick = {
                    ownTap = row.cue.index
                    onCueClick(row.cue.index)
                },
                thumbnail = row.thumbnail,
                label = row.cue.slideLabel,
                enabled = row.cue.enabled,
                mark = row.mark.toCueMark(),
                showGroupName = row.cue.startsGroup
            )
        }
    }
}
