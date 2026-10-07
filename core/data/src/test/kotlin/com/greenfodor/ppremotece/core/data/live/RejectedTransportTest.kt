package com.greenfodor.ppremotece.core.data.live

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamEnd
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.orNull
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

/** What a rejected transport or active-audio url leaves behind, and the live read asked for from outside. */
class RejectedTransportTest {
    @StartStop
    private val server = MockWebServer()

    private val fake = FakeProPresenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: StreamingLiveStateRepository

    @BeforeEach
    fun setUp() {
        server.dispatcher = fake
        val client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        repository = StreamingLiveStateRepository(
            client = client,
            scope = scope,
            watchdogTimeout = 10.seconds,
            reconnectDelay = { 10.milliseconds },
            log = {}
        )
    }

    @AfterEach
    fun tearDown() {
        fake.releaseStalls()
        scope.cancel()
    }

    @Test
    fun `a rejected presentation transport url leaves that transport unavailable`() = runBlocking<Unit> {
        rejectAfterLoading("transport/presentation/current")

        assertThat(repository.audioTransport.value.orNull()?.name).isEqualTo("Media 03")
        assertThat(repository.audioPosition.value).isEqualTo(83.0)
        assertThat(repository.activeAudio.value).isEqualTo(ACTIVE)
    }

    @Test
    fun `a rejected audio transport url clears the audio transport and its position`() = runBlocking<Unit> {
        rejectAfterLoading("transport/audio/current")

        assertThat(withTimeout(5.seconds) { repository.audioPosition.first { it == null } }).isNull()
        assertThat(repository.presentationTransport.value.orNull()?.name).isEqualTo("Media 01")
    }

    @Test
    fun `a rejected audio time url clears the audio transport and its position`() = runBlocking<Unit> {
        rejectAfterLoading("transport/audio/time")

        assertThat(withTimeout(5.seconds) { repository.audioPosition.first { it == null } }).isNull()
        assertThat(repository.presentationTransport.value.orNull()?.name).isEqualTo("Media 01")
    }

    @Test
    fun `a rejected active audio url clears the active track`() = runBlocking<Unit> {
        fake.enqueueStream(fake.frames(LOADED, listOf(rejection("audio/playlist/active")), end = StreamEnd.EOF))
        val collector = launch { repository.liveState.collect {} }

        withTimeout(5.seconds) { repository.activeAudio.first { it == ACTIVE } }
        assertThat(withTimeout(5.seconds) { repository.activeAudio.first { it == null } }).isNull()
        collector.cancel()

        assertThat(repository.audioTransport.value.orNull()?.name).isEqualTo("Media 03")
    }

    @Test
    fun `a live read asked for from outside re-reads the slide index and the live item`() = runBlocking<Unit> {
        val heartbeats = Array(HEARTBEATS) { listOf(FakeProPresenter.HEARTBEAT_FRAME) }
        fake.enqueueStream(fake.frames(*heartbeats, delayMillis = 100))

        repository.liveState.test(timeout = 10.seconds) {
            while (awaitItem().slide?.index != 3) Unit
            fake.slideIndexBodies += FakeProPresenter.SLIDE_INDEX.replace("\"index\":3", "\"index\":5")

            repository.requestLiveRead()

            while (awaitItem().slide?.index != 5) Unit
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(fake.count("GET", "/v1/presentation/slide_index")).isEqualTo(2)
        assertThat(fake.count("GET", "/v1/playlist/active")).isEqualTo(2)
    }

    /** Loads both transports, the position and the active track, then rejects [url]. */
    private suspend fun rejectAfterLoading(url: String) {
        fake.enqueueStream(fake.frames(LOADED, listOf(rejection(url)), end = StreamEnd.EOF))
        val collector = scope.launch { repository.liveState.collect {} }
        val rejected = if (url == "transport/presentation/current") {
            repository.presentationTransport
        } else {
            repository.audioTransport
        }
        assertThat(withTimeout(5.seconds) { rejected.first { it == Loadable.Unavailable } })
            .isEqualTo(Loadable.Unavailable)
        collector.cancel()
    }

    private fun rejection(url: String) = """["URL: $url. Error: 404 Not Found"]"""

    private companion object {
        const val HEARTBEATS = 60
        val ACTIVE = ActiveAudio(playlistUuid = "ap-0", trackUuid = "tr-2", trackIndex = 2)
        const val PRESENTATION_TRANSPORT_FRAME = """{"url":"transport/presentation/current","data":""" +
            """{"is_playing":true,"uuid":"m-0","name":"Media 01","artist":"","audio_only":false,"duration":20.0}}"""
        const val AUDIO_TRANSPORT_FRAME = """{"url":"transport/audio/current","data":""" +
            """{"is_playing":false,"uuid":"a-0","name":"Media 03","artist":"Artist 01",""" +
            """"audio_only":true,"duration":183.5}}"""
        const val AUDIO_TIME_FRAME = """{"url":"transport/audio/time","data":83.0}"""
        const val ACTIVE_AUDIO_FRAME = """{"url":"audio/playlist/active","data":""" +
            """{"playlist":{"uuid":"ap-0","name":"Audio Playlist 01","index":0},""" +
            """"item":{"uuid":"tr-2","name":"Track 03","index":2}}}"""
        val LOADED = listOf(PRESENTATION_TRANSPORT_FRAME, AUDIO_TRANSPORT_FRAME, AUDIO_TIME_FRAME, ACTIVE_AUDIO_FRAME)
    }
}
