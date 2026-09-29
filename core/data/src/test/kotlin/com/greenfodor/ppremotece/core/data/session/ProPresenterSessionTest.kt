package com.greenfodor.ppremotece.core.data.session

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.SlideSize
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProPresenterSessionTest {
    @StartStop
    private val server = MockWebServer()

    private val cache = FakeThumbnailCache()
    private val savedHosts = FakeSavedHosts()
    private lateinit var session: ProPresenterSession

    @BeforeEach
    fun setUp() {
        server.dispatcher = FakeProPresenter()
        session = ProPresenterSession(HttpClientFactory.create(), savedHosts, cache)
    }

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
        val request = requests.request(PlaylistItemKey("pl-1", 1), "p-1", cue)
        assertThat(
            request.url
        ).isEqualTo("http://${server.hostName}:${server.port}/v1/playlist/pl-1/1/thumbnail/3?quality=400")
        assertThat(request.cacheKey).isEqualTo(ThumbnailKey.of("Host 01", "p-1", cue))
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

        override suspend fun read(): ProPresenterHost? = host

        override suspend fun save(host: ProPresenterHost) {
            this.host = host
        }

        override suspend fun clear() {
            host = null
        }
    }
}
