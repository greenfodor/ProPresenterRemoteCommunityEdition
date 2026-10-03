package com.greenfodor.ppremotece.core.domain.model

/** What a transport layer has loaded: [uuid] is empty while nothing is loaded. */
data class Transport(
    val isPlaying: Boolean,
    val uuid: String,
    val name: String,
    val artist: String,
    val audioOnly: Boolean,
    val durationSeconds: Double
)
