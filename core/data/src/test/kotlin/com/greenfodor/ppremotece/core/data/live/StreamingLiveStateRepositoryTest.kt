package com.greenfodor.ppremotece.core.data.live

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.data.network.CapturedLiveBodies
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamEnd
import com.greenfodor.ppremotece.core.data.network.StreamReplay
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
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
import java.util.concurrent.TimeUnit
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
    private val lastLiveCue = LiveCue(CueSource.PlaylistItem(liveItem), FakeProPresenter.SONG_A_UUID, cueIndex = 3)

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
        fake.slideIndexBodies += (1..3).map { FakeProPresenter.SLIDE_INDEX.replace("\"index\":3,", "\"index\":$it,") }
        fake.enqueueStream(
            fake.frames(
                listOf(FakeProPresenter.playlistActiveFrame(liveItem), FakeProPresenter.SLIDE_FRAME),
                listOf(FakeProPresenter.SLIDE_FRAME),
                listOf(FakeProPresenter.SLIDE_FRAME),
                awaitSlideReads = true
            )
        )

        repository.liveState.test(timeout = 5.seconds) {
            assertThat(awaitUntil { it.slide?.index == 3 }.item).isEqualTo(liveItem)
            cancelAndIgnoreRemainingEvents()
        }
        val reads = generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }
            .map { "${it.method} ${it.url.encodedPath}" }
            .filter { it != "GET /version" }
            .take(7)
            .toList()
        assertThat(reads.first()).isEqualTo("POST /v1/status/updates")
        assertThat(reads.drop(1).groupingBy { it }.eachCount())
            .isEqualTo(mapOf("GET $SLIDE_INDEX" to 3, "GET $PLAYLIST_ACTIVE" to 3))
    }

    @Test
    fun `a slide of item 1 read before item 1's playlist active frame is attributed to item 1`() = runBlocking {
        replayStage7Probe(throughChunk = ITEM_1_SLIDE_INDEX_CHUNK)
        val item1 = PlaylistItemKey(ARRANGEMENT_TEST_PLAYLIST_UUID, index = 1)

        repository.liveState.test(timeout = 5.seconds) {
            val live = awaitUntil { it.slide?.totalCues == 7 }
            assertThat(live.item).isEqualTo(item1)
            assertThat(live.slide).isEqualTo(LiveSlide(FakeProPresenter.SONG_A_UUID, index = 2, totalCues = 7))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value)
            .isEqualTo(LiveCue(CueSource.PlaylistItem(item1), FakeProPresenter.SONG_A_UUID, cueIndex = 2))
    }

    @Test
    fun `a presentation route slide read before its playlist active frame has a presentation source`() =
        runBlocking {
            replayStage7Probe(throughChunk = PRESENTATION_ROUTE_SLIDE_INDEX_CHUNK)

            repository.liveState.test(timeout = 5.seconds) {
                val live = awaitUntil { it.slide?.totalCues == 15 }
                assertThat(live.item).isNull()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(repository.lastLive.value)
                .isEqualTo(
                    LiveCue(CueSource.Presentation(FakeProPresenter.SONG_A_UUID), FakeProPresenter.SONG_A_UUID, 3)
                )
        }

    @Test
    fun `a same item step without a playlist active frame keeps the item`() = runBlocking {
        replayStage7Probe(throughChunk = SAME_ITEM_SLIDE_INDEX_CHUNK)
        val item0 = PlaylistItemKey(ARRANGEMENT_TEST_PLAYLIST_UUID, index = 0)

        repository.liveState.test(timeout = 5.seconds) {
            val live = awaitUntil { it.slide?.index == 1 }
            assertThat(live.item).isEqualTo(item0)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value)
            .isEqualTo(LiveCue(CueSource.PlaylistItem(item0), FakeProPresenter.SONG_A_UUID, cueIndex = 1))
    }

    private fun replayStage7Probe(throughChunk: Int) {
        val delivered = AtomicInteger()
        val captured = CapturedLiveBodies(STAGE_7_PROBE)
        fake.liveBodies = { captured.after(delivered.get()) }
        fake.enqueueStream(
            fake.stream(
                STAGE_7_PROBE,
                StreamEnd.STALL,
                timeScale = 0.05,
                delivered = delivered,
                chunkLimit = throughChunk
            )
        )
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
    fun `a slide read is applied only together with a successful playlist active read`() = runBlocking {
        fake.failPlaylistActiveReads = 1
        fake.enqueueStream(
            fake.frames(
                listOf(FakeProPresenter.playlistActiveFrame(liveItem), FakeProPresenter.SLIDE_FRAME),
                listOf(FakeProPresenter.HEARTBEAT_FRAME),
                awaitSlideReads = true
            )
        )

        repository.liveState.test(timeout = 5.seconds) {
            assertThat(awaitUntil { it.slide != null }.slide).isEqualTo(liveSlide)
            assertThat(fake.count("GET", PLAYLIST_ACTIVE)).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value).isEqualTo(lastLiveCue)
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
    fun `layers with content reach the live state`() = runBlocking {
        fake.enqueueStream(fake.stream("stage5-status-updates", StreamEnd.STALL))

        repository.liveState.test(timeout = 5.seconds) {
            assertThat(awaitUntil { it.layers.isNotEmpty() }.layers).isEqualTo(setOf(OutputLayer.SLIDE))
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
    fun `a presentation live outside a playlist is remembered through a clear`() = runBlocking {
        fake.slideIndexBodies += listOf(FakeProPresenter.SLIDE_INDEX, FakeProPresenter.NO_SLIDE_INDEX)
        fake.enqueueStream(
            fake.frames(
                listOf(FakeProPresenter.playlistActiveFrame(null), FakeProPresenter.SLIDE_FRAME),
                listOf(FakeProPresenter.playlistActiveFrame(null), FakeProPresenter.SLIDE_FRAME)
            )
        )
        val remembered = LiveCue(CueSource.Presentation(FakeProPresenter.SONG_A_UUID), FakeProPresenter.SONG_A_UUID, 3)

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.slide != null }
            assertThat(repository.lastLive.value).isEqualTo(remembered)
            awaitUntil { it.slide == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value).isEqualTo(remembered)
    }

    @Test
    fun `the stage 6 capture of a presentation trigger leaves no live item and song A's slide`() = runBlocking {
        val songA15 = FakeProPresenter.SLIDE_INDEX.replace("\"total_cues\":7", "\"total_cues\":15")
        repeat(SLIDE_READS) { fake.slideIndexBodies += songA15 }
        val delivered = AtomicInteger()
        fake.enqueueStream(fake.stream("stage6-probe", StreamEnd.STALL, delivered = delivered))

        repository.liveState.test(timeout = 5.seconds) {
            awaitCondition { delivered.get() == StreamReplay.chunkCount("stage6-probe") }
            val live = awaitUntil { it.item == null && it.slide != null }
            assertThat(live.slide).isEqualTo(LiveSlide(FakeProPresenter.SONG_A_UUID, index = 3, totalCues = 15))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value)
            .isEqualTo(LiveCue(CueSource.Presentation(FakeProPresenter.SONG_A_UUID), FakeProPresenter.SONG_A_UUID, 3))
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
        const val PLAYLIST_ACTIVE = "/v1/playlist/active"
        const val STAGE_7_PROBE = "stage7-probe"
        const val ARRANGEMENT_TEST_PLAYLIST_UUID = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90"
        const val ITEM_1_SLIDE_INDEX_CHUNK = 10
        const val PRESENTATION_ROUTE_SLIDE_INDEX_CHUNK = 16
        const val SAME_ITEM_SLIDE_INDEX_CHUNK = 28
        const val SLIDE_READS = 10
        const val SUBSCRIPTIONS_BODY = """["status/slide","timer/system_time","playlist/active","status/layers"]"""
    }
}
