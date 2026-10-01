package com.greenfodor.ppremotece.core.data.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
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

        assertThat(playlist.data.items.size).isEqualTo(7)
        assertThat(presentation.data.arrangements.map { it.name }).containsExactly("Full", "Chorus Only", "Short", "")
    }

    @Test
    fun `libraries are mapped from their response`() = runBlocking {
        val libraries = (client.libraries() as Result.Success).data

        assertThat(libraries.size).isEqualTo(10)
        assertThat(libraries.first()).isEqualTo(Library(Fixtures.LIBRARY_ID, "Library 01", 0))
        assertThat(libraries.map { it.index }).isEqualTo((0..9).toList())
    }

    @Test
    fun `a library's presentations are mapped from its response`() = runBlocking {
        val entries = (client.library(Fixtures.LIBRARY_ID) as Result.Success).data

        assertThat(entries.size).isEqualTo(413)
        assertThat(entries[207]).isEqualTo(LibraryEntry(FakeProPresenter.SONG_A_UUID, "Song A", 207))
        assertThat(fake.requests.single().url.encodedPath).isEqualTo("/v1/library/${Fixtures.LIBRARY_ID}")
    }

    @Test
    fun `triggering a presentation cue gets the presentation cue trigger route`() = runBlocking {
        assertThat(client.triggerPresentationCue(FakeProPresenter.SONG_A_UUID, cueIndex = 3))
            .isEqualTo(Result.Success(Unit))
        assertThat(fake.requests.single().method).isEqualTo("GET")
        assertThat(fake.requests.single().url.encodedPath)
            .isEqualTo("/v1/presentation/${FakeProPresenter.SONG_A_UUID}/3/trigger")
    }

    @Test
    fun `a presentation's current arrangement is mapped`() = runBlocking {
        val presentation = (client.presentation(FakeProPresenter.SONG_A_UUID) as Result.Success).data

        assertThat(presentation.currentArrangementUuid).isEqualTo("9ccdc706-56db-49a6-9658-2eec33c7ebfc")
    }

    @Test
    fun `slide index is mapped to the live slide`() = runBlocking {
        assertThat(client.slideIndex())
            .isEqualTo(
                Result.Success(LiveSlide(presentationUuid = FakeProPresenter.SONG_A_UUID, index = 3, totalCues = 7))
            )
    }

    @Test
    fun `the active playlist item is mapped like the playlist active frame`() = runBlocking {
        val item = PlaylistItemKey(playlistUuid = FakeProPresenter.SERVICE_PLAYLIST_UUID, index = 4)
        val body = requireNotNull(playlistActiveData(FakeProPresenter.playlistActiveFrame(item)))
        fake.liveBodies = { LiveBodies(FakeProPresenter.SLIDE_INDEX, body) }

        assertThat(client.activePlaylistItem())
            .isEqualTo(Result.Success(StatusEvent.PlaylistActive(item, FakeProPresenter.SONG_A_UUID)))
    }

    @Test
    fun `no active playlist item is mapped to no item`() = runBlocking {
        assertThat(client.activePlaylistItem()).isEqualTo(Result.Success(StatusEvent.PlaylistActive(null, null)))
    }

    @Test
    fun `triggering a cue past the end of the item is not found`() = runBlocking {
        val item = PlaylistItemKey(playlistUuid = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90", index = 1)

        assertThat(client.triggerCue(item, cueIndex = 6)).isEqualTo(Result.Success(Unit))
        assertThat(client.triggerCue(item, cueIndex = 7)).isEqualTo(Result.Failure(DataError.Network.NOT_FOUND))
    }

    @Test
    fun `triggering an item gets the item trigger route`() = runBlocking {
        val item = PlaylistItemKey(playlistUuid = "6f760dbf-04b9-46f2-9bb3-33eeea6a6d90", index = 4)

        assertThat(client.triggerItem(item)).isEqualTo(Result.Success(Unit))
        assertThat(fake.requests.single().method).isEqualTo("GET")
        assertThat(fake.requests.single().url.encodedPath)
            .isEqualTo("/v1/playlist/6f760dbf-04b9-46f2-9bb3-33eeea6a6d90/4/trigger")
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
    fun `clearing a layer gets its clear route`() = runBlocking {
        OutputLayer.entries.forEach { assertThat(client.clearLayer(it)).isEqualTo(Result.Success(Unit)) }

        assertThat(fake.requests.map { it.method + " " + it.url.encodedPath }).containsExactly(
            "GET /v1/clear/layer/slide",
            "GET /v1/clear/layer/media",
            "GET /v1/clear/layer/video_input",
            "GET /v1/clear/layer/props",
            "GET /v1/clear/layer/messages",
            "GET /v1/clear/layer/announcements",
            "GET /v1/clear/layer/audio"
        )
    }

    @Test
    fun `clear groups are mapped from their response`() = runBlocking {
        assertThat(client.clearGroups()).isEqualTo(
            Result.Success(listOf(ClearGroup(uuid = CLEAR_GROUP_UUID, name = "Clear All")))
        )
        assertThat(fake.requests.single().url.encodedPath).isEqualTo("/v1/clear/groups")
    }

    @Test
    fun `triggering a clear group gets its trigger route`() = runBlocking {
        assertThat(client.triggerClearGroup(CLEAR_GROUP_UUID)).isEqualTo(Result.Success(Unit))
        assertThat(fake.requests.single().method).isEqualTo("GET")
        assertThat(fake.requests.single().url.encodedPath).isEqualTo("/v1/clear/group/$CLEAR_GROUP_UUID/trigger")
    }

    @Test
    fun `a clear group icon is read from its icon route`() = runBlocking {
        assertThat(client.clearGroupIcon(CLEAR_GROUP_UUID)).isEqualTo(
            Result.Success(ClearGroupIcon.Vector(18f, 18f, listOf(IconPath("M1,1 L17,17", evenOdd = false))))
        )
        assertThat(fake.requests.single().method).isEqualTo("GET")
        assertThat(fake.requests.single().url.encodedPath).isEqualTo("/v1/clear/group/$CLEAR_GROUP_UUID/icon")
    }

    @Test
    fun `a clear group icon is read once per connection`() = runBlocking {
        client.clearGroupIcon(CLEAR_GROUP_UUID)
        client.clearGroupIcon(CLEAR_GROUP_UUID)

        assertThat(fake.count("GET", "/v1/clear/group/$CLEAR_GROUP_UUID/icon")).isEqualTo(1)
    }

    @Test
    fun `an icon larger than the limit is not read`() = runBlocking {
        fake.iconBody = """<svg viewBox="0 0 18 18"><path d="M1,1"/>""" + " ".repeat(300 * 1024) + "</svg>"

        assertThat(client.clearGroupIcon(CLEAR_GROUP_UUID)).isEqualTo(Result.Failure(DataError.Network.SERIALIZATION))
    }

    @Test
    fun `every call uses only allowed methods and paths`() = runBlocking {
        fake.enqueueStream(fake.stream("status-updates", StreamEnd.EOF, timeScale = 0.0))
        val item = PlaylistItemKey(playlistUuid = FakeProPresenter.SERVICE_PLAYLIST_UUID, index = 4)

        client.version()
        client.playlists()
        client.libraries()
        client.library(Fixtures.LIBRARY_ID)
        client.triggerPresentationCue(FakeProPresenter.SONG_A_UUID, cueIndex = 1)
        client.playlist(FakeProPresenter.SERVICE_PLAYLIST_UUID)
        client.presentation(FakeProPresenter.SONG_A_UUID)
        client.slideIndex()
        client.activePlaylistItem()
        client.triggerCue(item, cueIndex = 2)
        client.triggerItem(item)
        client.clearLayer(OutputLayer.SLIDE)
        client.clearGroups()
        client.triggerClearGroup(CLEAR_GROUP_UUID)
        client.clearGroupIcon(CLEAR_GROUP_UUID)
        client.triggerNext()
        client.triggerPrevious()
        client.statusUpdates(listOf("status/slide")).first()

        assertThat(fake.requests.size).isEqualTo(18)
        FakeProPresenter.assertOnlyAllowedRequests(fake.requests)
    }

    private companion object {
        const val CLEAR_GROUP_UUID = "5da095db-20ef-4246-b3d5-3b741312386b"
    }
}
