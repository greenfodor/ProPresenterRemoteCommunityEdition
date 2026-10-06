package com.greenfodor.ppremotece.core.data.dto

import kotlinx.serialization.Serializable

/** `GET /v1/audio/playlist/{uuid}`: an audio playlist with its tracks. */
@Serializable
data class AudioPlaylistDto(
    val id: IdDto,
    val items: List<AudioTrackDto>? = null
)

@Serializable
data class AudioTrackDto(
    val id: IdDto,
    val artist: String = "",
    val duration: Double = 0.0
)
