package com.greenfodor.ppremotece.core.data.content

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.PlaylistNotFoundException
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** When the playlist's change connection is reopened after it ended, in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistChangesBackoffTest {
    private val http = HttpClientFactory.create()
    private val unused = KtorProPresenterClient(http, "http://localhost:1/")
    private val openedAt = mutableListOf<Long>()
    private var connections: List<suspend (emit: suspend () -> Unit) -> Unit> = emptyList()
    private var streamConnected = true

    @AfterEach
    fun tearDown() {
        http.close()
    }

    private fun TestScope.repository(): CachingContentRepository {
        val client = object : ProPresenterClient by unused {
            override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> =
                Result.Success(Playlist(uuid, "Arrangement Test", emptyList()))

            override fun playlistChanges(uuid: String): Flow<Unit> =
                flow {
                    val connection = connections.getOrNull(openedAt.size)
                    openedAt += currentTime
                    if (connection == null) delay(Duration.INFINITE) else connection { emit(Unit) }
                }
        }
        return CachingContentRepository(
            client = client,
            session = MutableStateFlow("1@host-a"),
            staleSignals = MutableSharedFlow(),
            scope = backgroundScope,
            restore = {},
            isStreamConnected = { streamConnected },
            timeSource = testScheduler.timeSource
        )
    }

    private val endsAtOnce: suspend (suspend () -> Unit) -> Unit = {}

    @Test
    fun `a connection that keeps ending is reopened after 2, 4, 8, 16, 30 and 30 seconds`() = runTest {
        connections = List(6) { endsAtOnce }
        backgroundScope.launch { repository().playlist(PLAYLIST).collect {} }

        advanceTimeBy(91.seconds)

        assertThat(openedAt).isEqualTo(listOf(0L, 2_000L, 6_000L, 14_000L, 30_000L, 60_000L, 90_000L))
    }

    @Test
    fun `a connection that delivered a change is reopened after 2 seconds, then 4`() = runTest {
        connections = listOf(endsAtOnce, endsAtOnce, { emit -> emit() }, endsAtOnce)
        backgroundScope.launch { repository().playlist(PLAYLIST).collect {} }

        advanceTimeBy(13.seconds)

        assertThat(openedAt).isEqualTo(listOf(0L, 2_000L, 6_000L, 8_000L, 12_000L))
    }

    @Test
    fun `a connection that stayed open for 30 seconds is reopened after 2 seconds, then 4`() = runTest {
        connections = listOf(endsAtOnce, endsAtOnce, { delay(30.seconds) }, endsAtOnce)
        backgroundScope.launch { repository().playlist(PLAYLIST).collect {} }

        advanceTimeBy(43.seconds)

        assertThat(openedAt).isEqualTo(listOf(0L, 2_000L, 6_000L, 38_000L, 42_000L))
    }

    @Test
    fun `a connection that stayed open for less than 30 seconds keeps backing off`() = runTest {
        connections = listOf(endsAtOnce, endsAtOnce, { delay(29.seconds) }, endsAtOnce)
        backgroundScope.launch { repository().playlist(PLAYLIST).collect {} }

        advanceTimeBy(44.seconds)

        assertThat(openedAt).isEqualTo(listOf(0L, 2_000L, 6_000L, 43_000L))
    }

    @Test
    fun `a connection that ended is reopened only once the status stream is connected`() = runTest {
        connections = listOf(endsAtOnce)
        streamConnected = false
        backgroundScope.launch { repository().playlist(PLAYLIST).collect {} }

        advanceTimeBy(20.seconds)
        assertThat(openedAt).isEqualTo(listOf(0L))
        streamConnected = true
        advanceTimeBy(2.seconds + 1.milliseconds)

        assertThat(openedAt.size).isEqualTo(2)
    }

    @Test
    fun `a playlist ProPresenter does not know is not asked again`() = runTest {
        connections = listOf({ throw PlaylistNotFoundException() })
        backgroundScope.launch { repository().playlist(PLAYLIST).collect {} }

        advanceTimeBy(120.seconds)

        assertThat(openedAt).isEqualTo(listOf(0L))
    }

    private companion object {
        const val PLAYLIST = "pl-0"
    }
}
