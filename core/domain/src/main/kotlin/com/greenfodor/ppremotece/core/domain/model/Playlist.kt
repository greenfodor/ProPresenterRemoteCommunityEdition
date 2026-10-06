package com.greenfodor.ppremotece.core.domain.model

data class Playlist(
    val uuid: String,
    val name: String,
    val items: List<PlaylistItem>
)

/** Identifies a playlist item by its playlist uuid and its index within that playlist. */
data class PlaylistItemKey(
    val playlistUuid: String,
    val index: Int
)

/**
 * A playlist item. A header carries its [headerColor] when one is set; a media or audio item
 * carries the uuid of what it plays as [targetUuid] and its [durationSeconds], null without one.
 */
data class PlaylistItem(
    val key: PlaylistItemKey,
    val name: String,
    val type: PlaylistItemType,
    val presentation: PresentationRef?,
    val headerColor: GroupColor? = null,
    val targetUuid: String? = null,
    val durationSeconds: Int? = null
)

enum class PlaylistItemType {
    PRESENTATION,
    PLACEHOLDER,
    HEADER,
    MEDIA,
    AUDIO,
    LIVE_VIDEO,
    OTHER
}

/** The presentation a playlist item plays and the arrangement chosen for that item. */
data class PresentationRef(
    val presentationUuid: String,
    val arrangementUuid: String,
    val arrangementName: String
)
