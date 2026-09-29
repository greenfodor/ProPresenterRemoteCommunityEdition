package com.greenfodor.ppremotece.core.data.thumbnail

import com.greenfodor.ppremotece.core.data.network.playlistItemPath
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey

private const val THUMBNAIL_QUALITY = 400

/** `GET {baseUrl}/v1/playlist/{pl}/{item}/thumbnail/{cue}?quality=400`. */
fun thumbnailUrl(baseUrl: String, item: PlaylistItemKey, cueIndex: Int): String =
    "${baseUrl.trimEnd('/')}/${playlistItemPath(item)}/thumbnail/$cueIndex?quality=$THUMBNAIL_QUALITY"
