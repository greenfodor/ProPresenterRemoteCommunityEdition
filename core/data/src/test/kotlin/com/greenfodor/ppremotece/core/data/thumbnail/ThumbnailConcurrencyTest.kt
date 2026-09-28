package com.greenfodor.ppremotece.core.data.thumbnail

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.size.Size
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamReplay
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ThumbnailConcurrencyTest {
    @StartStop
    private val server = MockWebServer()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stall = CountDownLatch(1)

    @AfterEach
    fun tearDown() {
        stall.countDown()
        scope.cancel()
    }

    @Test
    fun `the interceptor lets at most four requests proceed at once`() = runBlocking {
        val interceptor = ThumbnailConcurrencyInterceptor()
        val inside = AtomicInteger()
        val maxInside = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val chain = FakeChain {
            maxInside.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
            release.await()
            inside.decrementAndGet()
            throw Proceeded()
        }

        val calls = List(10) { scope.async { runCatching { interceptor.intercept(chain) } } }
        delay(200.milliseconds)
        assertThat(inside.get()).isEqualTo(4)
        release.complete(Unit)
        calls.awaitAll()

        assertThat(maxInside.get()).isEqualTo(4)
    }

    @Test
    fun `a permit is released when the request is cancelled`() = runBlocking {
        val interceptor = ThumbnailConcurrencyInterceptor()
        val started = AtomicInteger()
        val blocked = FakeChain {
            started.incrementAndGet()
            awaitCancellation()
        }
        val first = List(4) { scope.launch { interceptor.intercept(blocked) } }
        withTimeout(2.seconds) { while (started.get() < 4) delay(10.milliseconds) }

        first.forEach { it.cancel() }
        val next = scope.async { runCatching { interceptor.intercept(FakeChain { throw Proceeded() }) } }

        assertThat(withTimeout(2.seconds) { next.await() }.exceptionOrNull() is Proceeded).isEqualTo(true)
    }

    @Test
    fun `thumbnails stay at four open requests and a trigger does not wait behind them`() = runBlocking {
        val host = SlowThumbnailHost(stall)
        server.dispatcher = host
        val httpClient = HttpClientFactory.create()
        val client = KtorProPresenterClient(httpClient, server.url("/").toString())
        val base = server.url("/").toString()
        val item = PlaylistItemKey("6f760dbf-04b9-46f2-9bb3-33eeea6a6d90", 0)
        scope.launch { runCatching { client.statusUpdates(listOf("status/slide")).collect {} } }
        withTimeout(2.seconds) { while (host.streams.get() < 1) delay(10.milliseconds) }

        val interceptor = ThumbnailConcurrencyInterceptor()
        val thumbnails = List(12) { cue ->
            scope.async {
                runCatching { interceptor.intercept(FakeChain { fetch(httpClient, thumbnailUrl(base, item, cue)) }) }
            }
        }
        withTimeout(2.seconds) { while (host.openThumbnails.get() < 4) delay(10.milliseconds) }
        val started = System.nanoTime()
        val trigger = client.triggerCue(item, cueIndex = 3)
        val triggerMillis = (System.nanoTime() - started) / 1_000_000
        thumbnails.awaitAll()

        assertThat(trigger).isEqualTo(Result.Success(Unit))
        assertThat(triggerMillis).isLessThan(SlowThumbnailHost.THUMBNAIL_DELAY_MILLIS / 2)
        assertThat(host.maxOpenThumbnails.get()).isEqualTo(4)
        assertThat(host.thumbnailQueries.toSet()).isEqualTo(setOf("quality=400"))
    }

    private suspend fun fetch(httpClient: HttpClient, url: String): Nothing {
        httpClient.get(url).readRawBytes()
        throw Proceeded()
    }

    private class Proceeded : RuntimeException()

    private class FakeChain(
        private val onProceed: suspend () -> Nothing
    ) : Interceptor.Chain {
        override val request: ImageRequest get() = error("not used")
        override val size: Size get() = error("not used")

        override fun withRequest(request: ImageRequest): Interceptor.Chain = this

        override fun withSize(size: Size): Interceptor.Chain = this

        override suspend fun proceed(): ImageResult = onProceed()
    }

    private class SlowThumbnailHost(
        private val stall: CountDownLatch
    ) : Dispatcher() {
        val openThumbnails = AtomicInteger()
        val maxOpenThumbnails = AtomicInteger()
        val streams = AtomicInteger()
        val thumbnailQueries = java.util.concurrent.CopyOnWriteArrayList<String?>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            return when {
                request.method == "POST" -> {
                    streams.incrementAndGet()
                    StreamReplay.silent(stall)
                }
                path.contains("/thumbnail/") -> {
                    thumbnailQueries += request.url.encodedQuery
                    maxOpenThumbnails.accumulateAndGet(openThumbnails.incrementAndGet(), ::maxOf)
                    Thread.sleep(THUMBNAIL_DELAY_MILLIS)
                    openThumbnails.decrementAndGet()
                    MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body("marker").build()
                }
                path.endsWith("/trigger") -> MockResponse.Builder().code(204).build()
                else -> MockResponse.Builder().code(404).build()
            }
        }

        companion object {
            const val THUMBNAIL_DELAY_MILLIS = 400L
        }
    }
}
