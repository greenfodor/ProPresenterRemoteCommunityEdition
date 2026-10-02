package com.greenfodor.ppremotece.core.data.session

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.SlideSize
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailKey
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProPresenterSessionTest {
    @StartStop
    private val server = MockWebServer()

    private val fake = FakeProPresenter()
    private val cache = FakeThumbnailCache()
    private val savedHosts = FakeSavedHosts()
    private lateinit var session: ProPresenterSession

    @BeforeEach
    fun setUp() {
        server.dispatcher = fake
        session = ProPresenterSession(HttpClientFactory.create(), savedHosts, cache)
    }

    @AfterEach
    fun tearDown() {
        fake.releaseStalls()
    }

    @Test
    fun `the last live cue is cleared on disconnect`() = runBlocking {
        connectUntilLastLive()

        session.disconnect()

        assertThat(withTimeout(2.seconds) { session.lastLive.first { it == null } }).isNull()
    }

    @Test
    fun `the last live cue is cleared by a new connect`() = runBlocking {
        connectUntilLastLive()

        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()

        assertThat(withTimeout(2.seconds) { session.lastLive.first { it == null } }).isNull()
    }

    @Test
    fun `the timers are cleared on disconnect`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(TIMERS_FRAME)))
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()
        val collector = launch { session.liveState.collect {} }
        assertThat(withTimeout(5.seconds) { session.timers.first { it.isNotEmpty() } }.single().timer.name)
            .isEqualTo("Timer 01")
        collector.cancel()

        session.disconnect()

        assertThat(withTimeout(2.seconds) { session.timers.first { it.isEmpty() } }).isEmpty()
    }

    @Test
    fun `the macro collections are cleared on disconnect`() = runBlocking {
        fake.enqueueStream(fake.frames(listOf(MACROS_FRAME)))
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()
        val collector = launch { session.liveState.collect {} }
        assertThat(withTimeout(5.seconds) { session.collections.first { it.isNotEmpty() } }.single().name)
            .isEqualTo("Collection 01")
        collector.cancel()

        session.disconnect()

        assertThat(withTimeout(2.seconds) { session.collections.first { it.isEmpty() } }).isEmpty()
    }

    @Test
    fun `disconnect keeps the saved host`() = runBlocking {
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()

        session.disconnect()

        assertThat(session.savedHost()).isEqualTo(host())
    }

    @Test
    fun `disconnect asks to stay disconnected and the next connect clears it`() = runBlocking {
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()
        assertThat(session.stayDisconnected()).isFalse()

        session.disconnect()
        assertThat(session.stayDisconnected()).isTrue()

        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()
        assertThat(session.stayDisconnected()).isFalse()
    }

    @Test
    fun `a new session restores the saved host after a disconnect that asked to stay disconnected`() = runBlocking {
        savedHosts.host = host()
        savedHosts.stayDisconnected = true

        session.restore()

        assertThat(session.connectedHost.value?.host).isEqualTo(host())
    }

    @Test
    fun `a host entered by its address is named after the version name`() = runBlocking {
        val entered = ProPresenterHost(name = server.hostName, address = server.hostName, port = server.port)

        assertThat(session.connect(entered)).isInstanceOf<Result.Success<*>>()

        assertThat(session.savedHost()).isEqualTo(host())
        assertThat(session.connectedHost.value?.host).isEqualTo(host())
    }

    @Test
    fun `a host entered by its address keeps the address when the version name is blank`() = runBlocking {
        fake.versionName = " "
        val entered = ProPresenterHost(name = server.hostName, address = server.hostName, port = server.port)

        assertThat(session.connect(entered)).isInstanceOf<Result.Success<*>>()

        assertThat(session.savedHost()).isEqualTo(entered)
    }

    @Test
    fun `a host named after its version name is renamed on each connect`() = runBlocking {
        val entered = ProPresenterHost(name = server.hostName, address = server.hostName, port = server.port)
        assertThat(session.connect(entered)).isInstanceOf<Result.Success<*>>()
        fake.versionName = "Host 02"

        assertThat(session.connect(checkNotNull(session.savedHost()))).isInstanceOf<Result.Success<*>>()

        assertThat(session.savedHost()?.name).isEqualTo("Host 02")
    }

    @Test
    fun `a discovered host keeps its name`() = runBlocking {
        fake.versionName = "Host 02"

        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()

        assertThat(session.savedHost()).isEqualTo(host())
    }

    @Test
    fun `the connected host and its version are shown until disconnect`() = runBlocking {
        assertThat(session.connectedHost.value).isNull()
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()

        val connected = session.connectedHost.value
        assertThat(connected?.host).isEqualTo(host())
        assertThat(connected?.version?.hostDescription).isEqualTo("ProPresenter 21.4.2")

        session.disconnect()
        assertThat(session.connectedHost.value).isNull()
    }

    @Test
    fun `a request after disconnect does not reconnect to the saved host`() = runBlocking {
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()
        session.disconnect()

        session.restore()

        assertThat(session.sessionKey.value).isNull()
        assertThat(session.connectedHost.value).isNull()
    }

    @Test
    fun `a disconnect during a restore leaves the session disconnected`() = runBlocking {
        savedHosts.host = host()
        val versionRequested = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath == "/version") {
                    versionRequested.complete(Unit)
                    release.await(5, TimeUnit.SECONDS)
                }
                return fake.dispatch(request)
            }
        }

        val restoring = launch(Dispatchers.IO) { session.restore() }
        withTimeout(5.seconds) { versionRequested.await() }
        val disconnecting = launch(Dispatchers.IO) { session.disconnect() }
        delay(100.milliseconds)
        release.countDown()
        restoring.join()
        disconnecting.join()

        assertThat(session.sessionKey.value).isNull()
        assertThat(session.connectedHost.value).isNull()
    }

    private suspend fun connectUntilLastLive() = coroutineScope {
        val item = PlaylistItemKey(FakeProPresenter.SERVICE_PLAYLIST_UUID, 4)
        fake.enqueueStream(
            fake.frames(listOf(FakeProPresenter.playlistActiveFrame(item), FakeProPresenter.SLIDE_FRAME))
        )
        assertThat(session.connect(host())).isInstanceOf<Result.Success<*>>()
        val collector = launch { session.liveState.collect {} }
        val lastLive = withTimeout(5.seconds) { session.lastLive.filterNotNull().first() }
        collector.cancel()
        assertThat(
            lastLive
        ).isEqualTo(LiveCue(CueSource.PlaylistItem(item), FakeProPresenter.SONG_A_UUID, cueIndex = 3))
    }

    private companion object {
        const val MACROS_FRAME = """{"url":"macro_collections","data":{"collections":[""" +
            """{"id":{"uuid":"c-0","name":"Collection 01","index":0},"macros":[]}]}}"""
        const val TIMERS_FRAME = """{"url":"timers","data":[{"id":{"name":"Timer 01","index":0,"uuid":"t-0"},""" +
            """"allows_overrun":false,"elapsed":{"start_time":0}}]}"""
    }

    private fun host() = ProPresenterHost(name = "Host 01", address = server.hostName, port = server.port)

    @Test
    fun `every successful connect clears the thumbnail cache`() = runBlocking {
        val host = ProPresenterHost(name = "Host 01", address = server.hostName, port = server.port)

        assertThat(session.connect(host)).isInstanceOf<Result.Success<*>>()
        withTimeout(2.seconds) { session.thumbnailRequests.filterNotNull().first() }
        assertThat(session.connect(host)).isInstanceOf<Result.Success<*>>()
        withTimeout(2.seconds) { session.thumbnailRequests.filterNotNull().first() }

        assertThat(cache.clears.get()).isEqualTo(2)
    }

    @Test
    fun `connect does not wait for the clear and thumbnails are published after it`() = runBlocking {
        val host = ProPresenterHost(name = "Host 01", address = server.hostName, port = server.port)
        cache.gate = CompletableDeferred()

        assertThat(withTimeout(2.seconds) { session.connect(host) }).isInstanceOf<Result.Success<*>>()
        withTimeout(2.seconds) { while (cache.clears.get() < 1) delay(10.milliseconds) }
        assertThat(session.thumbnailRequests.first()).isNull()

        cache.gate.complete(Unit)

        val requests = withTimeout(2.seconds) { session.thumbnailRequests.filterNotNull().first() }
        val cue = Cue(3, "g-1", "Chorus", null, 0, "Chorus · 1", enabled = true, size = SlideSize(1920, 858))
        val item = CueSource.PlaylistItem(PlaylistItemKey("pl-1", 1))
        val request = requests.request(item, "p-1", cue, ThumbnailQuality.Grid)
        assertThat(
            request.url
        ).isEqualTo("http://${server.hostName}:${server.port}/v1/playlist/pl-1/1/thumbnail/3?quality=400")
        assertThat(request.cacheKey).isEqualTo(ThumbnailKey.of("Host 01", "p-1", cue))
        assertThat(request.placeholderKey).isNull()

        val box = requests.request(item, "p-1", cue, ThumbnailQuality.Box(1284))
        assertThat(
            box.url
        ).isEqualTo("http://${server.hostName}:${server.port}/v1/playlist/pl-1/1/thumbnail/3?quality=800")
        assertThat(box.cacheKey).isEqualTo(ThumbnailKey.of("Host 01", "p-1", cue, boxQuality = 800))
        assertThat(box.placeholderKey).isEqualTo(ThumbnailKey.of("Host 01", "p-1", cue))

        val base = "http://${server.hostName}:${server.port}/v1/presentation/p-1/thumbnail/3"
        val presentation = CueSource.Presentation("p-1")
        val presentationGrid = requests.request(presentation, "p-1", cue, ThumbnailQuality.Grid)
        val presentationBox = requests.request(presentation, "p-1", cue, ThumbnailQuality.Box(1284))
        assertThat(presentationGrid).isEqualTo(ThumbnailRequest("$base?quality=711", request.cacheKey))
        assertThat(presentationBox).isEqualTo(ThumbnailRequest("$base?quality=1422", box.cacheKey, request.cacheKey))
    }

    @Test
    fun `a failed connect keeps the thumbnail cache`() = runBlocking {
        val unreachable = ProPresenterHost(name = "Host 01", address = "127.0.0.1", port = 1)

        assertThat(session.connect(unreachable)).isInstanceOf<Result.Failure<*>>()

        assertThat(cache.clears.get()).isEqualTo(0)
    }

    private class FakeThumbnailCache : ThumbnailCache {
        val clears = AtomicInteger()
        val removed = mutableListOf<String>()

        @Volatile
        var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

        override suspend fun clear() {
            clears.incrementAndGet()
            gate.await()
        }

        override suspend fun remove(keys: Collection<String>) {
            removed += keys
        }
    }

    private class FakeSavedHosts : SavedHosts {
        var host: ProPresenterHost? = null
        var namedByVersion = false
        var stayDisconnected = false

        override suspend fun read(): SavedHost? = host?.let { SavedHost(it, namedByVersion, stayDisconnected) }

        override suspend fun save(host: ProPresenterHost, namedByVersion: Boolean) {
            this.host = host
            this.namedByVersion = namedByVersion
            stayDisconnected = false
        }

        override suspend fun setStayDisconnected() {
            stayDisconnected = true
        }

        override suspend fun clear() {
            host = null
        }
    }
}
