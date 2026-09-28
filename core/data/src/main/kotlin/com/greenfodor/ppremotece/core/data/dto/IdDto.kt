package com.greenfodor.ppremotece.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class IdDto(
    val uuid: String,
    val name: String,
    val index: Int
)
