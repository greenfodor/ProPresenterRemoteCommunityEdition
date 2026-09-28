package com.greenfodor.ppremotece.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresentationResponseDto(
    val presentation: PresentationDto
)

@Serializable
data class PresentationDto(
    val id: IdDto,
    val groups: List<GroupDto> = emptyList(),
    val arrangements: List<ArrangementDto> = emptyList()
)

@Serializable
data class GroupDto(
    val uuid: String,
    val name: String,
    val color: ColorDto? = null,
    val slides: List<SlideDto> = emptyList()
)

@Serializable
data class ColorDto(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
)

@Serializable
data class SlideDto(
    val text: String = "",
    val enabled: Boolean = true,
    val size: SlideSizeDto? = null,
    val label: String = ""
)

@Serializable
data class SlideSizeDto(
    val width: Int,
    val height: Int
)

@Serializable
data class ArrangementDto(
    val id: IdDto,
    val groups: List<String> = emptyList(),
    @SerialName("total_cues") val totalCues: Int = 0
)
