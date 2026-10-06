package com.greenfodor.ppremotece.core.data.mapper

import com.greenfodor.ppremotece.core.data.dto.AudioPlaylistDto
import com.greenfodor.ppremotece.core.domain.model.AudioTrack

/** The playlist's tracks in order, each with its index within the playlist. */
fun AudioPlaylistDto.toTracks(): List<AudioTrack> =
    items.orEmpty().map { track ->
        AudioTrack(
            uuid = track.id.uuid,
            name = track.id.name,
            index = track.id.index,
            artist = track.artist,
            durationSeconds = track.duration.toInt()
        )
    }
