package com.greenfodor.ppremotece.core.domain.thumbnail

import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import kotlinx.coroutines.flow.Flow

/** Where a cue's thumbnail is read from and the key it is cached under. */
data class ThumbnailRequest(
    val url: String,
    val cacheKey: String
)

/** Builds thumbnail requests for one connected host. */
fun interface ThumbnailRequests {
    fun request(item: PlaylistItemKey, presentationUuid: String, cue: Cue): ThumbnailRequest
}

/** Thumbnail requests of the connected host. */
interface ThumbnailSource {
    /** Emits the request builder of each newly connected host, and null while none is connected. */
    val thumbnailRequests: Flow<ThumbnailRequests?>
}
