package com.greenfodor.ppremotece.feature.playlist.items

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.transport.ItemLive
import com.greenfodor.ppremotece.core.domain.transport.TransportRepository
import com.greenfodor.ppremotece.feature.playlist.FakeAppPreferences
import com.greenfodor.ppremotece.feature.playlist.FakeContentRepository
import com.greenfodor.ppremotece.feature.playlist.FakeProPresenterClient
import com.greenfodor.ppremotece.feature.playlist.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistItemViewModelTest {
    private val content = FakeContentRepository()
    private val client = FakeProPresenterClient()
    private val messages = UiMessages()
    private val transports = object : TransportRepository {
        override val presentationTransport = MutableStateFlow<Loadable<Transport>>(Loadable.NotLoaded)
        override val audioTransport = MutableStateFlow<Loadable<Transport>>(Loadable.NotLoaded)
    }
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        content.playlists[PLAYLIST] = Playlist(
            uuid = PLAYLIST,
            name = "Arrangement Test",
            items = listOf(
                PlaylistItem(MEDIA, "Media 01", PlaylistItemType.MEDIA, null, targetUuid = "m-0", durationSeconds = 20),
                PlaylistItem(
                    AUDIO,
                    "Track 01",
                    PlaylistItemType.AUDIO,
                    null,
                    targetUuid = "a-0",
                    durationSeconds = 183
                ),
                PlaylistItem(LIVE_VIDEO, "Live Video 01", PlaylistItemType.LIVE_VIDEO, null)
            )
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val preferences = FakeAppPreferences()

    private fun viewModel(key: PlaylistItemKey) =
        PlaylistItemViewModel(key, content, transports, client, messages, preferences)

    private fun playing(uuid: String, isPlaying: Boolean = true) =
        Loadable.Loaded(Transport(isPlaying, uuid, "", "", false, 0.0))

    @Test
    fun `the card shows the item's name, type and duration`() = runTest(dispatcher) {
        viewModel(AUDIO).state.test {
            val state = expectMostRecentItem()
            assertThat(state.name).isEqualTo("Track 01")
            assertThat(state.type).isEqualTo(PlaylistItemType.AUDIO)
            assertThat(state.duration).isEqualTo("3:03")
            assertThat(state.live).isEqualTo(ItemLive.NONE)
        }
    }

    @Test
    fun `an item without a duration shows none`() = runTest(dispatcher) {
        viewModel(LIVE_VIDEO).state.test {
            assertThat(expectMostRecentItem().duration).isNull()
        }
    }

    @Test
    fun `a tap on the card triggers the item`() = runTest(dispatcher) {
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(PlaylistItemAction.OnCardClick)
        }

        assertThat(client.triggeredItems).containsExactly(MEDIA)
    }

    @Test
    fun `while locked the card sends nothing and posts Locked`() = runTest(dispatcher) {
        preferences.lock.value = true
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isTrue()
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredItems).isEmpty()
        assertLockedPosted()
    }

    private suspend fun assertLockedPosted() {
        messages.messages.test {
            assertThat((awaitItem() as UiText.StringResource).id).isEqualTo(DesignR.string.slide_locked)
        }
    }

    @Test
    fun `the card is locked until the lock is read, then a tap is sent`() = runTest(dispatcher) {
        preferences.lock.value = null
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isTrue()
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            assertLockedPosted()
            assertThat(client.triggeredItems).isEmpty()
            preferences.lock.value = false
            assertThat(expectMostRecentItem().locked).isFalse()
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredItems).containsExactly(MEDIA)
    }

    @Test
    fun `the card is locked when the lock cannot be read`() = runTest(dispatcher) {
        preferences.failRead = true
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isTrue()
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            assertLockedPosted()
            viewModel.onAction(PlaylistItemAction.OnLockClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredItems).isEmpty()
        assertThat(preferences.writes).isEmpty()
    }

    @Test
    fun `while the item loads the lock button shows the saved lock and toggles it`() = runTest(dispatcher) {
        content.pendingPlaylists += PLAYLIST
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isFalse()
            viewModel.onAction(PlaylistItemAction.OnLockClick)
            assertThat(expectMostRecentItem().locked).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(preferences.writes).containsExactly(true)
    }

    @Test
    fun `the lock button saves the other state`() = runTest(dispatcher) {
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isFalse()
            viewModel.onAction(PlaylistItemAction.OnLockClick)
            assertThat(awaitItem().locked).isTrue()
        }

        assertThat(preferences.writes).containsExactly(true)
    }

    @Test
    fun `a lock that can't be saved posts the setting message`() = runTest(dispatcher) {
        preferences.failWrites = true
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(PlaylistItemAction.OnLockClick)
            cancelAndIgnoreRemainingEvents()
        }

        messages.messages.test {
            assertThat((awaitItem() as UiText.StringResource).id).isEqualTo(DesignR.string.setting_not_saved)
        }
    }

    @Test
    fun `taps on the card are ignored while its request is in flight`() = runTest(dispatcher) {
        client.itemGate = CompletableDeferred()
        val viewModel = viewModel(MEDIA)

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            client.itemGate.complete(Unit)
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredItems).containsExactly(MEDIA, MEDIA)
    }

    @Test
    fun `a failed trigger posts its message with the item's name`() = runTest(dispatcher) {
        client.itemResult = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel(AUDIO)

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(PlaylistItemAction.OnCardClick)
            cancelAndIgnoreRemainingEvents()
        }

        messages.messages.test {
            val message = awaitItem() as UiText.StringResource
            assertThat(message.id).isEqualTo(R.string.playlist_item_error_start)
            assertThat(message.args).isEqualTo(listOf<Any>("Track 01"))
        }
    }

    @Test
    fun `a media item is live while the presentation transport plays its target`() = runTest(dispatcher) {
        viewModel(MEDIA).state.test {
            assertThat(expectMostRecentItem().live).isEqualTo(ItemLive.NONE)
            transports.presentationTransport.value = playing("m-0")
            assertThat(awaitItem().live).isEqualTo(ItemLive.LIVE)
            transports.presentationTransport.value = playing("")
            assertThat(awaitItem().live).isEqualTo(ItemLive.NONE)
        }
    }

    @Test
    fun `an audio item is live while played, paused while held and unmarked when unavailable`() = runTest(
        dispatcher
    ) {
        transports.audioTransport.value = playing("a-0")

        viewModel(AUDIO).state.test {
            assertThat(expectMostRecentItem().live).isEqualTo(ItemLive.LIVE)
            transports.audioTransport.value = playing("a-0", isPlaying = false)
            assertThat(awaitItem().live).isEqualTo(ItemLive.PAUSED)
            transports.audioTransport.value = Loadable.Unavailable
            assertThat(awaitItem().live).isEqualTo(ItemLive.NONE)
        }
    }

    @Test
    fun `an item that is no longer a media, audio or live-video item is not shown and cannot be triggered`() =
        runTest(dispatcher) {
            val viewModel = viewModel(MEDIA)

            viewModel.state.test {
                assertThat(expectMostRecentItem().type).isEqualTo(PlaylistItemType.MEDIA)
                content.playlists[PLAYLIST] = Playlist(
                    uuid = PLAYLIST,
                    name = "Arrangement Test",
                    items = listOf(PlaylistItem(MEDIA, "Header 01", PlaylistItemType.HEADER, null))
                )
                content.refreshPlaylist(PLAYLIST)
                val state = awaitItem()
                assertThat(state.type).isNull()
                assertThat(state.error).isNotNull()
                viewModel.onAction(PlaylistItemAction.OnCardClick)
            }

            assertThat(client.triggeredItems).isEqualTo(emptyList())
        }

    @Test
    fun `the card is still shown when the screen is collected again after a pause`() = runTest(dispatcher) {
        val viewModel = viewModel(AUDIO)
        viewModel.state.test { assertThat(expectMostRecentItem().name).isEqualTo("Track 01") }
        advanceTimeBy(STOP_TIMEOUT_MILLIS + 1_000)
        content.pendingPlaylists += PLAYLIST

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.name).isEqualTo("Track 01")
            assertThat(state.isLoading).isFalse()
            expectNoEvents()
        }
    }

    @Test
    fun `an item that cannot be read shows its error`() = runTest(dispatcher) {
        content.failWith = DataError.Network.TIMEOUT

        viewModel(MEDIA).state.test {
            val state = expectMostRecentItem()
            assertThat(state.error).isNotNull()
            assertThat(state.isLoading).isFalse()
        }
    }

    private companion object {
        const val PLAYLIST = "pl-1"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val MEDIA = PlaylistItemKey(PLAYLIST, 0)
        val AUDIO = PlaylistItemKey(PLAYLIST, 1)
        val LIVE_VIDEO = PlaylistItemKey(PLAYLIST, 2)
    }
}
