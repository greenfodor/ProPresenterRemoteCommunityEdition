package com.greenfodor.ppremotece.core.data.content

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.PlaylistNotFoundException
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The playlist's change connection: when it is open, and what a signal, an end and a reconnect do. */
class PlaylistChangesTest {
    @StartStop
    private val server = MockWebServer()

    private val host = PlaylistHost()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = MutableStateFlow<String?>("1@host-a")
    private val reconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val opened = AtomicInteger()
    private val closed = AtomicInteger()
    private val changesNoted = AtomicInteger()

    @Volatile
    private var ends = emptyList<End>()

    @Volatile
    private var streamConnected = true

    private lateinit var repository: CachingContentRepository

    private enum class End { COMPLETES, NOT_FOUND }

    @BeforeEach
    fun setUp() {
        server.dispatcher = host
        val real = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        val client = object : ProPresenterClient by real {
            override fun playlistChanges(uuid: String): Flow<Unit> =
                flow {
                    val end = ends.getOrNull(opened.getAndIncrement())
                    try {
                        when (end) {
                            End.COMPLETES -> Unit
                            End.NOT_FOUND -> throw PlaylistNotFoundException()
                            null -> emitAll(signals)
                        }
                    } finally {
                        closed.incrementAndGet()
                    }
                }
        }
        repository = CachingContentRepository(
            client = client,
            session = session,
            staleSignals = reconnects,
            scope = scope,
            restore = {},
            onPlaylistChanged = { changesNoted.incrementAndGet() },
            isStreamConnected = { streamConnected },
            retryDelay = { 50.milliseconds }
        )
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a change signal re-reads the playlist once and emits the new list`() = runBlocking<Unit> {
        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            assertThat(awaitItem().name()).isEqualTo(ORIGINAL_NAME)
            awaitCondition { opened.get() == 1 }
            host.playlistName = "Renamed"

            signals.emit(Unit)

            assertThat(awaitItem().name()).isEqualTo("Renamed")
            awaitCondition { changesNoted.get() == 1 }
        }
        assertThat(host.reads.get()).isEqualTo(2)
    }

    @Test
    fun `a change signal with the same list emits nothing`() = runBlocking<Unit> {
        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            awaitItem()
            awaitCondition { opened.get() == 1 }

            signals.emit(Unit)

            awaitCondition { host.reads.get() == 2 && changesNoted.get() == 1 }
            expectNoEvents()
        }
    }

    @Test
    fun `the connection is open only while the playlist is collected`() = runBlocking<Unit> {
        repository.refreshPlaylist(PLAYLIST)
        assertThat(opened.get()).isEqualTo(0)

        val first = launch { repository.playlist(PLAYLIST).collect {} }
        val second = launch { repository.playlist(PLAYLIST).collect {} }
        awaitCondition { opened.get() == 1 && host.reads.get() >= 2 }
        first.cancel()
        delay(100.milliseconds)
        assertThat(closed.get()).isEqualTo(0)

        second.cancel()

        awaitCondition { closed.get() == 1 }
        assertThat(opened.get()).isEqualTo(1)
    }

    @Test
    fun `a stream reconnect reopens the connection with one re-read`() = runBlocking<Unit> {
        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            awaitItem()
            awaitCondition { opened.get() == 1 }
            host.playlistName = "Renamed"

            reconnects.emit(Unit)

            assertThat(awaitItem().name()).isEqualTo("Renamed")
            awaitCondition { opened.get() == 2 && closed.get() == 1 }
        }
        assertThat(host.reads.get()).isEqualTo(2)
    }

    @Test
    fun `a connection that ends is reopened with a re-read while the stream is connected`() = runBlocking<Unit> {
        ends = listOf(End.COMPLETES)

        val collector = launch { repository.playlist(PLAYLIST).collect {} }

        awaitCondition { opened.get() == 2 && host.reads.get() == 2 }
        collector.cancel()
    }

    @Test
    fun `a connection that ends is reopened only once the stream is connected`() = runBlocking<Unit> {
        ends = listOf(End.COMPLETES)
        streamConnected = false

        val collector = launch { repository.playlist(PLAYLIST).collect {} }
        withTimeout(5.seconds) { repository.playlist(PLAYLIST).first() }
        awaitCondition { closed.get() == 1 }
        delay(300.milliseconds)
        assertThat(opened.get()).isEqualTo(1)

        streamConnected = true

        awaitCondition { opened.get() == 2 }
        collector.cancel()
    }

    @Test
    fun `a change signal is followed by a read that starts after it`() = runBlocking<Unit> {
        host.delayMillis = 300
        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            awaitCondition { opened.get() == 1 && host.reads.get() == 1 }
            host.playlistName = "Renamed"

            signals.emit(Unit)

            assertThat(awaitItem().name()).isEqualTo(ORIGINAL_NAME)
            assertThat(awaitItem().name()).isEqualTo("Renamed")
        }
        assertThat(host.reads.get()).isEqualTo(2)
    }

    @Test
    fun `a playlist ProPresenter does not know is not asked again`() = runBlocking<Unit> {
        ends = listOf(End.NOT_FOUND)

        val collector = launch { repository.playlist(PLAYLIST).collect {} }
        awaitCondition { closed.get() == 1 }
        delay(300.milliseconds)

        assertThat(opened.get()).isEqualTo(1)
        collector.cancel()
    }

    private fun Result<Playlist, DataError.Network>.name(): String = (this as Result.Success).data.name

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!condition()) delay(10.milliseconds)
        }
    }

    private class PlaylistHost : Dispatcher() {
        @Volatile
        var playlistName = ORIGINAL_NAME

        @Volatile
        var delayMillis = 0L

        val reads = AtomicInteger()

        override fun dispatch(request: RecordedRequest): MockResponse =
            if (request.url.encodedPath == "/v1/playlist/$PLAYLIST") {
                reads.incrementAndGet()
                MockResponse.Builder()
                    .addHeader("Content-Type", "application/json")
                    .headersDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .body(
                        Fixtures.text(Fixtures.ARRANGEMENT_TEST_PLAYLIST)
                            .replace("\"$ORIGINAL_NAME\"", "\"$playlistName\"")
                    ).build()
            } else {
                MockResponse.Builder().code(404).build()
            }
    }

    private companion object {
        const val PLAYLIST = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90"
        const val ORIGINAL_NAME = "Arrangement Test"
    }
}
