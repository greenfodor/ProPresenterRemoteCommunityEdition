package com.greenfodor.ppremotece.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionDto(
    val name: String = "",
    @SerialName("host_description") val hostDescription: String = "",
    @SerialName("api_version") val apiVersion: String = ""
)

@Serializable
data class SlideIndexResponseDto(
    @SerialName("presentation_index") val presentationIndex: PresentationIndexDto? = null
)

@Serializable
data class PresentationIndexDto(
    val index: Int,
    @SerialName("presentation_id") val presentationId: IdDto,
    @SerialName("total_cues") val totalCues: Int = 0
)
