package com.greenfodor.ppremotece.core.data.thumbnail

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.network.StreamReplay
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.result.Result
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
import kotlinx.coroutines.sync.Semaphore
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

    private val permits = Semaphore(LimitedFetcherFactory.MAX_IN_FLIGHT)

    @Test
    fun `at most four fetches run at once`() = runBlocking {
        val inside = AtomicInteger()
        val maxInside = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val fetcher = limited {
            maxInside.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
            release.await()
            inside.decrementAndGet()
        }

        val calls = List(10) { scope.async { fetcher.fetch() } }
        delay(200.milliseconds)
        assertThat(inside.get()).isEqualTo(4)
        release.complete(Unit)
        calls.awaitAll()

        assertThat(maxInside.get()).isEqualTo(4)
    }

    @Test
    fun `a permit is released when a fetch is cancelled`() = runBlocking {
        val started = AtomicInteger()
        val blocked = limited {
            started.incrementAndGet()
            awaitCancellation()
        }
        val first = List(4) { scope.launch { blocked.fetch() } }
        withTimeout(2.seconds) { while (started.get() < 4) delay(10.milliseconds) }

        first.forEach { it.cancel() }
        val ran = CompletableDeferred<Unit>()
        scope.launch { limited { ran.complete(Unit) }.fetch() }

        withTimeout(2.seconds) { ran.await() }
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

        val thumbnails = List(12) { cue ->
            scope.async {
                limited { httpClient.get(thumbnailUrl(base, item, cue, boxQuality = null)).readRawBytes() }.fetch()
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

    private fun limited(work: suspend () -> Unit): Fetcher =
        LimitedFetcher(
            object : Fetcher {
                override suspend fun fetch(): FetchResult? {
                    work()
                    return null
                }
            },
            permits
        )

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
