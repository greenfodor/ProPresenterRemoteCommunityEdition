package com.greenfodor.ppremotece.core.domain.props

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.Prop
import com.greenfodor.ppremotece.core.domain.model.PropCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The connected host's prop collections from the status stream; [Loadable.NotLoaded] while disconnected. */
interface PropsRepository {
    val propCollections: StateFlow<Loadable<List<PropCollection>>>
}

/** What a tap on a prop sends. */
enum class PropTap {
    TRIGGER,
    CLEAR
}

/** A tap clears an active [prop] and triggers any other. */
fun propTap(prop: Prop): PropTap = if (prop.isActive) PropTap.CLEAR else PropTap.TRIGGER

private const val WIDTH_STEP = 200
private const val MAX_WIDTH = 600

/** The thumbnail width asked for a tile [px] wide: rounded up to 200, 400 or 600. */
fun propThumbnailWidth(px: Int): Int = ((px + WIDTH_STEP - 1) / WIDTH_STEP * WIDTH_STEP).coerceIn(WIDTH_STEP, MAX_WIDTH)

/** The memory cache key of prop [uuid]'s thumbnail [width] px wide on host [hostName]. */
fun propThumbnailKey(hostName: String, uuid: String, width: Int): String = "prop:$hostName:$uuid:w$width"

/** Where a prop thumbnail is read from and the key it is kept under in memory. */
data class PropThumbnailRequest(
    val url: String,
    val cacheKey: String
)

/** Builds prop thumbnail requests for one connected host. */
fun interface PropThumbnailRequests {
    /** Prop [uuid]'s thumbnail for a tile [px] wide ([propThumbnailWidth]). */
    fun request(uuid: String, px: Int): PropThumbnailRequest
}

/** Prop thumbnail requests of the connected host. */
interface PropThumbnailSource {
    /** Emits the request builder of each newly connected host, and null while none is connected. */
    val propThumbnailRequests: Flow<PropThumbnailRequests?>
}
