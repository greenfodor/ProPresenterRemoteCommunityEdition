package com.greenfodor.ppremotece.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.ui.CueCell
import com.greenfodor.ppremotece.core.domain.model.CueSource

val CueSidebarWidth = 280.dp

/** One column of the [cues] of [source]; it scrolls so that the cue at [focus] is fully visible whenever [source] or [focus] changes. */
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
    LaunchedEffect(source, focus, cues.size) {
        val index = focus ?: return@LaunchedEffect
        val layout = listState.layoutInfo
        val shown = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyShown = shown != null &&
            shown.offset >= layout.viewportStartOffset &&
            shown.offset + shown.size <= layout.viewportEndOffset
        if (!fullyShown) listState.animateScrollToItem(index)
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
                onClick = { onCueClick(row.cue.index) },
                thumbnail = row.thumbnail,
                label = row.cue.slideLabel,
                enabled = row.cue.enabled,
                mark = row.mark.toCueMark(),
                showGroupName = row.cue.startsGroup
            )
        }
    }
}
