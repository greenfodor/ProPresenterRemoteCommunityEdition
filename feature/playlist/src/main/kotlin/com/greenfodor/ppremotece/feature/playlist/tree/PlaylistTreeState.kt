package com.greenfodor.ppremotece.feature.playlist.tree

import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel

/** Which list the Presentation tab's list pane shows. */
enum class ListMode {
    PLAYLISTS,
    LIBRARY
}

data class PlaylistTreeState(
    val rows: List<TreeRowUi> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
)

sealed interface TreeRowUi {
    val id: String
    val depth: Int

    data class Folder(
        override val id: String,
        override val depth: Int,
        val name: String,
        val expanded: Boolean
    ) : TreeRowUi

    data class Playlist(
        override val id: String,
        override val depth: Int,
        val name: String,
        val expanded: Boolean,
        val isLoading: Boolean
    ) : TreeRowUi

    data class Header(
        override val id: String,
        override val depth: Int,
        val name: String
    ) : TreeRowUi

    data class Item(
        override val id: String,
        override val depth: Int,
        val name: String,
        val key: PlaylistItemKey,
        val label: ArrangementLabel?,
        val opensSlides: Boolean
    ) : TreeRowUi
}

sealed interface PlaylistTreeAction {
    data class OnFolderClick(
        val uuid: String
    ) : PlaylistTreeAction

    data class OnPlaylistClick(
        val uuid: String
    ) : PlaylistTreeAction

    data class OnItemClick(
        val key: PlaylistItemKey
    ) : PlaylistTreeAction

    data object OnDisconnectClick : PlaylistTreeAction

    data object OnRetryClick : PlaylistTreeAction

    data object OnRefresh : PlaylistTreeAction
}

sealed interface PlaylistTreeEvent {
    data class OpenItem(
        val key: PlaylistItemKey
    ) : PlaylistTreeEvent

    data class ShowError(
        val message: UiText
    ) : PlaylistTreeEvent

    data object Disconnected : PlaylistTreeEvent
}
