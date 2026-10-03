package com.greenfodor.ppremotece.core.domain.transport

import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Transport
import kotlinx.coroutines.flow.StateFlow

/**
 * What the connected host's presentation and audio transport layers have loaded, from the status
 * stream; null until the first frame and while disconnected.
 */
interface TransportRepository {
    val presentationTransport: StateFlow<Transport?>
    val audioTransport: StateFlow<Transport?>
}

/**
 * Whether [item] is live: a media item while the [presentation] transport plays its target, an
 * audio item while the [audio] transport plays its target, any other item never.
 */
fun itemLive(item: PlaylistItem, presentation: Transport?, audio: Transport?): Boolean {
    val transport = when (item.type) {
        PlaylistItemType.MEDIA -> presentation
        PlaylistItemType.AUDIO -> audio
        else -> null
    }
    return transport != null &&
        transport.isPlaying &&
        !item.targetUuid.isNullOrEmpty() &&
        transport.uuid == item.targetUuid
}
