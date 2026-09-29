package com.greenfodor.ppremotece.core.data.network

import assertk.assertThat
import assertk.assertions.isEmpty
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.RecordedRequest
import okio.BufferedSink
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/** A MockWebServer dispatcher that answers like ProPresenter from the sanitised fixtures. */
class FakeProPresenter(
    private val cueCount: Int = 7
) : Dispatcher() {
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val arrivals = CopyOnWriteArrayList<Long>()

    @Volatile
    var failSlideIndexReads = 0

    /** Layers whose `clear/layer` answers 500. */
    val failingLayers = CopyOnWriteArrayList<String>()

    /** Bodies served by the next `slide_index` reads, in order, before [SLIDE_INDEX]. */
    val slideIndexBodies = ConcurrentLinkedQueue<String>()
    private val streams = LinkedBlockingQueue<MockResponse>()

    private val stalls = CountDownLatch(1)

    fun enqueueStream(response: MockResponse) {
        streams.add(response)
    }

    /** Replays a captured stream; with [StreamEnd.STALL] the connection stays open and silent until [releaseStalls]. */
    fun stream(
        name: String,
        end: StreamEnd,
        timeScale: Double = 0.02,
        delivered: AtomicInteger = AtomicInteger()
    ): MockResponse = StreamReplay.response(name, end, timeScale, delivered, stalls)

    /** A stream that sends each of [chunks] (frames without their separators) [delayMillis] apart. */
    fun frames(vararg chunks: List<String>, delayMillis: Long = 50, end: StreamEnd = StreamEnd.STALL): MockResponse =
        StreamReplay.frames(chunks.toList(), delayMillis, end, stalls)

    fun releaseStalls() {
        stalls.countDown()
    }

    fun count(method: String, path: String): Int =
        requests.count { it.method == method && it.url.encodedPath == path }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        arrivals += System.nanoTime()
        val path = request.url.encodedPath
        return when {
            request.method == "POST" && path == "/v1/status/updates" -> streams.poll() ?: StreamReplay.silent(stalls)
            request.method == "GET" -> dispatchGet(path)
            else -> status(405)
        }
    }

    private fun dispatchGet(path: String): MockResponse =
        when {
            path == "/version" -> json(Fixtures.text("version.json"))
            path == "/v1/playlists" -> json(Fixtures.text(Fixtures.PLAYLIST_TREE))
            path == "/v1/presentation/slide_index" && failSlideIndexReads > 0 -> {
                failSlideIndexReads--
                status(500)
            }
            path == "/v1/presentation/slide_index" -> json(slideIndexBodies.poll() ?: SLIDE_INDEX)
            path.startsWith("/v1/clear/") -> dispatchClear(path)
            path.endsWith("/trigger") -> dispatchTrigger(path)
            path.startsWith("/v1/playlist/") -> fixture("playlist", path.removePrefix("/v1/playlist/"))
            path.startsWith("/v1/presentation/") -> fixture("presentation", path.removePrefix("/v1/presentation/"))
            else -> status(404)
        }

    private fun dispatchTrigger(path: String): MockResponse =
        when {
            path == "/v1/trigger/next" || path == "/v1/trigger/previous" -> status(204)
            CUE_TRIGGER.matches(path) -> triggerCue(path)
            ITEM_TRIGGER.matches(path) -> status(204)
            else -> status(404)
        }

    private fun dispatchClear(path: String): MockResponse =
        when {
            CLEAR_LAYER.matches(path) -> status(if (path.substringAfterLast('/') in failingLayers) 500 else 204)
            path == "/v1/clear/groups" -> json(Fixtures.text("clear-groups.json"))
            CLEAR_GROUP_TRIGGER.matches(path) -> status(204)
            else -> status(404)
        }

    private fun triggerCue(path: String): MockResponse {
        val cue = requireNotNull(CUE_TRIGGER.matchEntire(path)).groupValues[1].toInt()
        return status(if (cue < cueCount) 204 else 404)
    }

    private fun fixture(kind: String, uuid: String): MockResponse =
        runCatching { json(Fixtures.text("$kind-${uuid.take(8)}.json")) }.getOrElse { status(404) }

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun status(code: Int) = MockResponse.Builder().code(code).build()

    companion object {
        const val SONG_A_UUID = "08672906-49df-4947-8d4c-bed596d6fbf3"
        const val SERVICE_PLAYLIST_UUID = "065f53c3-e299-4e07-8ac2-258ca76b7188"
        const val SLIDE_INDEX = """{"presentation_index":{"index":3,"presentation_id":""" +
            """{"uuid":"$SONG_A_UUID","name":"Song A","index":0},"total_cues":7,"remaining_cues":3}}"""
        private val CUE_TRIGGER = Regex("^/v1/playlist/[0-9a-f-]+/\\d+/(\\d+)/trigger$")
        private val ITEM_TRIGGER = Regex("^/v1/playlist/[0-9a-f-]+/\\d+/trigger$")
        private val CLEAR_LAYER =
            Regex("^/v1/clear/layer/(slide|media|video_input|props|messages|announcements|audio)$")
        private val CLEAR_GROUP_TRIGGER = Regex("^/v1/clear/group/[0-9a-f-]+/trigger$")
        const val NO_SLIDE_INDEX = """{"presentation_index":null}"""
        const val SLIDE_FRAME =
            """{"url":"status/slide","data":{"current":{"text":"Text 01","notes":"","uuid":"s-1"},""" +
                """"next":{"text":"Text 02","notes":"","uuid":"s-2"}}}"""

        /** A `playlist/active` frame naming [item] and the presentation it plays, or no item when null. */
        fun playlistActiveFrame(item: PlaylistItemKey?, presentationUuid: String = SONG_A_UUID): String {
            val presentation = item?.let {
                """{"playlist":{"uuid":"${it.playlistUuid}","name":"Playlist 01","index":0},""" +
                    """"item":{"uuid":"i-${it.index}","name":"Item 01","index":${it.index}},""" +
                    """"playlist_item":{"id":{"uuid":"i-${it.index}","name":"Item 01","index":${it.index}},""" +
                    """"type":"presentation","presentation_info":{"presentation_uuid":"$presentationUuid",""" +
                    """"arrangement_name":"","arrangement_uuid":""}}}"""
            } ?: """{"playlist":null,"item":null}"""
            return """{"url":"playlist/active","data":{"presentation":$presentation,""" +
                """"announcements":{"playlist":null,"item":null}}}"""
        }

        private val ALLOWED = listOf(
            "GET" to Regex("^/version$"),
            "GET" to Regex("^/v1/playlists$"),
            "GET" to Regex("^/v1/playlist/[0-9a-f-]+$"),
            "GET" to Regex("^/v1/presentation/[0-9a-f-]+$"),
            "GET" to Regex("^/v1/presentation/slide_index$"),
            "GET" to Regex("^/v1/playlist/[0-9a-f-]+/\\d+/\\d+/trigger$"),
            "GET" to Regex("^/v1/playlist/[0-9a-f-]+/\\d+/trigger$"),
            "GET" to Regex("^/v1/trigger/(next|previous)$"),
            "GET" to Regex("^/v1/clear/layer/(slide|media|video_input|props|messages|announcements|audio)$"),
            "GET" to Regex("^/v1/clear/groups$"),
            "GET" to Regex("^/v1/clear/group/[0-9a-f-]+/trigger$"),
            "POST" to Regex("^/v1/status/updates$")
        )

        fun assertOnlyAllowedRequests(requests: List<RecordedRequest>) {
            val disallowed = requests
                .filterNot { request ->
                    ALLOWED.any { (method, path) ->
                        request.method == method &&
                            path.matches(request.url.encodedPath)
                    }
                }
                .map { "${it.method} ${it.url.encodedPath}" }
            assertThat(disallowed).isEmpty()
        }
    }
}

