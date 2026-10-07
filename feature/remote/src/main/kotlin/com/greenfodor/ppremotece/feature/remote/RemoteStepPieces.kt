package com.greenfodor.ppremotece.feature.remote

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.ui.ArrangementChip
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

/** The Next button of the Remote, enabled while [display] has a next cue to send. */
@Composable
internal fun NextButton(display: RemoteDisplay, onAction: (RemoteAction) -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = { onAction(RemoteAction.OnNextClick) },
        enabled = display.nextButton != null,
        modifier = modifier
    ) {
        Text(stringResource(R.string.remote_next), modifier = Modifier.padding(end = 8.dp))
        Icon(painterResource(DesignR.drawable.ic_arrow_forward), contentDescription = null)
    }
}

/** The Previous button of the Remote, enabled while [display] has a previous cue to send. */
@Composable
internal fun PreviousButton(display: RemoteDisplay, onAction: (RemoteAction) -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = { onAction(RemoteAction.OnPreviousClick) },
        enabled = display.previousButton != null,
        modifier = modifier
    ) {
        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = null)
        Text(stringResource(R.string.remote_previous), modifier = Modifier.padding(start = 8.dp))
    }
}

/** The previous-item button, enabled while [display] has an item before the shown one. */
@Composable
internal fun PreviousItemButton(display: RemoteDisplay, onAction: (RemoteAction) -> Unit) {
    IconButton(
        onClick = { onAction(RemoteAction.OnPreviousItemClick) },
        enabled = display.previousItem != null
    ) {
        Icon(painterResource(DesignR.drawable.ic_skip_previous), stringResource(R.string.remote_previous_item))
    }
}

/** The next-item button, enabled while [display] has an item after the shown one. */
@Composable
internal fun NextItemButton(display: RemoteDisplay, onAction: (RemoteAction) -> Unit) {
    IconButton(
        onClick = { onAction(RemoteAction.OnNextItemClick) },
        enabled = display.nextItem != null
    ) {
        Icon(painterResource(DesignR.drawable.ic_skip_next), stringResource(R.string.remote_next_item))
    }
}

/** The "Back to live" chip shown while an item is cued. */
@Composable
internal fun BackToLiveChip(onAction: (RemoteAction) -> Unit) {
    AssistChip(
        onClick = { onAction(RemoteAction.OnBackToLiveClick) },
        label = { Text(stringResource(R.string.remote_back_to_live)) }
    )
}

/**
 * The next item's name on up to [maxLines] lines, then its arrangement chip; "End of playlist" at
 * the last item. The parts are placed by the row or column that calls this.
 */
@Composable
internal fun NextUpLabel(display: RemoteDisplay, maxLines: Int, nameModifier: Modifier = Modifier) {
    val nextUp = display.nextUp
    when {
        nextUp != null -> {
            Text(
                text = stringResource(R.string.remote_next_up, nextUp.name),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = nameModifier
            )
            nextUp.arrangement?.let { ArrangementChip(text = it.label()) }
        }
        display.endOfPlaylist -> Text(
            text = stringResource(R.string.remote_end_of_playlist),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The arrangement's name, "Unnamed arrangement" for an empty one and nothing for the song order. */
@Composable
internal fun ArrangementChoice.label(): String =
    when (this) {
        is ArrangementChoice.Resolved -> arrangement.name.ifEmpty {
            stringResource(R.string.remote_unnamed_arrangement)
        }
        ArrangementChoice.SongOrder -> ""
    }
