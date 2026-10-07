package com.greenfodor.ppremotece.core.domain.transport

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Transport
import kotlinx.coroutines.flow.StateFlow

/**
 * What the connected host's presentation and audio transport layers have loaded, from the status
 * stream: [Loadable.NotLoaded] until the first frame and while disconnected, and
 * [Loadable.Unavailable] when ProPresenter rejected the subscription that feeds it.
 */
interface TransportRepository {
    val presentationTransport: StateFlow<Loadable<Transport>>
    val audioTransport: StateFlow<Loadable<Transport>>
}

/** How a transport holds a media or audio playlist item. */
enum class ItemLive {
    NONE,
    LIVE,
    PAUSED
}

/**
 * How [item] is held by its transport: a media item by the [presentation] transport and an audio
 * item by the [audio] transport, [ItemLive.LIVE] while that transport names the item's target and
 * plays, [ItemLive.PAUSED] while it names it and does not play. Any other item is never held.
 */
fun itemLive(item: PlaylistItem, presentation: Transport?, audio: Transport?): ItemLive {
    val transport = when (item.type) {
        PlaylistItemType.MEDIA -> presentation
        PlaylistItemType.AUDIO -> audio
        else -> null
    }
    return when {
        transport == null || item.targetUuid.isNullOrEmpty() || transport.uuid != item.targetUuid -> ItemLive.NONE
        transport.isPlaying -> ItemLive.LIVE
        else -> ItemLive.PAUSED
    }
}
