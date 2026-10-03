package com.greenfodor.ppremotece.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistTreeNodeDto(
    val id: IdDto,
    @SerialName("field_type") val fieldType: String,
    val children: List<PlaylistTreeNodeDto>? = null
)

@Serializable
data class PlaylistDto(
    val id: IdDto,
    val items: List<PlaylistItemDto>? = null
)

@Serializable
data class PlaylistItemDto(
    val id: IdDto,
    val type: String,
    @SerialName("presentation_info") val presentationInfo: PresentationInfoDto? = null,
    @SerialName("header_color") val headerColor: ColorDto? = null,
    @SerialName("target_uuid") val targetUuid: String? = null,
    val duration: Double? = null
)

@Serializable
data class PresentationInfoDto(
    @SerialName("presentation_uuid") val presentationUuid: String,
    @SerialName("arrangement_name") val arrangementName: String = "",
    @SerialName("arrangement_uuid") val arrangementUuid: String = ""
)
