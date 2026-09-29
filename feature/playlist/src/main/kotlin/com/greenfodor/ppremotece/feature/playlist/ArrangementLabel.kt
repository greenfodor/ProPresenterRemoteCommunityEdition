package com.greenfodor.ppremotece.feature.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice

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
