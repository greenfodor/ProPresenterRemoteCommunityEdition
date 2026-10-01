package com.greenfodor.ppremotece.core.data.content

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CoroutineExceptionHandler
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
import java.util.concurrent.CopyOnWriteArrayList
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
    private val restores = AtomicInteger()
    private lateinit var repository: CachingContentRepository

    @BeforeEach
    fun setUp() {
        server.dispatcher = host
        val client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        repository =
            CachingContentRepository(client, session, reconnects, scope, restore = { restores.incrementAndGet() })
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
    fun `concurrent library list collectors share one read`() = runBlocking {
        host.delayMillis = 300

        val results = List(2) { async(Dispatchers.IO) { repository.libraries().first() } }.awaitAll()

        assertThat(results.map { (it as Result.Success).data.first().name }).containsExactly("Library 01", "Library 01")
        assertThat(host.reads(LIBRARIES_PATH)).isEqualTo(1)
    }

    @Test
    fun `the library list and a library emit again only when their content changes`() = runBlocking {
        repository.libraries().test(timeout = 5.seconds) {
            assertThat((awaitItem() as Result.Success).data.first().name).isEqualTo("Library 01")
            assertThat(repository.refreshLibraries()).isEqualTo(Result.Success(Unit))
            host.libraryName = "Renamed"
            assertThat(repository.refreshLibraries()).isEqualTo(Result.Success(Unit))
            assertThat((awaitItem() as Result.Success).data.first().name).isEqualTo("Renamed")
        }
        repository.library(Fixtures.LIBRARY_ID).test(timeout = 5.seconds) {
            assertThat((awaitItem() as Result.Success).data.size).isEqualTo(413)
            assertThat(repository.refreshLibrary(Fixtures.LIBRARY_ID)).isEqualTo(Result.Success(Unit))
            expectNoEvents()
        }
        assertThat(host.reads(LIBRARIES_PATH)).isEqualTo(3)
        assertThat(host.reads(LIBRARY_PATH)).isEqualTo(2)
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

    @Test
    fun `without a session nothing is read and a restore is requested`() = runBlocking {
        session.value = null

        repository.playlist(PLAYLIST).test(timeout = 5.seconds) {
            awaitCondition { restores.get() == 1 }
            delay(100.milliseconds)
            expectNoEvents()
        }
        assertThat(host.reads(PLAYLIST_PATH)).isEqualTo(0)
    }

    @Test
    fun `opening again does not replay a cached failure`() = runBlocking {
        host.failWith = 500
        assertThat(repository.playlist(PLAYLIST).first()).isEqualTo(Result.Failure(DataError.Network.SERVER))

        host.failWith = null

        assertThat(repository.playlist(PLAYLIST).first().name()).isEqualTo(ORIGINAL_NAME)
    }

    @Test
    fun `a failure repeated on retry is emitted again`() = runBlocking {
        host.failWith = 500
        assertThat(repository.playlist(PLAYLIST).first()).isEqualTo(Result.Failure(DataError.Network.SERVER))

        assertThat(repository.playlist(PLAYLIST).first()).isEqualTo(Result.Failure(DataError.Network.SERVER))
        assertThat(host.reads(PLAYLIST_PATH)).isEqualTo(2)
    }

    @Test
    fun `a read that throws is a failed read and does not reach the scope`() = runBlocking {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val guardedScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e }
        )
        val client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
        val throwingClient = object : ProPresenterClient by client {
            override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> = error("unexpected")
        }
        val throwing = CachingContentRepository(throwingClient, session, reconnects, guardedScope, restore = {})

        withTimeout(2.seconds) {
            assertThat(throwing.playlist(PLAYLIST).first()).isEqualTo(Result.Failure(DataError.Network.UNKNOWN))
            assertThat(throwing.refreshPlaylist(PLAYLIST)).isEqualTo(Result.Failure(DataError.Network.UNKNOWN))
        }
        delay(100.milliseconds)
        assertThat(uncaught.toList()).isEmpty()
        guardedScope.cancel()
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

        @Volatile
        var libraryName = "Library 01"

        private val counts = ConcurrentHashMap<String, AtomicInteger>()

        fun reads(path: String): Int = counts[path]?.get() ?: 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
            val body = when (path) {
                PLAYLIST_PATH -> Fixtures.text(Fixtures.ARRANGEMENT_TEST_PLAYLIST)
                    .replace("\"$ORIGINAL_NAME\"", "\"$playlistName\"")
                SONG_A_PATH -> Fixtures.text(Fixtures.SONG_A)
                LIBRARIES_PATH -> Fixtures.text(Fixtures.LIBRARIES).replace("\"Library 01\"", "\"$libraryName\"")
                LIBRARY_PATH -> Fixtures.text(Fixtures.LIBRARY)
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
        const val LIBRARIES_PATH = "/v1/libraries"
        const val LIBRARY_PATH = "/v1/library/${Fixtures.LIBRARY_ID}"
        const val ORIGINAL_NAME = "Arrangement Test"
    }
}
