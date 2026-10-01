package com.greenfodor.ppremotece.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LibraryResponseDto(
    val items: List<IdDto> = emptyList()
)