/** Timed chunks of a captured `status/updates` stream, replayed with compressed timings. */
object StreamReplay {
    private val CHUNK_LINE = Regex("""^# \+([\d.]+)s chunk \d+ \((\d+) B\)""")

    fun response(
        name: String,
        end: StreamEnd,
        timeScale: Double = 0.02,
        delivered: AtomicInteger = AtomicInteger(),
        stall: CountDownLatch = CountDownLatch(0)
    ): MockResponse {
        val raw = Fixtures.bytes("streams/$name.raw")
        val chunkLines = Fixtures.text("streams/$name.meta").lines().mapNotNull { CHUNK_LINE.find(it) }
        var offset = 0
        var previousSeconds = chunkLines.first().groupValues[1].toDouble()
        val chunks = chunkLines.map { line ->
            val seconds = line.groupValues[1].toDouble()
            val size = line.groupValues[2].toInt()
            val chunk = TimedChunk(
                delayMillis = ((seconds - previousSeconds) * 1000 * timeScale).toLong(),
                bytes = raw.copyOfRange(offset, offset + size)
            )
            offset += size
            previousSeconds = seconds
            chunk
        }
        return streamResponse(ChunkedReplayBody(chunks, end, delivered, stall))
    }

    fun frames(chunks: List<List<String>>, delayMillis: Long, end: StreamEnd, stall: CountDownLatch): MockResponse =
        streamResponse(
            ChunkedReplayBody(
                chunks.map { frames ->
                    TimedChunk(delayMillis, frames.joinToString("") { "$it\r\n\r\n" }.encodeToByteArray())
                },
                end,
                AtomicInteger(),
                stall
            )
        )

    fun silent(stall: CountDownLatch): MockResponse =
        streamResponse(ChunkedReplayBody(emptyList(), StreamEnd.STALL, AtomicInteger(), stall))

    private fun streamResponse(body: MockResponseBody) =
        MockResponse.Builder()
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Transfer-Encoding", "chunked")
            .body(body)
            .build()

    fun chunkCount(name: String): Int = Fixtures.text("streams/$name.meta").lines().count {
        CHUNK_LINE.containsMatchIn(it)
    }

    fun frameCount(name: String, url: String): Int =
        Fixtures.bytes("streams/$name.raw").decodeToString().split("\r\n\r\n").count { it.contains("\"url\":\"$url\"") }
}

enum class StreamEnd { EOF, STALL }

class TimedChunk(
    val delayMillis: Long,
    val bytes: ByteArray
)

private class ChunkedReplayBody(
    private val chunks: List<TimedChunk>,
    private val end: StreamEnd,
    private val delivered: AtomicInteger,
    private val stall: CountDownLatch
) : MockResponseBody {
    override val contentLength: Long = -1L

    override fun writeTo(sink: BufferedSink) {
        for (chunk in chunks) {
            Thread.sleep(chunk.delayMillis)
            sink.writeUtf8(Integer.toHexString(chunk.bytes.size)).writeUtf8("\r\n").write(chunk.bytes).writeUtf8("\r\n")
            sink.flush()
            delivered.incrementAndGet()
        }
        when (end) {
            StreamEnd.EOF -> {
                sink.writeUtf8("0\r\n\r\n")
                sink.flush()
            }
            StreamEnd.STALL -> stall.await()
        }
    }
}
