package com.greenfodor.ppremotece.core.data.network

import assertk.assertThat
import assertk.assertions.isEmpty
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.status.StatusFrameParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.RecordedRequest
import okio.BufferedSink
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** A MockWebServer dispatcher that answers like ProPresenter from the sanitised fixtures. */
class FakeProPresenter(
    private val cueCount: Int = 7
) : Dispatcher() {
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val arrivals = CopyOnWriteArrayList<Long>()

    @Volatile
    var failSlideIndexReads = 0

    @Volatile
    var failPlaylistActiveReads = 0

    /** The body served for every clear group icon. */
    @Volatile
    var iconBody: String = ICON_SVG

    /** Bodies served by the next `slide_index` reads, in order, before [SLIDE_INDEX]. */
    val slideIndexBodies = ConcurrentLinkedQueue<String>()

    /**
     * When set, answers every `slide_index` and `playlist/active` read with its bodies, in place of
     * [slideIndexBodies] and the last streamed `playlist/active` frame.
     */
    @Volatile
    var liveBodies: (() -> LiveBodies)? = null

    private val streams = LinkedBlockingQueue<MockResponse>()
    private val lastPlaylistActive = AtomicReference(NO_PLAYLIST_ACTIVE)
    private val slideReads = Semaphore(0)

    private val stalls = CountDownLatch(1)

    fun enqueueStream(response: MockResponse) {
        streams.add(response)
    }

    /**
     * Replays a captured stream, or only its first [chunkLimit] chunks; with [StreamEnd.STALL] the
     * connection stays open and silent until [releaseStalls].
     */
    fun stream(
        name: String,
        end: StreamEnd,
        timeScale: Double = 0.02,
        delivered: AtomicInteger = AtomicInteger(),
        chunkLimit: Int = Int.MAX_VALUE
    ): MockResponse = StreamReplay.response(name, end, timeScale, delivered, stalls, chunkLimit, ::noteChunk)

    /**
     * A stream that sends each of [chunks] (frames without their separators) [delayMillis] apart.
     * With [awaitSlideReads], each chunk after the first waits until the previous one was followed
     * by a `slide_index` read.
     */
    fun frames(
        vararg chunks: List<String>,
        delayMillis: Long = 50,
        end: StreamEnd = StreamEnd.STALL,
        awaitSlideReads: Boolean = false
    ): MockResponse =
        StreamReplay.frames(
            chunks.toList(),
            delayMillis,
            end,
            stalls,
            onChunk = ::noteChunk,
            beforeChunk = { index ->
                if (awaitSlideReads && index > 0) check(slideReads.tryAcquire(READ_WAIT_SECONDS, TimeUnit.SECONDS))
            }
        )

    /** The `name` `/version` reports instead of the fixture's, when set. */
    @Volatile
    var versionName: String? = null

    private fun versionJson(): String {
        val fixture = Fixtures.text("version.json")
        return versionName?.let { fixture.replace("\"name\": \"Host 01\"", "\"name\": \"$it\"") } ?: fixture
    }

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
            path == "/version" -> json(versionJson())
            path == "/v1/playlist/active" || path == "/v1/presentation/slide_index" -> dispatchLive(path)
            path == "/v1/playlists" -> json(Fixtures.text(Fixtures.PLAYLIST_TREE))
            path == "/v1/libraries" -> json(Fixtures.text(Fixtures.LIBRARIES))
            path.startsWith("/v1/library/") -> fixture("library", path.removePrefix("/v1/library/"))
            path.startsWith("/v1/clear/") -> dispatchClear(path)
            path.startsWith("/v1/timer/") || path.startsWith("/v1/macro/") -> dispatchTimerOrMacro(path)
            path.endsWith("/trigger") -> dispatchTrigger(path)
            path.startsWith("/v1/playlist/") -> fixture("playlist", path.removePrefix("/v1/playlist/"))
            path.startsWith("/v1/presentation/") -> fixture("presentation", path.removePrefix("/v1/presentation/"))
            else -> status(404)
        }

    private fun dispatchLive(path: String): MockResponse =
        when {
            path == "/v1/playlist/active" && failPlaylistActiveReads > 0 -> {
                failPlaylistActiveReads--
                status(500)
            }
            path == "/v1/playlist/active" -> json(liveBodies?.invoke()?.playlistActive ?: lastPlaylistActive.get())
            failSlideIndexReads > 0 -> {
                failSlideIndexReads--
                status(500)
            }
            else -> {
                slideReads.release()
                json(liveBodies?.invoke()?.slideIndex ?: slideIndexBodies.poll() ?: SLIDE_INDEX)
            }
        }

    private fun dispatchTimerOrMacro(path: String): MockResponse =
        when {
            TIMER_OPERATION.matches(path) || MACRO_TRIGGER.matches(path) -> status(204)
            MACRO_ICON.matches(path) ->
                MockResponse.Builder().addHeader("Content-Type", "image/svg+xml").body(iconBody).build()
            else -> status(404)
        }

    private fun dispatchTrigger(path: String): MockResponse =
        when {
            path == "/v1/trigger/next" || path == "/v1/trigger/previous" -> status(204)
            CUE_TRIGGER.matches(path) -> triggerCue(path)
            ITEM_TRIGGER.matches(path) -> status(204)
            PRESENTATION_TRIGGER.matches(path) -> status(204)
            else -> status(404)
        }

    private fun dispatchClear(path: String): MockResponse =
        when {
            CLEAR_LAYER.matches(path) -> status(204)
            path == "/v1/clear/groups" -> json(Fixtures.text("clear-groups.json"))
            CLEAR_GROUP_TRIGGER.matches(path) -> status(204)
            CLEAR_GROUP_ICON.matches(path) ->
                MockResponse.Builder().addHeader("Content-Type", "image/svg+xml").body(iconBody).build()
            else -> status(404)
        }

    private fun triggerCue(path: String): MockResponse {
        val cue = requireNotNull(CUE_TRIGGER.matchEntire(path)).groupValues[1].toInt()
        return status(if (cue < cueCount) 204 else 404)
    }

    private fun noteChunk(frames: List<String>) {
        frames.mapNotNull(::playlistActiveData).lastOrNull()?.let(lastPlaylistActive::set)
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
        private val PRESENTATION_TRIGGER = Regex("^/v1/presentation/[0-9a-f-]+/\\d+/trigger$")
        private val CLEAR_LAYER =
            Regex("^/v1/clear/layer/(slide|media|video_input|props|messages|announcements|audio)$")
        private val CLEAR_GROUP_TRIGGER = Regex("^/v1/clear/group/[0-9a-f-]+/trigger$")
        private val CLEAR_GROUP_ICON = Regex("^/v1/clear/group/[0-9a-f-]+/icon$")
        private val TIMER_OPERATION = Regex("^/v1/timer/[0-9a-f-]+/(start|stop|reset)$")
        private val MACRO_TRIGGER = Regex("^/v1/macro/[0-9a-f-]+/trigger$")
        private val MACRO_ICON = Regex("^/v1/macro/[0-9a-f-]+/icon$")
        const val ICON_SVG = """<svg viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">""" +
            """<path d="M1,1 L17,17" fill="#FFFFFF"/></svg>"""
        const val NO_SLIDE_INDEX = """{"presentation_index":null}"""
        const val NO_PLAYLIST_ACTIVE = """{"presentation":{"playlist":null,"item":null},""" +
            """"announcements":{"playlist":null,"item":null}}"""
        private const val READ_WAIT_SECONDS = 5L
        const val HEARTBEAT_FRAME = """{"url":"timer/system_time","data":1790000000}"""
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
            "GET" to Regex("^/v1/libraries$"),
            "GET" to Regex("^/v1/library/[0-9a-f-]+$"),
            "GET" to Regex("^/v1/presentation/[0-9a-f-]+/\\d+/trigger$"),
            "GET" to Regex("^/v1/playlist/[0-9a-f-]+$"),
            "GET" to Regex("^/v1/presentation/[0-9a-f-]+$"),
            "GET" to Regex("^/v1/presentation/slide_index$"),
            "GET" to Regex("^/v1/playlist/active$"),
            "GET" to Regex("^/v1/playlist/[0-9a-f-]+/\\d+/\\d+/trigger$"),
            "GET" to Regex("^/v1/playlist/[0-9a-f-]+/\\d+/trigger$"),
            "GET" to Regex("^/v1/trigger/(next|previous)$"),
            "GET" to Regex("^/v1/clear/layer/(slide|media|video_input|props|messages|announcements|audio)$"),
            "GET" to Regex("^/v1/clear/groups$"),
            "GET" to Regex("^/v1/clear/group/[0-9a-f-]+/trigger$"),
            "GET" to Regex("^/v1/clear/group/[0-9a-f-]+/icon$"),
            "GET" to Regex("^/v1/timer/[0-9a-f-]+/(start|stop|reset)$"),
            "GET" to Regex("^/v1/macro/[0-9a-f-]+/(trigger|icon)$"),
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
        stall: CountDownLatch = CountDownLatch(0),
        chunkLimit: Int = Int.MAX_VALUE,
        onChunk: (List<String>) -> Unit = {}
    ): MockResponse {
        val chunks = chunks(name, timeScale).take(chunkLimit)
        return streamResponse(ChunkedReplayBody(chunks, end, delivered, stall, onChunk))
    }

    /** The timed chunks of capture [name], their delays scaled by [timeScale]. */
    fun chunks(name: String, timeScale: Double = 0.02): List<TimedChunk> {
        val raw = Fixtures.bytes("streams/$name.raw")
        val chunkLines = Fixtures.text("streams/$name.meta").lines().mapNotNull { CHUNK_LINE.find(it) }
        var offset = 0
        var previousSeconds = chunkLines.first().groupValues[1].toDouble()
        return chunkLines.map { line ->
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
    }

    fun frames(
        chunks: List<List<String>>,
        delayMillis: Long,
        end: StreamEnd,
        stall: CountDownLatch,
        onChunk: (List<String>) -> Unit = {},
        beforeChunk: (index: Int) -> Unit = {}
    ): MockResponse =
        streamResponse(
            ChunkedReplayBody(
                chunks.map { frames ->
                    TimedChunk(delayMillis, frames.joinToString("") { "$it\r\n\r\n" }.encodeToByteArray())
                },
                end,
                AtomicInteger(),
                stall,
                onChunk,
                beforeChunk
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
    private val stall: CountDownLatch,
    private val onChunk: (List<String>) -> Unit = {},
    private val beforeChunk: (index: Int) -> Unit = {}
) : MockResponseBody {
    override val contentLength: Long = -1L

    override fun writeTo(sink: BufferedSink) {
        val parser = StatusFrameParser()
        for ((index, chunk) in chunks.withIndex()) {
            beforeChunk(index)
            Thread.sleep(chunk.delayMillis)
            onChunk(parser.feed(chunk.bytes))
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

/** The bodies of a `slide_index` read and a `playlist/active` read. */
data class LiveBodies(
    val slideIndex: String,
    val playlistActive: String
)

/** The `data` of [frame] when it is a `playlist/active` frame, as JSON text. */
fun playlistActiveData(frame: String): String? {
    val root = Json.parseToJsonElement(frame).jsonObject
    return root["data"]?.toString()?.takeIf { (root["url"] as? JsonPrimitive)?.content == "playlist/active" }
}

/**
 * What ProPresenter answers to `slide_index` and `playlist/active` reads during capture [name]:
 * from each `status/slide` frame on, the `presentation/slide_index` and `playlist/active` frames
 * that follow it before the next `status/slide` (the earlier `playlist/active` when none follows).
 */
class CapturedLiveBodies(
    name: String
) {
    private val bodiesAfterChunk: List<LiveBodies>

    init {
        val parser = StatusFrameParser()
        val framesByChunk = StreamReplay.chunks(name).map { parser.feed(it.bytes) }
        val frames = framesByChunk.flatten()
        val chunkOfFrame = framesByChunk.flatMapIndexed { chunk, chunkFrames -> chunkFrames.map { chunk } }
        var slideIndex = NO_SLIDE_INDEX_BODY
        var playlistActive = FakeProPresenter.NO_PLAYLIST_ACTIVE
        val states = MutableList(framesByChunk.size) { LiveBodies(slideIndex, playlistActive) }
        val transitionStarts = listOf(0) + frames.indices.filter { url(frames[it]) == "status/slide" }
        for ((n, start) in transitionStarts.withIndex()) {
            val end = transitionStarts.getOrElse(n + 1) { frames.size }
            for (frame in frames.subList(start, end)) {
                when (url(frame)) {
                    "presentation/slide_index" -> slideIndex = data(frame)
                    "playlist/active" -> playlistActive = data(frame)
                }
            }
            val bodies = LiveBodies(slideIndex, playlistActive)
            for (chunk in chunkOfFrame[start] until states.size) states[chunk] = bodies
        }
        bodiesAfterChunk = states
    }

    /** The bodies once [delivered] chunks were sent. */
    fun after(delivered: Int): LiveBodies = bodiesAfterChunk[(delivered - 1).coerceIn(bodiesAfterChunk.indices)]

    private fun url(frame: String) = (Json.parseToJsonElement(frame).jsonObject["url"] as? JsonPrimitive)?.content

    private fun data(frame: String) = Json.parseToJsonElement(frame).jsonObject["data"].toString()

    private companion object {
        const val NO_SLIDE_INDEX_BODY = """{"presentation_index":null}"""
    }
}
