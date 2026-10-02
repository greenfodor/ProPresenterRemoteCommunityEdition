package com.greenfodor.ppremotece.core.domain.status

import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.SlideText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Splits a `status/updates` byte stream into frames separated by `\r\n\r\n` and decodes each
 * frame's `{url, data}` into a [StatusEvent]; an error frame (a list of strings) decodes to
 * [StatusEvent.Rejected] with its strings. Bytes of an incomplete frame are kept until a later
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

    fun decode(frame: String): StatusEvent =
        when (val element = parseElement(frame)) {
            is JsonArray -> element.mapNotNull { it.stringOrNull() }
                .takeIf { it.isNotEmpty() }
                ?.let(StatusEvent::Rejected)
                ?: StatusEvent.Unknown(null)
            is JsonObject -> decodeObject(element)
            else -> StatusEvent.Unknown(null)
        }

    private fun decodeObject(root: JsonObject): StatusEvent {
        val url = (root["url"] as? JsonPrimitive)?.content
        val data = root["data"]
        return when (url) {
            "status/slide" -> StatusEvent.SlideChanged(data.toSlideText())
            "presentation/slide_index" -> StatusEvent.SlideIndex(data.child("presentation_index").toLiveSlide())
            "presentation/active" ->
                StatusEvent.PresentationActive(data.child("presentation").child("id").child("uuid").stringOrNull())
            "playlist/active" -> playlistActiveOf(data)
            "status/layers" -> (data as? JsonObject)?.toLayers()
            "timer/system_time" -> (data as? JsonPrimitive)?.longOrNull?.let { StatusEvent.Heartbeat(it) }
            else -> listEventOf(url, data)
        } ?: StatusEvent.Unknown(url)
    }

    private fun listEventOf(url: String?, data: JsonElement?): StatusEvent? {
        val list = data as? JsonArray
        val collections = data.child("collections") as? JsonArray
        return when {
            url == "timers" && list != null -> StatusEvent.Timers(list.mapNotNull { it.toTimer() })
            url == "timers/current" && list != null ->
                StatusEvent.TimerReadings(list.mapNotNull { it.toTimerReading() })
            url == "macro_collections" && collections != null ->
                StatusEvent.MacroCollections(collections.mapNotNull { it.toMacroCollection() })
            url == "looks" && list != null -> StatusEvent.Looks(list.mapNotNull { it.toLook() })
            url == "look/current" -> data.toLook()?.let(StatusEvent::CurrentLook)
            else -> null
        }
    }

    private fun JsonObject.toLayers() =
        StatusEvent.Layers(
            filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }
                .keys
                .mapNotNull(OutputLayer::fromApiName)
                .toSet()
        )

    private fun parseElement(frame: String): JsonElement? =
        try {
            Json.parseToJsonElement(frame)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun JsonElement?.toLiveSlide(): LiveSlide? {
        val uuid = child("presentation_id").child("uuid").stringOrNull()
        val index = child("index").intOrNull()
        return if (uuid != null && index != null) {
            LiveSlide(presentationUuid = uuid, index = index, totalCues = child("total_cues").intOrNull() ?: 0)
        } else {
            null
        }
    }

    private fun JsonElement?.toSlideText(): SlideText? =
        (this as? JsonObject)?.let {
            SlideText(
                current = child("current").child("text").stringOrNull().orEmpty(),
                next = child("next").child("text").stringOrNull().orEmpty()
            )
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

/** The live playlist item named by a `playlist/active` frame's `data`, or by a `GET /v1/playlist/active` body. */
fun playlistActiveOf(data: JsonElement?): StatusEvent.PlaylistActive =
    data.child("presentation").let { live ->
        StatusEvent.PlaylistActive(
            item = live.toPlaylistItemKey(),
            presentationUuid = live.child("playlist_item").child("presentation_info")
                .child("presentation_uuid").stringOrNull()
        )
    }

internal fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.intOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

private fun JsonElement?.toPlaylistItemKey(): PlaylistItemKey? {
    val playlistUuid = child("playlist").child("uuid").stringOrNull()
    val itemIndex = child("item").child("index").intOrNull()
    return if (playlistUuid != null && itemIndex != null) PlaylistItemKey(playlistUuid, itemIndex) else null
}
