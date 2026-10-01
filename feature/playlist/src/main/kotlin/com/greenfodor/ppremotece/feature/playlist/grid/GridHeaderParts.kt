package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.theme.LocalGroupColors
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.toColor
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementBanner
import com.greenfodor.ppremotece.core.domain.arrangement.GroupPill
import com.greenfodor.ppremotece.core.domain.arrangement.GroupSequence
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val PillHeight = 32.dp
private val PillTouchHeight = 48.dp
private val LiveRingWidth = 2.dp

/**
 * One 32 dp pill in a 48 dp tall slot per group occurrence of [sequence], filled with the group colour; the live pill has
 * a 2 dp `tertiary` ring and is scrolled into view. A tap reports the occurrence's first cue.
 */
@Composable
internal fun GroupStrip(
    sequence: GroupSequence,
    onPillClick: (firstCueIndex: Int) -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier
) {
    val rowState = rememberLazyListState()
    LaunchedEffect(sequence.livePill) {
        val live = sequence.livePill ?: return@LaunchedEffect
        val layout = rowState.layoutInfo
        val shown = layout.visibleItemsInfo.firstOrNull { it.index == live }
        val fullyShown = shown != null &&
            shown.offset >= layout.viewportStartOffset &&
            shown.offset + shown.size <= layout.viewportEndOffset
        if (!fullyShown) rowState.animateScrollToItem(live)
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(sequence.pills) { index, pill ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(PillTouchHeight)) {
                GroupPillChip(
                    pill = pill,
                    live = index == sequence.livePill,
                    onClick = { onPillClick(pill.firstCueIndex) }
                )
            }
        }
    }
}

/** A 32 dp group pill, its name in an automatic text colour. */
@Composable
private fun GroupPillChip(pill: GroupPill, live: Boolean, onClick: () -> Unit) {
    val fill = pill.color?.toColor() ?: MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = fill,
        contentColor = LocalGroupColors.current.labelOn(fill),
        border = if (live) BorderStroke(LiveRingWidth, MaterialTheme.colorScheme.tertiary) else null,
        modifier = Modifier.height(PillHeight).semantics { selected = live }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(text = pill.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** The `tertiaryContainer` "Live in …" banner with its Re-sync action. */
@Composable
internal fun ArrangementBannerBar(
    banner: ArrangementBanner,
    onResync: () -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = horizontalPadding + 4.dp, end = horizontalPadding)
        ) {
            Text(
                text = bannerText(banner),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            TextButton(
                onClick = onResync,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_sync),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.grid_resync))
            }
        }
    }
}

@Composable
private fun bannerText(banner: ArrangementBanner): String {
    val count = banner.totalCues
    val name = banner.arrangementName
    return when {
        banner.isSongOrder -> pluralStringResource(R.plurals.grid_banner_song_order, count, count)
        name != null -> pluralStringResource(R.plurals.grid_banner_named, count, name, count)
        else -> pluralStringResource(R.plurals.grid_banner_other, count, count)
    }
}

private val PreviewChorus = GroupColor(red = 0.2f, green = 0.4f, blue = 0.8f, alpha = 1f)
private val PreviewPills = listOf(
    GroupPill("v", "Verse 1", null, firstCueIndex = 0, occurrence = 0),
    GroupPill("c", "Chorus", PreviewChorus, firstCueIndex = 2, occurrence = 0),
    GroupPill("v", "Verse 1", null, firstCueIndex = 4, occurrence = 1),
    GroupPill("c", "Chorus", PreviewChorus, firstCueIndex = 6, occurrence = 1),
    GroupPill("b", "Bridge", null, firstCueIndex = 8, occurrence = 0)
)

@Preview(widthDp = 411)
@Composable
private fun GroupStripPreview() {
    PPRemoteTheme {
        Surface { GroupStrip(GroupSequence(PreviewPills, livePill = null), onPillClick = {}, horizontalPadding = 8.dp) }
    }
}

@Preview(widthDp = 411)
@Composable
private fun GroupStripLivePreview() {
    PPRemoteTheme {
        Surface { GroupStrip(GroupSequence(PreviewPills, livePill = 3), onPillClick = {}, horizontalPadding = 8.dp) }
    }
}

@Preview(widthDp = 411)
@Composable
private fun ArrangementBannerNamedPreview() {
    PPRemoteTheme {
        ArrangementBannerBar(ArrangementBanner("Short", isSongOrder = false, totalCues = 7), {
        }, horizontalPadding = 8.dp)
    }
}

@Preview(widthDp = 411)
@Composable
private fun ArrangementBannerSongOrderPreview() {
    PPRemoteTheme {
        ArrangementBannerBar(ArrangementBanner(null, isSongOrder = true, totalCues = 15), {}, horizontalPadding = 8.dp)
    }
}

@Preview(widthDp = 411)
@Composable
private fun ArrangementBannerGenericPreview() {
    PPRemoteTheme {
        ArrangementBannerBar(ArrangementBanner(null, isSongOrder = false, totalCues = 9), {}, horizontalPadding = 8.dp)
    }
}
