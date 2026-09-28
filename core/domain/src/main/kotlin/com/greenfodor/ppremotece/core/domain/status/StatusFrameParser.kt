package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Splits a `status/updates` byte stream into frames separated by `\r\n\r\n` and decodes each
 * frame's `{url, data}` into a [StatusEvent]. Bytes of an incomplete frame are kept until a later
 * chunk completes it; more than [MAX_PENDING_BYTES] without a separator are dropped. One instance
 * serves one stream connection.
 */
class StatusFrameParser {
    private var pending = ByteArray(0)

    fun feed(chunk: ByteArray): List<String> {
        val scanFrom = maxOf(0, pending.size - SEPARATOR.size + 1)
        pending += chunk
        val frames = mutableListOf<String>()
        var start = 0
        var separator = indexOfSeparator(pending, scanFrom)
        while (separator >= 0) {
            val frame = pending.decodeToString(start, separator)
            if (frame.isNotBlank()) frames += frame
            start = separator + SEPARATOR.size
            separator = indexOfSeparator(pending, start)
        }
        pending =
            if (pending.size - start > MAX_PENDING_BYTES) ByteArray(0) else pending.copyOfRange(start, pending.size)
        return frames
    }

    fun events(chunk: ByteArray): List<StatusEvent> = feed(chunk).map(::decode)

    fun decode(frame: String): StatusEvent {
        val root = parseObject(frame) ?: return StatusEvent.Unknown(null)
        val url = (root["url"] as? JsonPrimitive)?.content
        val data = root["data"]
        return when (url) {
            "status/slide" -> StatusEvent.SlideChanged
            "presentation/slide_index" -> StatusEvent.SlideIndex(data.child("presentation_index").toLiveSlide())
            "presentation/active" ->
                StatusEvent.PresentationActive(data.child("presentation").child("id").child("uuid").stringOrNull())
            "playlist/active" -> StatusEvent.PlaylistActive(data.child("presentation").toPlaylistItemKey())
            "timer/system_time" -> (data as? JsonPrimitive)?.longOrNull?.let { StatusEvent.Heartbeat(it) }
                ?: StatusEvent.Unknown(url)
            else -> StatusEvent.Unknown(url)
        }
    }

    private fun parseObject(frame: String): JsonObject? =
        try {
            Json.parseToJsonElement(frame).jsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

    private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.intOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun JsonElement?.toLiveSlide(): LiveSlide? {
        val uuid = child("presentation_id").child("uuid").stringOrNull()
        val index = child("index").intOrNull()
        return if (uuid != null && index != null) {
            LiveSlide(presentationUuid = uuid, index = index, totalCues = child("total_cues").intOrNull() ?: 0)
        } else {
            null
        }
    }

    private fun JsonElement?.toPlaylistItemKey(): PlaylistItemKey? {
        val playlistUuid = child("playlist").child("uuid").stringOrNull()
        val itemIndex = child("item").child("index").intOrNull()
        return if (playlistUuid != null && itemIndex != null) PlaylistItemKey(playlistUuid, itemIndex) else null
    }

    private fun indexOfSeparator(bytes: ByteArray, from: Int): Int {
        for (i in from..bytes.size - SEPARATOR.size) {
            if (SEPARATOR.indices.all { bytes[i + it] == SEPARATOR[it] }) return i
        }
        return -1
    }

    companion object {
        const val MAX_PENDING_BYTES = 1 shl 20
        private val SEPARATOR = "\r\n\r\n".encodeToByteArray()
    }
}
