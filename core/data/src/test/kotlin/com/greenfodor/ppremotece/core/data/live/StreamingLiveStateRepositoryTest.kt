package com.greenfodor.ppremotece.core.data.live

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamEnd
import com.greenfodor.ppremotece.core.data.network.StreamReplay
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.SlideText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StreamingLiveStateRepositoryTest {
    @StartStop
    private val server = MockWebServer()

    private val fake = FakeProPresenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: KtorProPresenterClient
    private lateinit var repository: StreamingLiveStateRepository

    private val liveItem = PlaylistItemKey(playlistUuid = FakeProPresenter.SERVICE_PLAYLIST_UUID, index = 4)
    private val liveSlide = LiveSlide(presentationUuid = FakeProPresenter.SONG_A_UUID, index = 3, totalCues = 7)
    private val lastLiveCue = LiveCue(liveItem, FakeProPresenter.SONG_A_UUID, cueIndex = 3)

    @BeforeEach
    fun setUp() {
        server.dispatcher = fake
        client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        runBlocking { client.version() }
        repository = repositoryWithWatchdog(RELAXED_WATCHDOG)
    }

    private fun repositoryWithWatchdog(watchdog: Duration) =
        StreamingLiveStateRepository(
            client = client,
            scope = scope,
            watchdogTimeout = watchdog,
            reconnectDelay = { 10.milliseconds }
        )

    @AfterEach
    fun tearDown() {
        fake.releaseStalls()
        scope.cancel()
    }

    @Test
    fun `heartbeats keep one connection alive`() = runBlocking {
        repository = repositoryWithWatchdog(WATCHDOG)
        val delivered = AtomicInteger()
        fake.enqueueStream(fake.stream("su-long", StreamEnd.STALL, delivered = delivered))

        repository.liveState.test(timeout = 5.seconds) {
            assertThat(
                awaitUntil {
                    it.connection == ConnectionStatus.CONNECTED && it.item != null && it.slide != null
                }
            )
                .isEqualTo(
                    LiveState(ConnectionStatus.CONNECTED, liveItem, liveSlide, SlideText("Chorus · 1", "Chorus · 2"))
                )
            awaitCondition { delivered.get() == StreamReplay.chunkCount("su-long") }
            assertThat(fake.count("POST", "/v1/status/updates")).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each slide change re-reads the slide index`() = runBlocking {
        val delivered = AtomicInteger()
        fake.enqueueStream(fake.stream("su-long", StreamEnd.STALL, delivered = delivered))

        repository.liveState.test(timeout = 5.seconds) {
            val slideChanges = StreamReplay.frameCount("su-long", "status/slide")
            awaitCondition { delivered.get() == StreamReplay.chunkCount("su-long") }
            delay(50.milliseconds)
            assertThat(fake.count("GET", SLIDE_INDEX)).isBetween(1, slideChanges)
            assertThat(fake.count("POST", "/v1/status/updates")).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `silent host is reported as reconnecting`() = runBlocking {
        repository = repositoryWithWatchdog(WATCHDOG)
        fake.enqueueStream(fake.stream("status-updates", StreamEnd.STALL))

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.connection == ConnectionStatus.CONNECTED }
            awaitUntil { it.connection == ConnectionStatus.RECONNECTING }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `silent stall reconnects after the watchdog, resubscribes and re-reads the slide index`() = runBlocking {
        repository = repositoryWithWatchdog(WATCHDOG)
        val firstDelivered = AtomicInteger()
        fake.enqueueStream(fake.stream("status-updates", StreamEnd.STALL, delivered = firstDelivered))
        fake.enqueueStream(fake.stream("su-long", StreamEnd.STALL))

        repository.liveState.test(timeout = 5.seconds) {
            awaitCondition { firstDelivered.get() == StreamReplay.chunkCount("status-updates") }
            val lastChunkAt = System.nanoTime()
            val readsBeforeStall = fake.count("GET", SLIDE_INDEX)
            awaitCondition { fake.count("POST", "/v1/status/updates") == 2 }
            awaitCondition { fake.count("GET", SLIDE_INDEX) > readsBeforeStall }
            val secondPostAt = fake.arrivals[fake.requests.indexOfLast { it.method == "POST" }]
            assertThat((secondPostAt - lastChunkAt) / 1_000_000).isGreaterThanOrEqualTo(250L)
            val bodies = fake.requests.filter { it.method == "POST" }.map { it.body?.utf8() }
            assertThat(bodies).containsExactly(SUBSCRIPTIONS_BODY, SUBSCRIPTIONS_BODY)
            cancelAndIgnoreRemainingEvents()
        }
        FakeProPresenter.assertOnlyAllowedRequests(fake.requests)
    }

    @Test
    fun `end of stream reconnects`() = runBlocking {
        fake.enqueueStream(fake.stream("status-updates", StreamEnd.EOF))
        fake.enqueueStream(fake.stream("su-long", StreamEnd.STALL))

        repository.liveState.test(timeout = 5.seconds) {
            awaitCondition { fake.count("POST", "/v1/status/updates") == 2 }
            awaitUntil { it.connection == ConnectionStatus.CONNECTED }
            cancelAndIgnoreRemainingEvents()
        }
        FakeProPresenter.assertOnlyAllowedRequests(fake.requests)
    }

    @Test
    fun `a reconnect is reported once the new stream delivers`() = runBlocking {
        val reconnects = AtomicInteger()
        val secondDelivered = AtomicInteger()
        repository = StreamingLiveStateRepository(
            client = client,
            scope = scope,
            watchdogTimeout = WATCHDOG,
            reconnectDelay = { 10.milliseconds },
            onReconnected = { reconnects.incrementAndGet() }
        )
        fake.enqueueStream(fake.stream("status-updates", StreamEnd.STALL))
        fake.enqueueStream(fake.stream("su-long", StreamEnd.STALL, delivered = secondDelivered))

        repository.liveState.test(timeout = 5.seconds) {
            awaitCondition { secondDelivered.get() > 0 }
            delay(50.milliseconds)
            assertThat(reconnects.get()).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed slide index read is retried on the next chunk`() = runBlocking {
        fake.failSlideIndexReads = 1
        fake.enqueueStream(fake.stream("su-long", StreamEnd.STALL))

        repository.liveState.test(timeout = 5.seconds) {
            assertThat(awaitUntil { it.slide != null }.slide).isEqualTo(liveSlide)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resubscribing after all collectors left starts from the initial state`() = runBlocking {
        fake.enqueueStream(fake.stream("su-long", StreamEnd.STALL))
        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.connection == ConnectionStatus.CONNECTED }
            cancelAndIgnoreRemainingEvents()
        }
        delay(100.milliseconds)

        repository.liveState.test(timeout = 5.seconds) {
            assertThat(awaitItem()).isEqualTo(LiveState.Initial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `slide text from the stream reaches the live state`() = runBlocking {
        fake.enqueueStream(fake.stream("stage5-status-updates", StreamEnd.STALL))

        repository.liveState.test(timeout = 5.seconds) {
            assertThat(awaitUntil { it.slideText != null }.slideText)
                .isEqualTo(SlideText(current = "Text 03", next = "Text 04"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the last live cue is kept through a clear`() = runBlocking {
        fake.slideIndexBodies += listOf(FakeProPresenter.SLIDE_INDEX, FakeProPresenter.NO_SLIDE_INDEX)
        fake.enqueueStream(
            fake.frames(
                listOf(FakeProPresenter.playlistActiveFrame(liveItem), FakeProPresenter.SLIDE_FRAME),
                listOf(FakeProPresenter.playlistActiveFrame(null))
            )
        )

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.slide != null }
            assertThat(repository.lastLive.value).isEqualTo(lastLiveCue)
            awaitUntil { it.item == null && it.slide == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value).isEqualTo(lastLiveCue)
    }

    @Test
    fun `a live slide of another presentation than the live item's is not remembered`() = runBlocking {
        fake.enqueueStream(
            fake.frames(
                listOf(
                    FakeProPresenter.playlistActiveFrame(liveItem, "other-presentation"),
                    FakeProPresenter.SLIDE_FRAME
                )
            )
        )

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.slide != null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value).isNull()
    }

    @Test
    fun `the last live cue is kept while nothing collects the live state`() = runBlocking {
        fake.enqueueStream(
            fake.frames(listOf(FakeProPresenter.playlistActiveFrame(liveItem), FakeProPresenter.SLIDE_FRAME))
        )
        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.slide != null }
            cancelAndIgnoreRemainingEvents()
        }
        delay(100.milliseconds)

        assertThat(repository.lastLive.value).isEqualTo(lastLiveCue)
        repository.liveState.test(timeout = 5.seconds) {
            assertThat(awaitItem()).isEqualTo(LiveState.Initial)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value).isEqualTo(lastLiveCue)
    }

    @Test
    fun `reconnect delay doubles from half a second and is capped at ten seconds`() {
        assertThat((0..6).map { defaultReconnectDelay(it).inWholeMilliseconds })
            .containsExactly(500L, 1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L)
    }

    private suspend fun ReceiveTurbine<LiveState>.awaitUntil(predicate: (LiveState) -> Boolean): LiveState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!condition()) delay(5.milliseconds)
        }
    }

    private companion object {
        val WATCHDOG = 300.milliseconds
        val RELAXED_WATCHDOG = 1.seconds
        const val SLIDE_INDEX = "/v1/presentation/slide_index"
        const val SUBSCRIPTIONS_BODY = """["status/slide","timer/system_time","playlist/active"]"""
    }
}
