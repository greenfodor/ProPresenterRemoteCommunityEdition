package com.greenfodor.ppremotece.core.data.network

import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import io.ktor.http.encodeURLPathPart

/** `v1/playlist/{playlistUuid}/{itemIndex}`, with the uuid encoded as a path segment. */
internal fun playlistItemPath(item: PlaylistItemKey): String =
    "v1/playlist/${item.playlistUuid.encodeURLPathPart()}/${item.index}"
