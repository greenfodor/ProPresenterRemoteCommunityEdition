package com.greenfodor.ppremotece.core.data.live

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamEnd
import com.greenfodor.ppremotece.core.data.network.StreamReplay
import com.greenfodor.ppremotece.core.domain.audio.TransportButton
import com.greenfodor.ppremotece.core.domain.audio.nowPlaying
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.orNull
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioPlaylist
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import com.greenfodor.ppremotece.core.domain.status.StatusFrameParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Replays of the stage 10 audio capture: track index 2 of playlist index 3 is played, paused and
 * resumed, then next, previous and previous again, then the audio layer is cleared.
 */
class AudioReplayTest {
    @StartStop
    private val server = MockWebServer()

    private val fake = FakeProPresenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: KtorProPresenterClient

    /** What the audio side of the repository holds. */
    private data class Audio(
        val playlists: List<AudioPlaylist>,
        val active: ActiveAudio?,
        val transport: Transport?,
        val position: Double?
    )

    @BeforeEach
    fun setUp() {
        server.dispatcher = fake
        client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        runBlocking { client.version() }
    }

    @AfterEach
    fun tearDown() {
        fake.releaseStalls()
        scope.cancel()
    }

    @Test
    fun `the capture lists the six audio playlists in order`() = runBlocking<Unit> {
        val audio = audioAfterChunk(PLAYLISTS_CHUNK)

        assertThat(audio.playlists.map { it.name }).containsExactly(
            "Audio Playlist 01",
            "Audio Playlist 02",
            "Audio Playlist 03",
            "Audio Playlist 04",
            "Audio Playlist 05",
            "Audio Playlist 06"
        )
        assertThat(audio.playlists.map { it.index }).isEqualTo((0..5).toList())
        assertThat(audio.active).isNull()
    }

    @Test
    fun `a triggered track is active by the playlist's own uuids and its transport plays`() = runBlocking<Unit> {
        val audio = audioAfterChunk(TRACK_2_PLAYING_CHUNK)

        assertThat(audio.active).isEqualTo(ActiveAudio(Fixtures.AUDIO_PLAYLIST_UUID, TRACK_2_UUID, trackIndex = 2))
        assertThat(audio.transport?.isPlaying).isEqualTo(true)
        assertThat(audio.transport?.name).isEqualTo("Media 04")
        assertThat(
            nowPlaying(audio.transport.asLoadable(), audio.position, audio.active).button
        ).isEqualTo(TransportButton.PAUSE)
    }

    @Test
    fun `the position follows the time frames while the track plays`() = runBlocking<Unit> {
        val audio = audioAfterChunk(TRACK_2_FOUR_SECONDS_CHUNK)

        assertThat(audio.position).isEqualTo(4.305506499963813)
        assertThat(
            nowPlaying(audio.transport.asLoadable(), audio.position, audio.active).readout
        ).isEqualTo("0:04 / 3:35")
    }

    @Test
    fun `a pause keeps the track active and a play resumes it`() = runBlocking<Unit> {
        val paused = audioAfterChunk(PAUSED_CHUNK)
        val resumed = audioAfterChunk(RESUMED_CHUNK)

        assertThat(paused.active?.trackIndex).isEqualTo(2)
        assertThat(paused.transport?.isPlaying).isEqualTo(false)
        assertThat(
            nowPlaying(paused.transport.asLoadable(), paused.position, paused.active).button
        ).isEqualTo(TransportButton.PLAY)
        assertThat(resumed.transport?.isPlaying).isEqualTo(true)
    }

    @Test
    fun `next and previous move the active track`() = runBlocking<Unit> {
        assertThat(audioAfterChunk(TRACK_3_CHUNK).active?.trackIndex).isEqualTo(3)
        assertThat(audioAfterChunk(BACK_TO_TRACK_2_CHUNK).active?.trackIndex).isEqualTo(2)
        assertThat(audioAfterChunk(TRACK_1_CHUNK).active?.trackIndex).isEqualTo(1)
    }

    @Test
    fun `the position starts again when another track loads`() = runBlocking<Unit> {
        val loaded = audioAfterChunk(TRACK_3_CHUNK)
        val firstFrame = audioAfterChunk(TRACK_3_FIRST_TIME_CHUNK)

        assertThat(loaded.active?.trackIndex).isEqualTo(3)
        assertThat(loaded.position).isNull()
        assertThat(nowPlaying(loaded.transport.asLoadable(), loaded.position, loaded.active).progress).isEqualTo(0f)
        assertThat(firstFrame.position).isEqualTo(0.27957063326456894)
    }

