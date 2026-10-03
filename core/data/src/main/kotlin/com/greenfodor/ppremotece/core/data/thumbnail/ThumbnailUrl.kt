package com.greenfodor.ppremotece.core.data.thumbnail

import com.greenfodor.ppremotece.core.data.network.playlistItemPath
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRoute
import io.ktor.http.encodeURLPathPart

/** `GET {baseUrl}/v1/playlist/{pl}/{item}/thumbnail/{cue}?quality={boxQuality, else 400}`. */
fun thumbnailUrl(baseUrl: String, item: PlaylistItemKey, cueIndex: Int, boxQuality: Int?): String =
    "${baseUrl.trimEnd('/')}/${playlistItemPath(item)}/thumbnail/$cueIndex" +
        "?quality=${ThumbnailRoute.PLAYLIST.query(boxQuality)}"

/** `GET {baseUrl}/v1/presentation/{uuid}/thumbnail/{cue}?quality={width}`: 711 px for the grid, else the box quality's width. */
fun presentationThumbnailUrl(baseUrl: String, presentationUuid: String, cueIndex: Int, boxQuality: Int?): String =
    "${baseUrl.trimEnd('/')}/v1/presentation/${presentationUuid.encodeURLPathPart()}/thumbnail/$cueIndex" +
        "?quality=${ThumbnailRoute.PRESENTATION.query(boxQuality)}"

/** `GET {baseUrl}/v1/prop/{uuid}/thumbnail?quality={width}`: the prop's image [width] px wide. */
fun propThumbnailUrl(baseUrl: String, propUuid: String, width: Int): String =
    "${baseUrl.trimEnd('/')}/v1/prop/${propUuid.encodeURLPathPart()}/thumbnail?quality=$width"
