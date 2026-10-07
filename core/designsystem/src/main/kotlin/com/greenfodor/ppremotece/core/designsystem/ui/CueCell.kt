package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest

private val FrameWidth = 4.dp
private val LabelStripHeight = 28.dp
private val BadgeIconSize = 16.dp
private const val PREVIEW_ASPECT = 1920f / 858f

/** The state a cue cell is marked with: a ring and a badge, or nothing. */
enum class CueMark {
    NONE,
    LIVE,
    NEXT,
    CUED,
    CLEARED
}

/**
 * One cue: a reserved ring slot (LIVE 4 dp `tertiary`, NEXT, CUED and CLEARED 2 dp `secondary`), a
 * 4 dp frame in the group colour, the slide image with its badges at the top, and a 28 dp label
 * strip with "[number]. [groupName]" ("[number]." unless [showGroupName]) and the slide [label]. A
 * disabled cue is dimmed, badged and not clickable; with a null [onClick] the cell is not clickable.
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
    thumbnailGeneration: Int = 0,
    showGroupName: Boolean = true
) {
    val groupColors = PPRemoteTheme.groupColors
    val frameColor = groupColor?.takeIf { it.alpha > 0f }?.toColor() ?: MaterialTheme.colorScheme.outlineVariant
    CueMarkFrame(mark = mark, enabled = enabled, onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(CueContentCorner))
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
                        text = cueLabel(number, groupName, showGroupName),
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

/** "[number]. [groupName]", or "[number]." unless [showGroupName]. */
@Composable
internal fun cueLabel(number: Int, groupName: String, showGroupName: Boolean): String =
    if (showGroupName) {
        stringResource(R.string.cue_label, number, groupName)
    } else {
        stringResource(R.string.cue_number, number)
    }

/** The `LIVE`, `NEXT`, `CUED` or `CLEARED` badge of [mark]; nothing for [CueMark.NONE]. */
@Composable
fun CueMarkBadge(mark: CueMark, modifier: Modifier = Modifier) {
    when (mark) {
        CueMark.LIVE -> LiveBadge(modifier)
        CueMark.NEXT -> Badge(
            text = stringResource(R.string.cue_next),
            container = MaterialTheme.colorScheme.secondary,
            content = MaterialTheme.colorScheme.onSecondary,
            modifier = modifier
        )
        CueMark.CUED -> OutlinedBadge(stringResource(R.string.cue_cued), modifier)
        CueMark.CLEARED -> OutlinedBadge(stringResource(R.string.cue_cleared), modifier)
        CueMark.NONE -> Unit
    }
}

/** A badge in the `CUED` look, reading [text]: `secondary` text and outline on the darkest surface. */
@Composable
fun OutlinedBadge(text: String, modifier: Modifier = Modifier) {
    Badge(
        text = text,
        container = MaterialTheme.colorScheme.surfaceContainerLowest,
        content = MaterialTheme.colorScheme.secondary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        modifier = modifier
    )
}

/** The `LIVE` badge, reading [text]: `onTertiary` text on `tertiary`. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, text: String = stringResource(R.string.cue_live)) {
    Badge(
        text = text,
        container = MaterialTheme.colorScheme.tertiary,
        content = MaterialTheme.colorScheme.onTertiary,
        modifier = modifier
    )
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
                CueCell(
                    number = 3,
                    groupName = "Verse 1",
                    groupColor = null,
                    fallbackText = "Verse 1 · 3",
                    aspect = PREVIEW_ASPECT,
                    onClick = {},
                    showGroupName = false
                )
            }
        }
    }
}
