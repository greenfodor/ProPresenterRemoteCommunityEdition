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
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.looks.liveLook
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CountDownTarget
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PropCollection
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.TimerState
import com.greenfodor.ppremotece.core.domain.model.TimerType
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import com.greenfodor.ppremotece.core.domain.status.StatusFrameParser
import com.greenfodor.ppremotece.core.domain.timers.LiveTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import java.util.concurrent.CopyOnWriteArrayList
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

    private val logged = CopyOnWriteArrayList<String>()

    private fun repositoryWithWatchdog(watchdog: Duration) =
        StreamingLiveStateRepository(
            client = client,
            scope = scope,
            watchdogTimeout = watchdog,
            reconnectDelay = { 10.milliseconds },
            log = { logged += it }
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
        replayProbe(STAGE_7_PROBE, throughChunk = ITEM_1_SLIDE_INDEX_CHUNK)
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
            replayProbe(STAGE_7_PROBE, throughChunk = PRESENTATION_ROUTE_SLIDE_INDEX_CHUNK)

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
        replayProbe(STAGE_7_PROBE, throughChunk = SAME_ITEM_SLIDE_INDEX_CHUNK)
        val item0 = PlaylistItemKey(ARRANGEMENT_TEST_PLAYLIST_UUID, index = 0)

        repository.liveState.test(timeout = 5.seconds) {
            val live = awaitUntil { it.slide?.index == 1 }
            assertThat(live.item).isEqualTo(item0)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.lastLive.value)
            .isEqualTo(LiveCue(CueSource.PlaylistItem(item0), FakeProPresenter.SONG_A_UUID, cueIndex = 1))
    }

    /**
     * Replays capture [name] through [throughChunk]; every slide index and playlist active read is
     * answered with the captured state after that chunk.
     */
    private fun replayProbe(name: String, throughChunk: Int) {
        val bodies = CapturedLiveBodies(name).after(throughChunk)
        fake.liveBodies = { bodies }
        fake.enqueueStream(fake.stream(name, StreamEnd.STALL, timeScale = 0.05, chunkLimit = throughChunk))
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
        replayProbe(STAGE_6_PROBE, throughChunk = STAGE_6_TRIGGER_CHUNK)

        repository.liveState.test(timeout = 5.seconds) {
            val live = awaitUntil { it.slide?.totalCues == 15 }
            assertThat(live.item).isNull()
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
    fun `the stream subscribes to exactly the live and timer urls`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(FakeProPresenter.HEARTBEAT_FRAME)))

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.connection == ConnectionStatus.CONNECTED }
            cancelAndIgnoreRemainingEvents()
        }

        val stream = generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }
            .first { it.url.encodedPath == "/v1/status/updates" }
        assertThat(stream.body?.utf8()).isEqualTo(SUBSCRIPTIONS_BODY)
    }

    @Test
    fun `the stage 8 capture joins each timer with its reading`() = runBlocking {
        val timers = timersAfterChunk(TIMERS_READ_CHUNK) { it.size == 3 && it.all { timer -> timer.reading != null } }

        assertThat(timers.map { it.timer.name to it.timer.type }).containsExactly(
            "Timer 01" to TimerType.COUNTDOWN_TO_TIME,
            "Timer 02" to TimerType.COUNTDOWN_TO_TIME,
            "Timer 03" to TimerType.ELAPSED
        )
        assertThat(timers.map { it.reading }).containsExactly(
            TimerReading(TIMER_0, "17:14:53", TimerState.STOPPED),
            TimerReading("c5fcf5b0-ee31-4143-9d54-6514d922b2bb", "11:50:00", TimerState.STOPPED),
            TimerReading("9bf671d2-475a-4716-af17-2ab73589734d", "00:00:00", TimerState.STOPPED)
        )
    }

    @Test
    fun `the stage 8 capture shows timer 1 running with its latest readout`() = runBlocking {
        val timers = timersAfterChunk(TIMER_RUNNING_CHUNK) { it.firstOrNull()?.reading?.time == "17:05:20" }

        assertThat(timers.first().reading).isEqualTo(TimerReading(TIMER_0, "17:05:20", TimerState.RUNNING))
    }

    @Test
    fun `the stage 8 capture ends with timer 1 stopped at its frozen readout`() = runBlocking {
        val timers = timersAfterChunk(TIMER_STOPPED_CHUNK) { it.firstOrNull()?.reading?.time == "17:05:18" }

        assertThat(timers.first().reading).isEqualTo(TimerReading(TIMER_0, "17:05:18", TimerState.STOPPED))
    }

    @Test
    fun `the stage 8 capture lists the macro collection with its macros and colours`() = runBlocking {
        fake.enqueueStream(
            fake.stream(STAGE_8_TIMERS, StreamEnd.STALL, timeScale = 0.0, chunkLimit = MACROS_READ_CHUNK)
        )
        val collector = launch { repository.liveState.collect {} }
        val collections = withTimeout(5.seconds) { repository.collections.loaded { it.isNotEmpty() } }
        collector.cancel()

        val collection = collections.single()
        assertThat(collection.name).isEqualTo("Collection 01")
        assertThat(collection.macros.map { it.name })
            .containsExactly("Macro 01", "Macro 02", "Macro 03", "Macro 04", "Macro 05")
        assertThat(collection.macros.first().color)
            .isEqualTo(GroupColor(red = 0.09019608f, green = 0.49803922f, blue = 1f, alpha = 1f))
    }

    @Test
    fun `the stage 9 capture shows timer 2 overrunning with a negative time`() = runBlocking {
        val timers = timersAfterChunk(OVERRUNNING_CHUNK, STAGE_9_OVERRUN) {
            it.getOrNull(1)?.reading?.time ==
                "-00:34:45"
        }

        assertThat(timers[1].reading).isEqualTo(TimerReading(TIMER_2, "-00:34:45", TimerState.OVERRUNNING))
    }

    @Test
    fun `the stage 9 capture ends with timer 2 overran`() = runBlocking {
        val timers = timersAfterChunk(OVERRAN_CHUNK, STAGE_9_OVERRUN) {
            it.getOrNull(1)?.reading?.state ==
                TimerState.OVERRAN
        }

        assertThat(timers[1].reading).isEqualTo(TimerReading(TIMER_2, "-00:34:47", TimerState.OVERRAN))
    }

    @Test
    fun `the stage 9 capture reads each count-down-to-time target`() = runBlocking {
        val timers = timersAfterChunk(STAGE_9_TIMERS_CHUNK, STAGE_9_OVERRUN) { it.size == 3 }

        assertThat(timers.map { it.timer.target }).containsExactly(
            CountDownTarget(timeOfDaySeconds = 30600, period = "pm"),
            CountDownTarget(timeOfDaySeconds = 42600, period = "is_24_hour"),
            null
        )
    }

    @Test
    fun `timers and macros are not loaded before their first frame`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(FakeProPresenter.HEARTBEAT_FRAME)))

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.connection == ConnectionStatus.CONNECTED }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.timers.value).isEqualTo(Loadable.NotLoaded)
        assertThat(repository.collections.value).isEqualTo(Loadable.NotLoaded)
    }

    @Test
    fun `empty timers and macros frames load empty lists`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(EMPTY_TIMERS_FRAME, EMPTY_MACROS_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        assertThat(withTimeout(5.seconds) { repository.timers.first { it is Loadable.Loaded } })
            .isEqualTo(Loadable.Loaded(emptyList()))
        assertThat(withTimeout(5.seconds) { repository.collections.first { it is Loadable.Loaded } })
            .isEqualTo(Loadable.Loaded(emptyList()))
        collector.cancel()
    }

    @Test
    fun `timers and macros are kept across a reconnect`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(ONE_TIMER_FRAME, ONE_MACRO_FRAME), end = StreamEnd.EOF))
        fake.enqueueStream(fake.frames(listOf(FakeProPresenter.HEARTBEAT_FRAME)))

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.connection == ConnectionStatus.RECONNECTING }
            awaitUntil { it.connection == ConnectionStatus.CONNECTED }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.timers.loaded { true }.single().timer.name).isEqualTo("Timer 01")
        assertThat(repository.collections.loaded { true }.single().name).isEqualTo("Collection 01")
    }

    @Test
    fun `an error frame resubscribes without the rejected url and marks its tab unavailable`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(MACROS_REJECTED_FRAME), end = StreamEnd.EOF))
        fake.enqueueStream(fake.frames(listOf(FakeProPresenter.HEARTBEAT_FRAME, ONE_TIMER_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        assertThat(withTimeout(5.seconds) { repository.collections.first { it == Loadable.Unavailable } })
            .isEqualTo(Loadable.Unavailable)
        withTimeout(5.seconds) { repository.timers.first { it is Loadable.Loaded } }
        collector.cancel()

        val bodies = server.recordedStreamBodies()
        assertThat(bodies.first()).isEqualTo(SUBSCRIPTIONS_BODY)
        assertThat(bodies[1]).isEqualTo(SUBSCRIPTIONS_WITHOUT_MACROS_BODY)
        assertThat(logged.toList()).containsExactly("URL: macro_collections. Error: 404 Not Found")
    }

    @Test
    fun `timers stay unavailable when only timers current was rejected`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(READINGS_REJECTED_FRAME), end = StreamEnd.EOF))
        fake.enqueueStream(fake.frames(listOf(ONE_TIMER_FRAME, ONE_MACRO_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        withTimeout(5.seconds) { repository.collections.first { it is Loadable.Loaded } }
        collector.cancel()

        assertThat(repository.timers.value).isEqualTo(Loadable.Unavailable)
    }

    @Test
    fun `every url an error frame names is left out`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(TWO_REJECTED_FRAME), end = StreamEnd.EOF))
        fake.enqueueStream(fake.frames(listOf(FakeProPresenter.HEARTBEAT_FRAME)))

        repository.liveState.test(timeout = 5.seconds) {
            awaitUntil { it.connection == ConnectionStatus.RECONNECTING }
            awaitUntil { it.connection == ConnectionStatus.CONNECTED }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(server.recordedStreamBodies()[1]).isEqualTo(
            """["status/slide","timer/system_time","playlist/active","status/layers","timers",""" +
                """"looks","look/current",""" +
                """"prop_collections"]"""
        )
        assertThat(repository.collections.value).isEqualTo(Loadable.Unavailable)
    }

    @Test
    fun `the stage 9 capture lists five looks with look 2 live`() = runBlocking {
        val (looks, current) = looksAfterChunk(LOOKS_READ_CHUNK) { it.size == 5 }

        assertThat(looks.map { it.name }).containsExactly("Look 01", "Look 02", "Look 03", "Look 04", "Look 05")
        assertThat(liveLook(looks, current)).isEqualTo(looks[1])
    }

    @Test
    fun `a one-frame look trigger in the stage 9 capture ends on look 1`() = runBlocking {
        val (looks, current) = looksAfterChunk(ONE_FRAME_TRIGGER_CHUNK) { true }

        assertThat(liveLook(looks, current)).isEqualTo(looks[0])
    }

    @Test
    fun `a two-frame look trigger in the stage 9 capture shows look 2 after each frame`() = runBlocking {
        val (ownUuidLooks, ownUuidCurrent) = looksAfterChunk(OWN_UUID_FRAME_CHUNK) { true }
        val (looks, current) = looksAfterChunk(TWO_FRAME_TRIGGER_CHUNK) { true }

        assertThat(liveLook(ownUuidLooks, ownUuidCurrent)).isEqualTo(ownUuidLooks[1])
        assertThat(liveLook(looks, current)).isEqualTo(looks[1])
    }

    /**
     * Replays the stage 9 looks capture through [chunk] on a new repository and returns the loaded
     * looks matching [settled] with the current look the capture holds at that chunk.
     */
    private suspend fun looksAfterChunk(chunk: Int, settled: (List<Look>) -> Boolean): Pair<List<Look>, Look?> =
        coroutineScope {
            val fresh = repositoryWithWatchdog(RELAXED_WATCHDOG)
            fake.enqueueStream(fake.stream(STAGE_9_LOOKS, StreamEnd.STALL, timeScale = 0.0, chunkLimit = chunk))
            val expected = expectedCurrentLook(chunk)
            val collector = launch { fresh.liveState.collect {} }
            val looks = withTimeout(5.seconds) { fresh.looks.loaded(settled) }
            val current = withTimeout(5.seconds) { fresh.currentLook.first { it == expected } }
            collector.cancel()
            looks to current
        }

    /** The last `look/current` frame of the stage 9 looks capture within its first [chunk] chunks. */
    private fun expectedCurrentLook(chunk: Int): Look? =
        StreamReplay.chunks(STAGE_9_LOOKS, timeScale = 0.0).take(chunk)
            .flatMap { StatusFrameParser().events(it.bytes) }
            .filterIsInstance<StatusEvent.CurrentLook>()
            .lastOrNull()
            ?.look

    @Test
    fun `looks still load when only look current was rejected`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(CURRENT_LOOK_REJECTED_FRAME), end = StreamEnd.EOF))
        fake.enqueueStream(fake.frames(listOf(ONE_LOOK_FRAME)))
        val collector = launch { repository.liveState.collect {} }

        val looks = withTimeout(5.seconds) { repository.looks.loaded { true } }
        collector.cancel()

        assertThat(looks.map { it.name }).containsExactly("Look 01")
        assertThat(repository.currentLook.value).isNull()
    }

    @Test
    fun `the stage 9 capture shows each prop toggle with its active state`() = runBlocking {
        val expected = mapOf(
            PROPS_READ_CHUNK to listOf(false, false, false),
            22 to listOf(true, false, false),
            28 to listOf(true, true, false),
            32 to listOf(true, true, true),
            37 to listOf(false, true, true),
            41 to listOf(false, false, true),
            45 to listOf(false, false, false)
        )

        expected.forEach { (chunk, active) ->
            val collections = propsAfterChunk(chunk) { it.single().props.map { prop -> prop.isActive } == active }
            assertThat(collections.single().props.map { it.isActive }).isEqualTo(active)
        }
    }

    @Test
    fun `the stage 9 capture lists the props with their names and transition`() = runBlocking {
        val collection = propsAfterChunk(PROPS_READ_CHUNK) { true }.single()

        assertThat(collection.name).isEqualTo("Collection 04")
        assertThat(collection.props.map { it.name to it.transitionName }).containsExactly(
            "Prop 01" to null,
            "Prop 02" to "Transition 01",
            "Prop 03" to null
        )
    }

    /** Replays the stage 9 looks and props capture through [chunk] on a new repository. */
    private suspend fun propsAfterChunk(
        chunk: Int,
        settled: (List<PropCollection>) -> Boolean
    ): List<PropCollection> =
        coroutineScope {
            val fresh = repositoryWithWatchdog(RELAXED_WATCHDOG)
            fake.enqueueStream(fake.stream(STAGE_9_LOOKS, StreamEnd.STALL, timeScale = 0.0, chunkLimit = chunk))
            val collector = launch { fresh.liveState.collect {} }
            val collections = withTimeout(5.seconds) { fresh.propCollections.loaded(settled) }
            collector.cancel()
            collections
        }

    /** Replays [capture] through [chunk] and returns the loaded timers once they match [settled]. */
    private suspend fun timersAfterChunk(
        chunk: Int,
        capture: String = STAGE_8_TIMERS,
        settled: (List<LiveTimer>) -> Boolean
    ): List<LiveTimer> =
        coroutineScope {
            fake.enqueueStream(fake.stream(capture, StreamEnd.STALL, timeScale = 0.0, chunkLimit = chunk))
            val collector = launch { repository.liveState.collect {} }
            val timers = withTimeout(5.seconds) { repository.timers.loaded(settled) }
            collector.cancel()
            timers
        }

    /** The first loaded value of this flow that matches [settled]. */
    private suspend fun <T> Flow<Loadable<T>>.loaded(settled: (T) -> Boolean): T =
        filterIsInstance<Loadable.Loaded<T>>().map { it.value }.first(settled)

    /** The bodies of every `status/updates` request so far, in order. */
    private fun MockWebServer.recordedStreamBodies(): List<String?> =
        generateSequence { takeRequest(1, TimeUnit.SECONDS) }
            .filter { it.url.encodedPath == "/v1/status/updates" }
            .map { it.body?.utf8() }
            .toList()

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
        const val STAGE_6_PROBE = "stage6-probe"
        const val STAGE_6_TRIGGER_CHUNK = 10
        const val STAGE_7_PROBE = "stage7-probe"
        const val ARRANGEMENT_TEST_PLAYLIST_UUID = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90"
        const val ITEM_1_SLIDE_INDEX_CHUNK = 10
        const val PRESENTATION_ROUTE_SLIDE_INDEX_CHUNK = 16
        const val SAME_ITEM_SLIDE_INDEX_CHUNK = 28
        const val SUBSCRIPTIONS_BODY = """["status/slide","timer/system_time","playlist/active","status/layers",""" +
            """"timers","timers/current","macro_collections","looks","look/current","prop_collections"]"""
        const val STAGE_8_TIMERS = "stage8-timers"
        const val TIMER_0 = "2d8ffe81-50af-46a5-8c6b-8ed6ac5f34cf"
        const val TIMERS_READ_CHUNK = 7
        const val MACROS_READ_CHUNK = 7
        const val TIMER_RUNNING_CHUNK = 13
        const val TIMER_STOPPED_CHUNK = 20
        const val STAGE_9_OVERRUN = "stage9-overrun"
        const val TIMER_2 = "c5fcf5b0-ee31-4143-9d54-6514d922b2bb"
        const val STAGE_9_TIMERS_CHUNK = 6
        const val OVERRUNNING_CHUNK = 10
        const val OVERRAN_CHUNK = 16
        const val SUBSCRIPTIONS_WITHOUT_MACROS_BODY =
            """["status/slide","timer/system_time","playlist/active","status/layers","timers","timers/current",""" +
                """"looks","look/current","prop_collections"]"""
        const val STAGE_9_LOOKS = "stage9-looks-props"
        const val LOOKS_READ_CHUNK = 11
        const val PROPS_READ_CHUNK = 10
        const val ONE_FRAME_TRIGGER_CHUNK = 14
        const val OWN_UUID_FRAME_CHUNK = 16
        const val TWO_FRAME_TRIGGER_CHUNK = 18
        const val MACROS_REJECTED_FRAME = """["URL: macro_collections. Error: 404 Not Found"]"""
        const val CURRENT_LOOK_REJECTED_FRAME = """["URL: look/current. Error: 404 Not Found"]"""
        const val ONE_LOOK_FRAME =
            """{"url":"looks","data":[{"id":{"uuid":"l-0","name":"Look 01","index":0},"screens":[]}]}"""
        const val READINGS_REJECTED_FRAME = """["URL: timers/current. Error: 404 Not Found"]"""
        const val TWO_REJECTED_FRAME =
            """["URL: timers/current. Error: 404 Not Found","URL: macro_collections. Error: 404 Not Found"]"""
        const val EMPTY_TIMERS_FRAME = """{"url":"timers","data":[]}"""
        const val EMPTY_MACROS_FRAME = """{"url":"macro_collections","data":{"collections":[]}}"""
        const val ONE_TIMER_FRAME = """{"url":"timers","data":[{"id":{"name":"Timer 01","index":0,"uuid":"t-0"},""" +
            """"allows_overrun":false,"elapsed":{"start_time":0}}]}"""
        const val ONE_MACRO_FRAME = """{"url":"macro_collections","data":{"collections":[""" +
            """{"id":{"uuid":"c-0","name":"Collection 01","index":0},"macros":[]}]}}"""
    }
}
