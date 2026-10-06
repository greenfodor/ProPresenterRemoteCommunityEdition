package com.greenfodor.ppremotece.feature.playlist.items

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel

/** A playlist's [name] and its items as [rows]. */
data class PlaylistState(
    val name: String = "",
    val rows: List<PlaylistRowUi> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
)

/** The item types that open the item screen. */
internal val ItemScreenTypes = setOf(PlaylistItemType.MEDIA, PlaylistItemType.AUDIO, PlaylistItemType.LIVE_VIDEO)

/** What a tap on an item row opens. */
enum class RowTarget {
    SLIDES,
    ITEM,
    NONE
}

sealed interface PlaylistRowUi {
    val id: String
    val name: String

    /** A header row, filled with [color] when the header has one. */
    data class Header(
        override val id: String,
        override val name: String,
        val color: GroupColor?
    ) : PlaylistRowUi

    /** An item row with its arrangement [label]; a tap opens [opens]. */
    data class Item(
        override val id: String,
        override val name: String,
        val key: PlaylistItemKey,
        val type: PlaylistItemType,
        val label: ArrangementLabel?,
        val opens: RowTarget
    ) : PlaylistRowUi
}

sealed interface PlaylistAction {
    data class OnItemClick(
        val key: PlaylistItemKey
    ) : PlaylistAction

    data object OnRetryClick : PlaylistAction

    data object OnRefresh : PlaylistAction
}

sealed interface PlaylistEvent {
    data class OpenSlides(
        val key: PlaylistItemKey
    ) : PlaylistEvent

    data class OpenItem(
        val key: PlaylistItemKey
    ) : PlaylistEvent

    data class ShowError(
        val message: UiText
    ) : PlaylistEvent
}
