package com.greenfodor.ppremotece.feature.playlist.items

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType

/**
 * A media, audio or live-video playlist item's card: its [name], [type] (null until it is read),
 * its [duration] as text when it has one, and whether it is [live].
 */
data class PlaylistItemState(
    val name: String = "",
    val type: PlaylistItemType? = null,
    val duration: String? = null,
    val live: Boolean = false,
    val isLoading: Boolean = true,
    val error: UiText? = null
)

sealed interface PlaylistItemAction {
    data object OnCardClick : PlaylistItemAction

    data object OnRetryClick : PlaylistItemAction
}
