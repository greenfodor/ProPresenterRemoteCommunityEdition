package com.greenfodor.ppremotece.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

/** How a playlist item's arrangement is labelled in its chip. */
sealed interface ArrangementLabel {
    data class Named(
        val name: String
    ) : ArrangementLabel

    data object Unnamed : ArrangementLabel
}

/** The chip label for an item's arrangement; null when the item plays the presentation's own group order. */
fun ArrangementChoice.toArrangementLabel(): ArrangementLabel? =
    when (this) {
        ArrangementChoice.SongOrder -> null
        is ArrangementChoice.Resolved ->
            if (arrangement.name.isEmpty()) ArrangementLabel.Unnamed else ArrangementLabel.Named(arrangement.name)
    }

@Composable
fun ArrangementLabel.text(): String =
    when (this) {
        is ArrangementLabel.Named -> name
        ArrangementLabel.Unnamed -> stringResource(R.string.arrangement_unnamed)
    }

private val ChipIconSize = 16.dp

@Composable
fun ArrangementChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_reorder),
                contentDescription = null,
                modifier = Modifier.size(ChipIconSize)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
