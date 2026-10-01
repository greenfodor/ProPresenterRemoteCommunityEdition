package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.model.GroupColor

private val GroupBarWidth = 6.dp
private val RowMinHeight = 56.dp
private val DisabledIconSize = 16.dp

/**
 * One cue as text: a 6 dp bar in the group colour, "[number]. [groupName]" with its `LIVE`/`NEXT`
 * badge and the slide [label] right-aligned, then the full slide [text] without a line limit (the
 * group name in italics when the text is empty). The ring slot is the grid cell's (LIVE 4 dp
 * `tertiary`, NEXT and CUED 2 dp `secondary`). A disabled cue is dimmed, shows `visibility_off`
 * and is not clickable; with a null [onClick] the row is not clickable.
 */
@Composable
fun CueRow(
    number: Int,
    groupName: String,
    groupColor: GroupColor?,
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    mark: CueMark = CueMark.NONE
) {
    val barColor = groupColor?.takeIf { it.alpha > 0f }?.toColor() ?: MaterialTheme.colorScheme.outlineVariant
    CueMarkFrame(mark = mark, enabled = enabled, onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .clip(RoundedCornerShape(CueContentCorner))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .drawBehind { drawRect(barColor, size = Size(GroupBarWidth.toPx(), size.height)) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = GroupBarWidth + 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val maxLabelWidth = maxWidth / 2
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CueMarkBadge(mark = mark)
                        Text(
                            text = stringResource(R.string.cue_label, number, groupName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = maxLabelWidth)
                            )
                        }
                        if (!enabled) {
                            Icon(
                                painter = painterResource(R.drawable.ic_visibility_off),
                                contentDescription = stringResource(R.string.cue_disabled),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(DisabledIconSize)
                            )
                        }
                    }
                }
                if (text.isBlank()) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private val PreviewChorus = GroupColor(red = 0f, green = 0.47f, blue = 0.8f, alpha = 1f)

@Preview(widthDp = 360)
@Composable
private fun CueRowPlainPreview() {
    PPRemoteTheme {
        CueRow(1, "Verse 1", null, "Verse 1 · 1\nVerse 1 · 1, line 2\nVerse 1 · 1, line 3", onClick = {
        }, label = "Label 01")
    }
}

@Preview(widthDp = 360)
@Composable
private fun CueRowLivePreview() {
    PPRemoteTheme {
        CueRow(2, "Chorus", PreviewChorus, "Chorus · 1\nChorus · 1, line 2", onClick = {}, mark = CueMark.LIVE)
    }
}

@Preview(widthDp = 360)
@Composable
private fun CueRowNextPreview() {
    PPRemoteTheme {
        CueRow(3, "Chorus", PreviewChorus, "Chorus · 2", onClick = {}, mark = CueMark.NEXT)
    }
}

@Preview(widthDp = 360)
@Composable
private fun CueRowDisabledPreview() {
    PPRemoteTheme {
        CueRow(2, "Verse 1", null, "Verse 1 · 2", onClick = {}, enabled = false)
    }
}

@Preview(widthDp = 360)
@Composable
private fun CueRowEmptyTextPreview() {
    PPRemoteTheme {
        CueRow(5, "Bridge", null, "", onClick = {})
    }
}
