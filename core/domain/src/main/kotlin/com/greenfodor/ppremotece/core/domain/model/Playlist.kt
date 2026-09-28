package com.greenfodor.ppremotece.core.domain.model

data class Playlist(
    val uuid: String,
    val name: String,
    val items: List<PlaylistItem>
)

/** Identifies a playlist item by its playlist and its position; item uuids are not unique within a playlist. */
data class PlaylistItemKey(
    val playlistUuid: String,
    val index: Int
)

data class PlaylistItem(
    val key: PlaylistItemKey,
    val name: String,
    val type: PlaylistItemType,
    val presentation: PresentationRef?
)

enum class PlaylistItemType {
    PRESENTATION,
    HEADER,
    MEDIA,
    OTHER
}

/** The presentation a playlist item plays and the arrangement chosen for that item. */
data class PresentationRef(
    val presentationUuid: String,
    val arrangementUuid: String,
    val arrangementName: String
)
