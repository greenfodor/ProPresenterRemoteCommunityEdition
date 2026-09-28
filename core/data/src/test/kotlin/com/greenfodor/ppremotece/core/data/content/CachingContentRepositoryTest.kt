package com.greenfodor.ppremotece.core.data.content

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CachingContentRepositoryTest {
    @StartStop
    private val server = MockWebServer()

    private val host = ContentHost()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = MutableStateFlow<String?>("1@host-a")
    private val reconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var repository: CachingContentRepository

    @BeforeEach
    fun setUp() {
        server.dispatcher = host
        val client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        repository = CachingContentRepository(client, session, reconnects, scope)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `concurrent collectors share one read`() = runBlocking {
        host.delayMillis = 300

        val results = List(2) { async(Dispatchers.IO) { repository.playlist(PLAYLIST).first() } }.awaitAll()

        assertThat(results.map { it.name() }).containsExactly(ORIGINAL_NAME, ORIGINAL_NAME)
        assertThat(host.reads(PLAYLIST_PATH)).isEqualTo(1)
    }

    @Test
    fun `opening again emits the cached value, then the value read`() = runBlocking {
        assertThat(repository.playlist(PLAYLIST).first().name()).isEqualTo(ORIGINAL_NAME)
        host.playlistName = "Renamed"
        host.delayMillis = 300

        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            assertThat(awaitItem().name()).isEqualTo(ORIGINAL_NAME)
            assertThat(awaitItem().name()).isEqualTo("Renamed")
        }
        assertThat(host.reads(PLAYLIST_PATH)).isEqualTo(2)
    }

    @Test
    fun `a read that returns the same content emits nothing`() = runBlocking {
        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            assertThat(awaitItem().name()).isEqualTo(ORIGINAL_NAME)

            assertThat(repository.refreshPlaylist(PLAYLIST)).isEqualTo(Result.Success(Unit))
            host.playlistName = "Renamed"
            assertThat(repository.refreshPlaylist(PLAYLIST)).isEqualTo(Result.Success(Unit))

            assertThat(awaitItem().name()).isEqualTo("Renamed")
        }
        assertThat(host.reads(PLAYLIST_PATH)).isEqualTo(3)
    }

    @Test
    fun `a failed read keeps the cached value`() = runBlocking {
        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            assertThat(awaitItem().name()).isEqualTo(ORIGINAL_NAME)
            host.failWith = 500

            assertThat(repository.refreshPlaylist(PLAYLIST)).isEqualTo(Result.Failure(DataError.Network.SERVER))
            delay(100.milliseconds)
            expectNoEvents()
        }
    }

    @Test
    fun `a stream reconnect re-reads what is being collected`() = runBlocking {
        repository.presentation(SONG_A).first()

        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            assertThat(awaitItem().name()).isEqualTo(ORIGINAL_NAME)
            host.playlistName = "Renamed"

            reconnects.emit(Unit)

            assertThat(awaitItem().name()).isEqualTo("Renamed")
        }
        assertThat(host.reads(PLAYLIST_PATH)).isEqualTo(2)
        assertThat(host.reads(SONG_A_PATH)).isEqualTo(1)
    }

    @Test
    fun `disconnect clears the cache`() = runBlocking {
        assertThat(repository.playlist(PLAYLIST).first().name()).isEqualTo(ORIGINAL_NAME)

        session.value = null
        awaitCondition { repository.cachedEntries() == 0 }
        session.value = "2@host-a"
        host.failWith = 500

        assertThat(repository.playlist(PLAYLIST).first()).isEqualTo(Result.Failure(DataError.Network.SERVER))
    }

    @Test
    fun `a new host does not see the previous host's values`() = runBlocking {
        assertThat(repository.playlist(PLAYLIST).first().name()).isEqualTo(ORIGINAL_NAME)

        session.value = "2@host-b"
        host.failWith = 500

        assertThat(repository.playlist(PLAYLIST).first()).isEqualTo(Result.Failure(DataError.Network.SERVER))
    }

    private fun Result<Playlist, DataError.Network>.name(): String = (this as Result.Success).data.name

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!condition()) delay(10.milliseconds)
        }
    }

    private class ContentHost : Dispatcher() {
        @Volatile
        var delayMillis = 0L

        @Volatile
        var failWith: Int? = null

        @Volatile
        var playlistName = ORIGINAL_NAME

        private val counts = ConcurrentHashMap<String, AtomicInteger>()

        fun reads(path: String): Int = counts[path]?.get() ?: 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
            val body = when (path) {
                PLAYLIST_PATH -> Fixtures.text(Fixtures.ARRANGEMENT_TEST_PLAYLIST)
                    .replace("\"$ORIGINAL_NAME\"", "\"$playlistName\"")
                SONG_A_PATH -> Fixtures.text(Fixtures.SONG_A)
                else -> null
            }
            val code = failWith ?: if (body == null) 404 else 200
            return if (code == 200 && body != null) {
                MockResponse.Builder()
                    .addHeader("Content-Type", "application/json")
                    .headersDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .body(body)
                    .build()
            } else {
                MockResponse.Builder().code(code).build()
            }
        }
    }

    private companion object {
        const val PLAYLIST = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90"
        const val SONG_A = "08672906-49df-4947-8d4c-bed596d6fbf3"
        const val PLAYLIST_PATH = "/v1/playlist/$PLAYLIST"
        const val SONG_A_PATH = "/v1/presentation/$SONG_A"
        const val ORIGINAL_NAME = "Arrangement Test"
    }
}
