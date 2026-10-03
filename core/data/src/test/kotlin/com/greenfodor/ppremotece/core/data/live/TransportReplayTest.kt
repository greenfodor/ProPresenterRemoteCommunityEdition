package com.greenfodor.ppremotece.core.data.live

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.data.mapper.toDomain
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamEnd
import com.greenfodor.ppremotece.core.data.network.StreamReplay
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import com.greenfodor.ppremotece.core.domain.status.StatusFrameParser
import com.greenfodor.ppremotece.core.domain.transport.itemLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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

/** Replays of the stage 10 items capture: an audio item, then a media item, a clear and the restore. */
class TransportReplayTest {
    @StartStop
    private val server = MockWebServer()

    private val fake = FakeProPresenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: KtorProPresenterClient
    private val items = Fixtures.playlist(Fixtures.STAGE_10_PLAYLIST).toDomain().items

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
    fun `the audio item is live once it is triggered`() = runBlocking<Unit> {
        val (presentation, audio) = transportsAfterChunk(AUDIO_ITEM_LIVE_CHUNK)

        assertThat(items.map { itemLive(it, presentation, audio) })
            .containsExactly(false, false, false, false, false, true, false, false, false)
        assertThat(audio?.name).isEqualTo("Media 03")
        assertThat(audio?.artist).isEqualTo("Artist 01")
    }

    @Test
    fun `the media item is live once it is triggered and the audio item no longer is`() = runBlocking<Unit> {
        val (presentation, audio) = transportsAfterChunk(MEDIA_ITEM_LIVE_CHUNK)

        assertThat(items.map { itemLive(it, presentation, audio) })
            .containsExactly(false, false, false, false, true, false, false, false, false)
    }

    @Test
    fun `no item is live after the clear and after the restore`() = runBlocking<Unit> {
        val (clearedPresentation, clearedAudio) = transportsAfterChunk(CLEARED_CHUNK)
        val (restoredPresentation, restoredAudio) = transportsAfterChunk(RESTORED_CHUNK)

        assertThat(clearedPresentation?.uuid).isEqualTo("")
        assertThat(items.any { itemLive(it, clearedPresentation, clearedAudio) }).isFalse()
        assertThat(restoredPresentation?.isPlaying).isEqualTo(true)
        assertThat(items.any { itemLive(it, restoredPresentation, restoredAudio) }).isFalse()
    }

    /**
     * Replays the capture through [chunk] on a new repository and returns the presentation and
     * audio transports once they equal what the capture holds at that chunk.
     */
    private suspend fun transportsAfterChunk(chunk: Int): Pair<Transport?, Transport?> =
        coroutineScope {
            val repository = StreamingLiveStateRepository(
                client = client,
                scope = scope,
                watchdogTimeout = 1.seconds,
                reconnectDelay = { 10.milliseconds },
                log = {}
            )
            fake.enqueueStream(fake.stream(STAGE_10_ITEMS, StreamEnd.STALL, timeScale = 0.0, chunkLimit = chunk))
            val events = StreamReplay.chunks(STAGE_10_ITEMS, timeScale = 0.0).take(chunk)
                .flatMap { StatusFrameParser().events(it.bytes) }
            val expectedPresentation = events.filterIsInstance<StatusEvent.PresentationTransport>().last().transport
            val expectedAudio = events.filterIsInstance<StatusEvent.AudioTransport>().last().transport
            val collector = launch { repository.liveState.collect {} }
            val presentation =
                withTimeout(5.seconds) { repository.presentationTransport.first { it == expectedPresentation } }
            val audio = withTimeout(5.seconds) { repository.audioTransport.first { it == expectedAudio } }
            collector.cancel()
            presentation to audio
        }

    private companion object {
        const val STAGE_10_ITEMS = "stage10-items"
        const val AUDIO_ITEM_LIVE_CHUNK = 16
        const val MEDIA_ITEM_LIVE_CHUNK = 26
        const val CLEARED_CHUNK = 39
        const val RESTORED_CHUNK = 40
    }
}
