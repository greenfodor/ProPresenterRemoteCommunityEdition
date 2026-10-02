package com.greenfodor.ppremotece.core.data.thumbnail

import android.content.ContextWrapper
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.ConcurrentRequestStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.size.Size
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import mockwebserver3.junit5.StartStop
import okio.Buffer
import okio.BufferedSink
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoilApi::class)
class ValidatingCacheFetcherTest {
    @StartStop
    private val server = MockWebServer()

    @TempDir
    lateinit var dir: Path

    private val cache by lazy { DiskCache.Builder().directory(dir.toOkioPath()).build() }
    private val memory = MemoryCache.Builder().maxSizeBytes(MEMORY_BYTES).build()
    private val image = ByteArray(FULL) { (it % 251).toByte() }

    @Test
    fun `a body cut short of its content-length is not stored and the fetch fails`() = runBlocking<Unit> {
        server.enqueue(cutResponse())

        assertFailure { validating(networkFetcher()).fetch() }

        assertThat(cache.storedSize(KEY)).isNull()
    }

    @Test
    fun `a stored entry shorter than its content-length is removed and fetched again`() = runBlocking {
        cache.store(KEY, image.copyOf(CUT), contentLength = FULL.toLong())
        server.enqueue(fullResponse())

        val result = validating(networkFetcher()).fetch() as SourceFetchResult
        result.source.close()

        assertThat(result.dataSource).isEqualTo(DataSource.NETWORK)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(cache.storedSize(KEY)).isEqualTo(FULL.toLong())
    }

    @Test
    fun `a removed short entry also leaves the memory cache`() = runBlocking {
        cache.store(KEY, image.copyOf(CUT), contentLength = FULL.toLong())
        memory[MemoryCache.Key(KEY)] = MemoryCache.Value(ColorImage(0, 1, 1))
        server.enqueue(fullResponse())

        (validating(networkFetcher()).fetch() as SourceFetchResult).source.close()

        assertThat(memory[MemoryCache.Key(KEY)]).isNull()
    }

    @Test
    fun `a complete stored entry is served from disk without a request`() = runBlocking {
        cache.store(KEY, image)

        val result = validating(networkFetcher()).fetch() as SourceFetchResult
        result.source.close()

        assertThat(result.dataSource).isEqualTo(DataSource.DISK)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an entry without a stored content-length is served as stored`() = runBlocking {
        cache.store(KEY, image.copyOf(CUT), contentLength = null)

        val result = validating(networkFetcher()).fetch() as SourceFetchResult
        result.source.close()

        assertThat(result.dataSource).isEqualTo(DataSource.DISK)
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(cache.storedSize(KEY)).isEqualTo(CUT.toLong())
    }

    @Test
    fun `a fetch that stores a short body runs once more`() = runBlocking {
        val calls = AtomicInteger()
        val storing = storingFetcher(calls) { call -> if (call == 1) CUT else FULL }

        val result = validating(storing).fetch() as SourceFetchResult
        result.source.close()

        assertThat(calls.get()).isEqualTo(2)
        assertThat(cache.storedSize(KEY)).isEqualTo(FULL.toLong())
    }

    @Test
    fun `a second short body ends as an error and nothing stays stored`() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val storing = storingFetcher(calls) { CUT }

        assertFailure { validating(storing).fetch() }

        assertThat(calls.get()).isEqualTo(2)
        assertThat(cache.storedSize(KEY)).isNull()
    }

    @Test
    fun `a fetch that stores nothing is returned as it is`() = runBlocking {
        val result = validating(
            Fetcher {
                SourceFetchResult(ImageSource(Buffer(), cache.fileSystem), null, DataSource.NETWORK)
            }
        )
            .fetch()

        assertThat(result).isNotNull()
    }

    private fun validating(delegate: Fetcher) = ValidatingCacheFetcher(delegate, cache, memory, KEY)

    /** A fetcher that stores the first n bytes of the image under [KEY] on each call, n from [length]. */
    private fun storingFetcher(calls: AtomicInteger, length: (Int) -> Int) = Fetcher {
        cache.store(KEY, image.copyOf(length(calls.incrementAndGet())), contentLength = FULL.toLong())
        val snapshot = checkNotNull(cache.openSnapshot(KEY))
        SourceFetchResult(ImageSource(snapshot.data, cache.fileSystem, KEY, snapshot), "image/jpeg", DataSource.NETWORK)
    }

    private fun fullResponse() =
        MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body(Buffer().write(image)).build()

    private fun cutResponse() =
        MockResponse.Builder()
            .addHeader("Content-Type", "image/jpeg")
            .body(
                object : MockResponseBody {
                    override val contentLength = FULL.toLong()

                    override fun writeTo(sink: BufferedSink) {
                        sink.write(image, 0, CUT)
                        sink.flush()
                    }
                }
            ).onResponseEnd(SocketEffect.CloseSocket())
            .build()

    /**
     * Coil's network fetcher for a thumbnail on [server], storing under [KEY]; network cache reads
     * are off and the `Cache-Control` header they add is removed before the request is sent.
     */
    private fun networkFetcher() =
        NetworkFetcher(
            url = server.url("/v1/playlist/pl/0/thumbnail/0?quality=400").toString(),
            options = Options(
                context = ContextWrapper(null),
                size = Size.ORIGINAL,
                diskCacheKey = KEY,
                networkCachePolicy = CachePolicy.WRITE_ONLY
            ),
            networkClient = lazyOf(HttpClientFactory.create(withoutCacheControl()).asNetworkClient()),
            diskCache = lazyOf(cache),
            cacheStrategy = lazyOf(CacheStrategy.DEFAULT),
            connectivityChecker = lazyOf(ConnectivityChecker.ONLINE),
            concurrentRequestStrategy = lazyOf(ConcurrentRequestStrategy.UNCOORDINATED)
        )

    private fun withoutCacheControl() =
        OkHttp.create {
            addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
            }
        }

    private companion object {
        const val KEY = "thumb:v1:host:p:g:0:digest"
        const val FULL = 248_588
        const val CUT = 14_297
        const val MEMORY_BYTES = 1024L * 1024
    }
}
