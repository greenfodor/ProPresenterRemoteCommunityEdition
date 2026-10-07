package com.greenfodor.ppremotece.core.data.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.live.PlaylistNotFoundException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class PlaylistChangesClientTest {
    @StartStop
    private val server = MockWebServer()

    private val fake = FakeProPresenter()
    private lateinit var client: KtorProPresenterClient

    @BeforeEach
    fun setUp() {
        server.dispatcher = fake
        client = KtorProPresenterClient(HttpClientFactory.create(), server.url("/").toString())
    }

    @Test
    fun `a change chunk is one signal and the flow ends with the body`() = runBlocking<Unit> {
        fake.playlistUpdates += body(CHANGE)

        val signals = withTimeout(5.seconds) { client.playlistChanges(PLAYLIST).toList() }

        assertThat(signals).containsExactly(Unit)
        val request = fake.requests.single()
        assertThat("${request.method} ${request.url.encodedPath}?${request.url.encodedQuery}")
            .isEqualTo("GET /v1/playlist/$PLAYLIST/updates?chunked=true")
    }

    @Test
    fun `two change chunks in one read are two signals`() = runBlocking<Unit> {
        fake.playlistUpdates += body(CHANGE + CHANGE)

        assertThat(withTimeout(5.seconds) { client.playlistChanges(PLAYLIST).toList() }).containsExactly(Unit, Unit)
    }

    @Test
    fun `a body that ends without a chunk ends the flow without a signal`() = runBlocking<Unit> {
        fake.playlistUpdates += body("")

        assertThat(withTimeout(5.seconds) { client.playlistChanges(PLAYLIST).toList() }).isEmpty()
    }

    @Test
    fun `a playlist ProPresenter does not know fails as not found`() = runBlocking<Unit> {
        val failure = runCatching { withTimeout(5.seconds) { client.playlistChanges(PLAYLIST).toList() } }

        assertThat(failure.exceptionOrNull() is PlaylistNotFoundException).isTrue()
    }

    @Test
    fun `the connection is not cut by the client's request and socket timeouts`() = runBlocking<Unit> {
        val impatient = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = SHORT_TIMEOUT_MILLIS
                socketTimeoutMillis = SHORT_TIMEOUT_MILLIS
            }
        }
        val slowClient = KtorProPresenterClient(impatient, server.url("/").toString())
        fake.playlistUpdates += MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .bodyDelay(4 * SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .body(CHANGE)
            .build()

        assertThat(withTimeout(5.seconds) { slowClient.playlistChanges(PLAYLIST).toList() }).containsExactly(Unit)
        impatient.close()
    }

    private fun body(text: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(text).build()

    private companion object {
        const val PLAYLIST = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90"
        const val CHANGE = "\"change\"\r\n\r\n"
        const val SHORT_TIMEOUT_MILLIS = 200L
    }
}
