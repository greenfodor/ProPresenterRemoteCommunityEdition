package com.greenfodor.ppremotece.core.data.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KtorProPresenterClientTest {
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
    fun `version is read from the unversioned version route`() = runBlocking {
        assertThat(client.version()).isEqualTo(
            Result.Success(
                ProPresenterVersion(name = "Host 01", hostDescription = "ProPresenter 21.4.2", apiVersion = "v1")
            )
        )
    }

    @Test
    fun `playlist and presentation are mapped from their responses`() = runBlocking {
        val playlist = client.playlist("6f760dbf-04b9-46f2-9bb3-33eeea6a6d90") as Result.Success
        val presentation = client.presentation(FakeProPresenter.SONG_A_UUID) as Result.Success

        assertThat(playlist.data.items.size).isEqualTo(6)
        assertThat(presentation.data.arrangements.map { it.name }).containsExactly("Full", "Chorus Only", "Short", "")
    }

    @Test
    fun `slide index is mapped to the live slide`() = runBlocking {
        assertThat(client.slideIndex())
            .isEqualTo(
                Result.Success(LiveSlide(presentationUuid = FakeProPresenter.SONG_A_UUID, index = 3, totalCues = 7))
            )
    }

    @Test
    fun `triggering a cue past the end of the item is not found`() = runBlocking {
        val item = PlaylistItemKey(playlistUuid = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90", index = 1)

        assertThat(client.triggerCue(item, cueIndex = 6)).isEqualTo(Result.Success(Unit))
        assertThat(client.triggerCue(item, cueIndex = 7)).isEqualTo(Result.Failure(DataError.Network.NOT_FOUND))
    }

    @Test
    fun `unreachable host is no connection`() = runBlocking {
        val unreachable = KtorProPresenterClient(HttpClientFactory.create(), "http://127.0.0.1:1/")

        assertThat(unreachable.version()).isEqualTo(Result.Failure(DataError.Network.NO_CONNECTION))
    }

    @Test
    fun `malformed host is unknown`() = runBlocking {
        val malformed = KtorProPresenterClient(HttpClientFactory.create(), "http://host name:1/")

        assertThat(malformed.version()).isEqualTo(Result.Failure(DataError.Network.UNKNOWN))
        assertThat(malformed.triggerNext()).isEqualTo(Result.Failure(DataError.Network.UNKNOWN))
    }

    @Test
    fun `status updates posts the subscriptions and streams the response chunks`() = runBlocking {
        fake.enqueueStream(fake.stream("status-updates", StreamEnd.EOF, timeScale = 0.0))

        val chunks = client.statusUpdates(listOf("status/slide", "timer/system_time")).toList()

        assertThat(chunks.reduce { all, chunk -> all + chunk }.toList())
            .isEqualTo(Fixtures.bytes("streams/status-updates.raw").toList())
        val post = fake.requests.single { it.method == "POST" }
        assertThat(post.body?.utf8()).isEqualTo("""["status/slide","timer/system_time"]""")
    }

    @Test
    fun `every call uses only allowed methods and paths`() = runBlocking {
        fake.enqueueStream(fake.stream("status-updates", StreamEnd.EOF, timeScale = 0.0))
        val item = PlaylistItemKey(playlistUuid = FakeProPresenter.SERVICE_PLAYLIST_UUID, index = 4)

        client.version()
        client.playlists()
        client.playlist(FakeProPresenter.SERVICE_PLAYLIST_UUID)
        client.presentation(FakeProPresenter.SONG_A_UUID)
        client.slideIndex()
        client.triggerCue(item, cueIndex = 2)
        client.triggerNext()
        client.triggerPrevious()
        client.statusUpdates(listOf("status/slide")).first()

        assertThat(fake.requests.size).isEqualTo(9)
        FakeProPresenter.assertOnlyAllowedRequests(fake.requests)
    }
}
