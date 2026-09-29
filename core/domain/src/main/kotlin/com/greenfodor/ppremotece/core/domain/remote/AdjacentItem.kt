package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType

enum class ItemDirection {
    PREVIOUS,
    NEXT
}

/**
 * The nearest item before or after [from] in [playlist] that can go live (a presentation, media,
 * audio or live-video item); headers, placeholders and unknown items are skipped. Null at the ends.
 */
fun adjacentItem(playlist: Playlist, from: PlaylistItemKey, direction: ItemDirection): PlaylistItemKey? {
    val candidates = when (direction) {
        ItemDirection.PREVIOUS -> playlist.items.filter { it.key.index < from.index }.asReversed()
        ItemDirection.NEXT -> playlist.items.filter { it.key.index > from.index }
    }
    return candidates.firstOrNull { it.type in LIVE_TYPES }?.key
}

private val LIVE_TYPES = setOf(
    PlaylistItemType.PRESENTATION,
    PlaylistItemType.MEDIA,
    PlaylistItemType.AUDIO,
    PlaylistItemType.LIVE_VIDEO
)