    @Test
    fun `the position sent before the first transport frame of a connection is kept`() = runBlocking<Unit> {
        val repository = repository()
        fake.enqueueStream(fake.frames(listOf(PAUSED_AT_83_TIME_FRAME, PAUSED_TRANSPORT_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        val transport = withTimeout(5.seconds) { repository.audioTransport.map { it.orNull() }.first { it != null } }
        collector.cancel()

        assertThat(transport?.isPlaying).isEqualTo(false)
        assertThat(repository.audioPosition.value).isEqualTo(83.0)
    }

    @Test
    fun `a pause and a resume keep the position`() = runBlocking<Unit> {
        assertThat(audioAfterChunk(PAUSED_CHUNK).position).isEqualTo(4.305506499963813)
        assertThat(audioAfterChunk(RESUMED_CHUNK).position).isEqualTo(5.373020833333333)
    }

    @Test
    fun `a clear leaves no active track and nothing loaded`() = runBlocking<Unit> {
        val audio = audioAfterChunk(CLEARED_CHUNK)

        assertThat(audio.active).isNull()
        assertThat(audio.transport?.uuid).isEqualTo("")
        assertThat(nowPlaying(audio.transport.asLoadable(), audio.position, audio.active).loaded).isEqualTo(false)
    }

    /**
     * Replays the capture through [chunk] on a new repository and returns its audio state once it
     * equals what the capture holds at that chunk.
     */
    private suspend fun audioAfterChunk(chunk: Int): Audio =
        coroutineScope {
            val repository = repository()
            fake.enqueueStream(fake.stream(STAGE_10_AUDIO, StreamEnd.STALL, timeScale = 0.0, chunkLimit = chunk))
            val events = StreamReplay.chunks(STAGE_10_AUDIO, timeScale = 0.0).take(chunk)
                .flatMap { StatusFrameParser().events(it.bytes) }
            val expectedActive = events.filterIsInstance<StatusEvent.ActiveAudioChanged>().last().active
            val expectedTransport = events.filterIsInstance<StatusEvent.AudioTransport>().last().transport
            val expectedPosition = expectedPosition(events)
            val collector = launch { repository.liveState.collect {} }
            val audio = withTimeout(5.seconds) {
                Audio(
                    playlists = repository.audioPlaylists.filterIsInstance<Loadable.Loaded<List<AudioPlaylist>>>()
                        .first().value,
                    active = repository.activeAudio.first { it == expectedActive },
                    transport = repository.audioTransport.map { it.orNull() }.first { it == expectedTransport },
                    position = repository.audioPosition.first { it == expectedPosition }
                )
            }
            collector.cancel()
            audio
        }

    private fun repository() =
        StreamingLiveStateRepository(
            client = client,
            scope = scope,
            watchdogTimeout = 1.seconds,
            reconnectDelay = { 10.milliseconds },
            log = {}
        )

    /**
     * The last position of [events] sent since the audio transport last changed what it has loaded;
     * null without one. The first transport frame changes nothing.
     */
    private fun expectedPosition(events: List<StatusEvent>): Double? {
        var uuid: String? = null
        var position: Double? = null
        events.forEach { event ->
            when (event) {
                is StatusEvent.AudioTransport -> {
                    if (uuid != null && event.transport.uuid != uuid) position = null
                    uuid = event.transport.uuid
                }
                is StatusEvent.AudioTime -> position = event.seconds
                else -> Unit
            }
        }
        return position
    }

    private fun Transport?.asLoadable(): Loadable<Transport> = this?.let { Loadable.Loaded(it) } ?: Loadable.NotLoaded

    private companion object {
        const val STAGE_10_AUDIO = "stage10-audio"
        const val PAUSED_AT_83_TIME_FRAME = """{"url":"transport/audio/time","data":83.0}"""
        const val PAUSED_TRANSPORT_FRAME = """{"url":"transport/audio/current","data":{"is_playing":false,""" +
            """"uuid":"a-0","name":"Media 04","artist":"Artist 02","audio_only":true,"duration":215.9}}"""
        const val TRACK_2_UUID = "0ff5ae8f-74ff-437e-bf1b-f3e6e4492ccf"
        const val PLAYLISTS_CHUNK = 14
        const val TRACK_2_PLAYING_CHUNK = 19
        const val TRACK_2_FOUR_SECONDS_CHUNK = 28
        const val PAUSED_CHUNK = 30
        const val RESUMED_CHUNK = 38
        const val TRACK_3_CHUNK = 49
        const val TRACK_3_FIRST_TIME_CHUNK = 50
        const val BACK_TO_TRACK_2_CHUNK = 64
        const val TRACK_1_CHUNK = 75
        const val CLEARED_CHUNK = 92
    }
}
