package com.greenfodor.ppremotece.feature.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.ui.CueCell
import com.greenfodor.ppremotece.core.designsystem.ui.CueMark
import com.greenfodor.ppremotece.core.designsystem.ui.CueMarkBadge
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.remote.BoxMark
import com.greenfodor.ppremotece.core.domain.remote.RemoteBox
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val CellFrame = 24.dp
private val CellChrome = 48.dp
private val CardIconSize = 48.dp
private val CardCorner = 12.dp
private val RingWidthOther = 2.dp
private val RingWidthLive = 4.dp

/** One box, as large as fits in its space at the slide's [aspect]; [onClick] null makes it not clickable. */
@Composable
internal fun LiveBox(
    box: RemoteBox,
    thumbnail: ThumbnailRequest?,
    aspect: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(contentAlignment = Alignment.TopCenter, modifier = modifier) {
        when (box) {
            is RemoteBox.Slide -> CueCell(
                number = box.cue.index + 1,
                groupName = box.cue.groupName,
                groupColor = box.cue.groupColor,
                fallbackText = box.cue.slideText.ifBlank { box.cue.groupName },
                aspect = aspect,
                onClick = onClick,
                modifier = Modifier.width(
                    fittedWidth(maxWidth - CellFrame, maxHeight - CellChrome, aspect) + CellFrame
                ),
                thumbnail = thumbnail,
                label = box.cue.slideLabel,
                enabled = box.cue.enabled,
                mark = box.mark.toCueMark()
            )
            is RemoteBox.Text -> TextBox(
                text = box.text,
                modifier = Modifier.width(fittedWidth(maxWidth, maxHeight, aspect)).aspectRatio(aspect)
            )
            is RemoteBox.ItemCard -> ItemCard(
                card = box,
                onClick = onClick,
                modifier = Modifier.width(fittedWidth(maxWidth, maxHeight, aspect)).aspectRatio(aspect)
            )
            RemoteBox.Empty -> Unit
        }
    }
}

private fun fittedWidth(maxWidth: Dp, maxHeight: Dp, aspect: Float): Dp = minOf(maxWidth, maxHeight * aspect)

@Composable
private fun TextBox(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.clip(RoundedCornerShape(CardCorner)).background(Color.Black).padding(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ItemCard(card: RemoteBox.ItemCard, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CardCorner)
    val ring = when (card.mark) {
        BoxMark.LIVE -> Modifier.border(RingWidthLive, MaterialTheme.colorScheme.tertiary, shape)
        BoxMark.CUED, BoxMark.NEXT -> Modifier.border(RingWidthOther, MaterialTheme.colorScheme.secondary, shape)
        BoxMark.NONE -> Modifier
    }
    Box(
        modifier = modifier
            .then(ring)
            .clip(shape)
            .background(Color.Black)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.Center).padding(16.dp)
        ) {
            Icon(
                painter = painterResource(card.type.icon()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(CardIconSize)
            )
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        CueMarkBadge(mark = card.mark.toCueMark(), modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

internal fun BoxMark.toCueMark(): CueMark =
    when (this) {
        BoxMark.NONE -> CueMark.NONE
        BoxMark.LIVE -> CueMark.LIVE
        BoxMark.NEXT -> CueMark.NEXT
        BoxMark.CUED -> CueMark.CUED
    }

private fun PlaylistItemType.icon(): Int =
    when (this) {
        PlaylistItemType.AUDIO -> DesignR.drawable.ic_music_note
        PlaylistItemType.LIVE_VIDEO -> DesignR.drawable.ic_videocam
        PlaylistItemType.PRESENTATION -> DesignR.drawable.ic_slideshow
        else -> DesignR.drawable.ic_movie
    }
