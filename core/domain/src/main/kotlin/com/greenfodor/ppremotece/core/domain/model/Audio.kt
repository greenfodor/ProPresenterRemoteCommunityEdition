package com.greenfodor.ppremotece.core.domain.model

/** A playlist of the audio bin. */
data class AudioPlaylist(
    val uuid: String,
    val name: String,
    val index: Int
)

/** A track of an audio playlist, triggered by its [index] within that playlist. */
data class AudioTrack(
    val uuid: String,
    val name: String,
    val index: Int,
    val artist: String,
    val durationSeconds: Int
)

/** The audio playlist track ProPresenter plays from the audio bin, named by that playlist's own uuids. */
data class ActiveAudio(
    val playlistUuid: String,
    val trackUuid: String,
    val trackIndex: Int
)
