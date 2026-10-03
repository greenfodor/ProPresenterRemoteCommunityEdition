package com.greenfodor.ppremotece.feature.playlist.tree

import com.greenfodor.ppremotece.core.designsystem.ui.UiText

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
        val name: String
    ) : TreeRowUi
}

sealed interface PlaylistTreeAction {
    data class OnFolderClick(
        val uuid: String
    ) : PlaylistTreeAction

    data class OnPlaylistClick(
        val uuid: String
    ) : PlaylistTreeAction

    data object OnRetryClick : PlaylistTreeAction

    data object OnRefresh : PlaylistTreeAction
}

sealed interface PlaylistTreeEvent {
    data class OpenPlaylist(
        val uuid: String
    ) : PlaylistTreeEvent

    data class ShowError(
        val message: UiText
    ) : PlaylistTreeEvent
}
