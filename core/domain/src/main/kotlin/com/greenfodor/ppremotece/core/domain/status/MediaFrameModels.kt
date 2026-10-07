package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioFolder
import com.greenfodor.ppremotece.core.domain.model.AudioNode
import com.greenfodor.ppremotece.core.domain.model.AudioPlaylist
import com.greenfodor.ppremotece.core.domain.model.Transport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** The event of a transport or audio bin frame; null for any other [url] or unusable [data]. */
internal fun mediaEventOf(url: String?, data: JsonElement?): StatusEvent? =
    when (url) {
        "transport/presentation/current" -> data.toTransport()?.let(StatusEvent::PresentationTransport)
        "transport/audio/current" -> data.toTransport()?.let(StatusEvent::AudioTransport)
        "transport/audio/time" -> (data as? JsonPrimitive)?.doubleOrNull?.let(StatusEvent::AudioTime)
        "audio/playlists" -> (data as? JsonArray)?.let { StatusEvent.AudioPlaylists(it.toAudioNodes()) }
        "audio/playlist/active" -> (data as? JsonObject)?.let { StatusEvent.ActiveAudioChanged(it.toActiveAudio()) }
        else -> null
    }

/** A transport's loaded content; null unless this is an object. */
internal fun JsonElement?.toTransport(): Transport? =
    (this as? JsonObject)?.let {
        Transport(
            isPlaying = (child("is_playing") as? JsonPrimitive)?.booleanOrNull ?: false,
            uuid = child("uuid").stringOrNull().orEmpty(),
            name = child("name").stringOrNull().orEmpty(),
            artist = child("artist").stringOrNull().orEmpty(),
            audioOnly = (child("audio_only") as? JsonPrimitive)?.booleanOrNull ?: false,
            durationSeconds = (child("duration") as? JsonPrimitive)?.doubleOrNull ?: 0.0
        )
    }

/**
 * The audio bin's tree: a `playlist` node with a uuid is a playlist, and any other node a folder
 * with the nodes of its `children`.
 */
internal fun JsonArray.toAudioNodes(): List<AudioNode> =
    mapNotNull { node ->
        val id = node.child("id")
        val uuid = id.child("uuid").stringOrNull()
        val name = id.child("name").stringOrNull().orEmpty()
        if (node.child("type").stringOrNull() == "playlist") {
            uuid?.let { AudioPlaylist(it, name) }
        } else {
            AudioFolder(uuid.orEmpty(), name, (node.child("children") as? JsonArray)?.toAudioNodes().orEmpty())
        }
    }

/** The active audio playlist track; null unless both the playlist and its item are named. */
internal fun JsonElement?.toActiveAudio(): ActiveAudio? {
    val playlistUuid = child("playlist").child("uuid").stringOrNull()
    val trackUuid = child("item").child("uuid").stringOrNull()
    val trackIndex = child("item").child("index").intOrNull()
    return if (playlistUuid != null && trackUuid != null && trackIndex != null) {
        ActiveAudio(playlistUuid, trackUuid, trackIndex)
    } else {
        null
    }
}
