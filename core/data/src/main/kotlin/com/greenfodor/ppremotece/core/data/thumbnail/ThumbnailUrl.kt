package com.greenfodor.ppremotece.core.data.thumbnail

import com.greenfodor.ppremotece.core.data.network.playlistItemPath
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRoute

/** `GET {baseUrl}/v1/playlist/{pl}/{item}/thumbnail/{cue}?quality={boxQuality, else 400}`. */
fun thumbnailUrl(baseUrl: String, item: PlaylistItemKey, cueIndex: Int, boxQuality: Int?): String {
    val quality = boxQuality ?: ThumbnailRoute.PLAYLIST.gridQuality
    return "${baseUrl.trimEnd('/')}/${playlistItemPath(item)}/thumbnail/$cueIndex?quality=$quality"
}
