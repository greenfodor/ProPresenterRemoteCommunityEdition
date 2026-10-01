package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest

private val RingSlot = 4.dp
private val RingGap = 4.dp
private val LiveRingWidth = 4.dp
private val SecondaryRingWidth = 2.dp
private val FrameWidth = 4.dp
private val FrameCorner = 4.dp
private val LabelStripHeight = 28.dp
private val BadgeIconSize = 16.dp
private const val DISABLED_ALPHA = 0.38f
private const val PREVIEW_ASPECT = 1920f / 858f

private val RingShape = RoundedCornerShape(FrameCorner + RingGap + RingSlot)

/** The state a cue cell is marked with: a ring and a badge, or nothing. */
enum class CueMark {
    NONE,
    LIVE,
    NEXT,
    CUED
}

/**
 * One cue: a reserved ring slot (LIVE 4 dp `tertiary`, NEXT and CUED 2 dp `secondary`), a 4 dp
 * frame in the group colour, the slide image with its badges at the top, and a 28 dp label strip
 * with "[number]. [groupName]" and the slide [label]. A disabled cue is dimmed, badged and not clickable;
 * with a null [onClick] the cell is not clickable.
 * A new [thumbnailGeneration] loads the image again.
 */
@Composable
fun CueCell(
    number: Int,
    groupName: String,
    groupColor: GroupColor?,
    fallbackText: String,
    aspect: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    thumbnail: ThumbnailRequest? = null,
    label: String = "",
    enabled: Boolean = true,
    mark: CueMark = CueMark.NONE,
    thumbnailGeneration: Int = 0
) {
    val groupColors = PPRemoteTheme.groupColors
    val frameColor = groupColor?.takeIf { it.alpha > 0f }?.toColor() ?: MaterialTheme.colorScheme.outlineVariant
    val ringModifier = when (mark) {
        CueMark.LIVE -> Modifier.border(LiveRingWidth, MaterialTheme.colorScheme.tertiary, RingShape)
        CueMark.NEXT, CueMark.CUED -> Modifier.border(
            SecondaryRingWidth,
            MaterialTheme.colorScheme.secondary,
            RingShape
        )
        CueMark.NONE -> Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(ringModifier)
            .clip(RingShape)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .semantics { selected = mark == CueMark.LIVE }
            .padding(RingSlot + RingGap)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(FrameCorner))
                .background(frameColor)
                .padding(start = FrameWidth, top = FrameWidth, end = FrameWidth)
        ) {
            Box {
                key(thumbnailGeneration) {
                    SlideThumbnail(
                        url = thumbnail?.url,
                        cacheKey = thumbnail?.cacheKey,
                        placeholderKey = thumbnail?.placeholderKey,
                        aspect = aspect,
                        fallbackText = fallbackText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CueBadges(mark = mark, enabled = enabled)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(LabelStripHeight)) {
                val labelColor = groupColors.labelOn(frameColor)
                val maxLabelWidth = maxWidth / 2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.cue_label, number, groupName),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = maxLabelWidth)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CueBadges(mark: CueMark, enabled: Boolean) {
    CueMarkBadge(mark = mark, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
    if (!enabled) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_visibility_off),
                contentDescription = stringResource(R.string.cue_disabled),
                modifier = Modifier.padding(2.dp).size(BadgeIconSize)
            )
        }
    }
}

/** The `LIVE`, `NEXT` or `CUED` badge of [mark]; nothing for [CueMark.NONE]. */
@Composable
fun CueMarkBadge(mark: CueMark, modifier: Modifier = Modifier) {
    when (mark) {
        CueMark.LIVE -> Badge(
            text = stringResource(R.string.cue_live),
            container = MaterialTheme.colorScheme.tertiary,
            content = MaterialTheme.colorScheme.onTertiary,
            modifier = modifier
        )
        CueMark.NEXT -> Badge(
            text = stringResource(R.string.cue_next),
            container = MaterialTheme.colorScheme.secondary,
            content = MaterialTheme.colorScheme.onSecondary,
            modifier = modifier
        )
        CueMark.CUED -> Badge(
            text = stringResource(R.string.cue_cued),
            container = MaterialTheme.colorScheme.surfaceContainerLowest,
            content = MaterialTheme.colorScheme.secondary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            modifier = modifier
        )
        CueMark.NONE -> Unit
    }
}

@Composable
private fun Badge(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        border = border,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** The group colour as a Compose [Color]. */
fun GroupColor.toColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)

@Preview
@Composable
private fun CueCellMarksPreview() {
    val chorus = GroupColor(red = 0f, green = 0.47f, blue = 0.8f, alpha = 1f)
    PPRemoteTheme {
        SyntheticThumbnails {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(200.dp)) {
                CueMark.entries.forEach { mark ->
                    CueCell(
                        number = 1,
                        groupName = "Chorus",
                        groupColor = chorus,
                        fallbackText = "Chorus · 1",
                        aspect = PREVIEW_ASPECT,
                        onClick = {},
                        thumbnail = ThumbnailRequest(
                            "http://192.0.2.14:60113/v1/playlist/p/0/thumbnail/0?quality=400",
                            "k0"
                        ),
                        mark = mark
                    )
                }
                CueCell(
                    number = 2,
                    groupName = "Verse 1",
                    groupColor = null,
                    fallbackText = "Verse 1 · 2",
                    aspect = PREVIEW_ASPECT,
                    onClick = {},
                    label = "Label 01",
                    enabled = false
                )
            }
        }
    }
}
