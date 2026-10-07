package com.greenfodor.ppremotece.feature.playlist.items

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.transport.ItemLive

/**
 * A media, audio or live-video playlist item's card: its [name], [type] (null until it is read),
 * its [duration] as text when it has one, and whether its transport plays it or holds it paused
 * ([live]).
 */
data class PlaylistItemState(
    val name: String = "",
    val type: PlaylistItemType? = null,
    val duration: String? = null,
    val live: ItemLive = ItemLive.NONE,
    val isLoading: Boolean = true,
    val error: UiText? = null
)

sealed interface PlaylistItemAction {
    data object OnCardClick : PlaylistItemAction

    data object OnRetryClick : PlaylistItemAction
}
