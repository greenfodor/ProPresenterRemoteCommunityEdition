package com.greenfodor.ppremotece.core.data.mapper

import com.greenfodor.ppremotece.core.data.dto.PlaylistDto
import com.greenfodor.ppremotece.core.data.dto.PlaylistItemDto
import com.greenfodor.ppremotece.core.data.dto.PlaylistTreeNodeDto
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistFolder
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.PlaylistLeaf
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.PresentationRef

private const val FIELD_TYPE_FOLDER = "group"

fun PlaylistTreeNodeDto.toDomain(): PlaylistTreeNode =
    if (fieldType == FIELD_TYPE_FOLDER) {
        PlaylistFolder(uuid = id.uuid, name = id.name, children = children.orEmpty().map { it.toDomain() })
    } else {
        PlaylistLeaf(uuid = id.uuid, name = id.name)
    }

fun PlaylistDto.toDomain(): Playlist =
    Playlist(
        uuid = id.uuid,
        name = id.name,
        items = items.orEmpty().map { it.toDomain(id.uuid) }
    )

private fun PlaylistItemDto.toDomain(playlistUuid: String): PlaylistItem =
    PlaylistItem(
        key = PlaylistItemKey(playlistUuid = playlistUuid, index = id.index),
        name = id.name,
        type = when (type) {
            "presentation" -> PlaylistItemType.PRESENTATION
            "header" -> PlaylistItemType.HEADER
            "media" -> PlaylistItemType.MEDIA
            "placeholder" -> PlaylistItemType.PLACEHOLDER
            "audio" -> PlaylistItemType.AUDIO
            "livevideo" -> PlaylistItemType.LIVE_VIDEO
            else -> PlaylistItemType.OTHER
        },
        presentation = presentationInfo?.let {
            PresentationRef(
                presentationUuid = it.presentationUuid,
                arrangementUuid = it.arrangementUuid,
                arrangementName = it.arrangementName
            )
        }
    )
