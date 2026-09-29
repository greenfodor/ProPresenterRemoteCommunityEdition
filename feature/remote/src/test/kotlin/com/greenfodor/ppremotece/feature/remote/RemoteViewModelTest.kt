package com.greenfodor.ppremotece.feature.remote

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.remote.BoxMark
import com.greenfodor.ppremotece.core.domain.remote.RemoteBox
import com.greenfodor.ppremotece.core.domain.remote.RemoteCommand
import com.greenfodor.ppremotece.core.domain.remote.RemoteStatus
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {
    private val client = FakeProPresenterClient()
    private val live = FakeLiveStateRepository()
    private val content = FakeContentRepository(mapOf(PLAYLIST to playlist), mapOf(SONG_A to songA, SONG_C to songC))
    private val thumbnails = object : ThumbnailSource {
        override val thumbnailRequests = MutableStateFlow<ThumbnailRequests?>(
            ThumbnailRequests {
                item,
                _,
                cue
                ->
                ThumbnailRequest("http://host/${item.index}/${cue.index}", "k${cue.index}")
            }
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        live.liveState.value = LiveState.Initial.copy(connection = ConnectionStatus.CONNECTED)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RemoteViewModel(client, live, content, thumbnails)

    @Test
    fun `taps and buttons send the display's commands`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        val viewModel = viewModel()

        viewModel.state.test {
            val state = awaitShowing()
            assertThat(state.currentThumbnail).isEqualTo(ThumbnailRequest("http://host/0/2", "k2"))
            assertThat(state.nextThumbnail).isEqualTo(ThumbnailRequest("http://host/0/3", "k3"))
            viewModel.onAction(RemoteAction.OnCurrentClick)
            viewModel.onAction(RemoteAction.OnNextBoxClick)
            viewModel.onAction(RemoteAction.OnNextClick)
            viewModel.onAction(RemoteAction.OnPreviousClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.sent).containsExactly(
            RemoteCommand.TriggerCue(key(0), 2),
            RemoteCommand.TriggerCue(key(0), 3),
            RemoteCommand.TriggerCue(key(0), 3),
            RemoteCommand.TriggerCue(key(0), 1)
        )
    }

    @Test
    fun `item steps cue an item and send nothing`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        val viewModel = viewModel()

        viewModel.state.test {
            awaitShowing()
            viewModel.onAction(RemoteAction.OnNextItemClick)
            val media = awaitUntil { it.display.cued }
            assertThat(
                media.display.current
            ).isEqualTo(RemoteBox.ItemCard("Loop", PlaylistItemType.MEDIA, BoxMark.CUED))

            viewModel.onAction(RemoteAction.OnNextItemClick)
            val songC = awaitUntil { it.display.current != media.display.current }
            assertThat((songC.display.current as RemoteBox.Slide).mark).isEqualTo(BoxMark.CUED)
            assertThat(songC.display.baseItem).isEqualTo(key(0))

            viewModel.onAction(RemoteAction.OnPreviousItemClick)
            awaitUntil { it.display.current == media.display.current }
            viewModel.onAction(RemoteAction.OnPreviousItemClick)
            assertThat(awaitUntil { !it.display.cued }.display.current).isInstanceOf(RemoteBox.Slide::class)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.sent).isEmpty()
    }

    @Test
    fun `tapping a cued item sends its cue 0 and next sends the same`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 1))
        val viewModel = viewModel()

        viewModel.state.test {
            awaitShowing()
            viewModel.onAction(RemoteAction.OnNextItemClick)
            viewModel.onAction(RemoteAction.OnNextItemClick)
            awaitUntil { it.display.cued && it.display.current is RemoteBox.Slide }
            viewModel.onAction(RemoteAction.OnCurrentClick)
            viewModel.onAction(RemoteAction.OnNextClick)
            viewModel.onAction(RemoteAction.OnNextBoxClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.sent).containsExactly(
            RemoteCommand.TriggerCue(key(4), 0),
            RemoteCommand.TriggerCue(key(4), 0),
            RemoteCommand.TriggerCue(key(4), 2)
        )
    }

    @Test
    fun `back to live leaves the cued item`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        val viewModel = viewModel()

        viewModel.state.test {
            val liveCurrent = awaitShowing().display.current
            viewModel.onAction(RemoteAction.OnNextItemClick)
            awaitUntil { it.display.cued }
            viewModel.onAction(RemoteAction.OnBackToLiveClick)
            assertThat(awaitUntil { !it.display.cued }.display.current).isEqualTo(liveCurrent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `another cue going live ends the cued state`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        val viewModel = viewModel()

        viewModel.state.test {
            awaitShowing()
            viewModel.onAction(RemoteAction.OnNextItemClick)
            awaitUntil { it.display.cued }
            live.goLive(LiveCue(key(0), SONG_A, 3))
            val live = awaitUntil { it.display.header?.cueNumber == 4 }
            assertThat(live.display.cued).isFalse()
            assertThat(live.display.current).isInstanceOf(RemoteBox.Slide::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a triggered media item shows live until a slide goes live`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        val viewModel = viewModel()

        viewModel.state.test {
            awaitShowing()
            viewModel.onAction(RemoteAction.OnNextItemClick)
            awaitUntil { it.display.cued }
            viewModel.onAction(RemoteAction.OnCurrentClick)
            val media = awaitUntil {
                it.display.current ==
                    RemoteBox.ItemCard("Loop", PlaylistItemType.MEDIA, BoxMark.LIVE)
            }
            assertThat(media.display.cued).isFalse()
            assertThat(media.display.nextButton == null).isTrue()

            live.goLive(LiveCue(key(4), SONG_C, 0))
            awaitUntil { (it.display.current as? RemoteBox.Slide)?.mark == BoxMark.LIVE }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.sent).containsExactly(RemoteCommand.TriggerItem(key(2)))
    }

    @Test
    fun `a cued item does not come back when the live cue returns to the cue it was chosen under`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 1))
        val viewModel = viewModel()

        viewModel.state.test {
            awaitShowing()
            viewModel.onAction(RemoteAction.OnNextItemClick)
            awaitUntil { it.display.cued }
            live.goLive(LiveCue(key(0), SONG_A, 2))
            awaitUntil { it.display.header?.cueNumber == 3 }
            live.goLive(LiveCue(key(0), SONG_A, 1))
            val back = awaitUntil { it.display.header?.cueNumber == 2 }
            assertThat(back.display.cued).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed presentation read shows an error and retry reads it again`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        content.failing += SONG_A
        val viewModel = viewModel()

        viewModel.state.test {
            val failed = awaitUntil { it.error != null }
            assertThat(failed.display.status).isEqualTo(RemoteStatus.LOADING)
            content.failing.clear()
            viewModel.onAction(RemoteAction.OnRetryClick)
            assertThat(awaitShowing().error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `item steps read each playlist and presentation once`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        val viewModel = viewModel()

        viewModel.state.test {
            awaitShowing()
            viewModel.onAction(RemoteAction.OnNextItemClick)
            awaitUntil { it.display.cued }
            viewModel.onAction(RemoteAction.OnNextItemClick)
            awaitUntil { it.display.current is RemoteBox.Slide && it.display.cued }
            viewModel.onAction(RemoteAction.OnBackToLiveClick)
            awaitUntil { !it.display.cued }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(content.reads.count { it == PLAYLIST }).isEqualTo(1)
        assertThat(content.reads.count { it == SONG_A }).isEqualTo(1)
        assertThat(content.reads.count { it == SONG_C }).isEqualTo(1)
    }

    @Test
    fun `a slide outside the playlist steps with trigger next and previous`() = runTest {
        live.liveState.value = LiveState(
            ConnectionStatus.CONNECTED,
            item = null,
            slide = LiveSlide(SONG_A, 1, 5),
            slideText = SlideText("Text 03", "Text 04")
        )
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitShowing().display.current).isEqualTo(RemoteBox.Text("Text 03"))
            viewModel.onAction(RemoteAction.OnNextClick)
            viewModel.onAction(RemoteAction.OnPreviousClick)
            viewModel.onAction(RemoteAction.OnCurrentClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.sent).containsExactly(RemoteCommand.TriggerNext, RemoteCommand.TriggerPrevious)
    }

    @Test
    fun `a failed trigger is reported`() = runTest {
        live.goLive(LiveCue(key(0), SONG_A, 2))
        client.failTriggers = DataError.Network.NO_CONNECTION
        val viewModel = viewModel()

        viewModel.state.test {
            awaitShowing()
            viewModel.events.test {
                viewModel.onAction(RemoteAction.OnNextClick)
                assertThat(awaitItem()).isInstanceOf(RemoteEvent.ShowError::class)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<RemoteState>.awaitShowing(): RemoteState =
        awaitUntil { it.display.status == RemoteStatus.SHOWING }

    private suspend fun ReceiveTurbine<RemoteState>.awaitUntil(predicate: (RemoteState) -> Boolean): RemoteState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private companion object {
        const val PLAYLIST = "pl"
        const val SONG_A = "song-a"
        const val SONG_C = "song-c"

        fun key(index: Int) = PlaylistItemKey(PLAYLIST, index)

        val songA = Presentation(
            uuid = SONG_A,
            name = "Song A",
            groups = listOf(
                Group("verse", "Verse 1", null, List(3) { Slide("Verse 1 · ${it + 1}") }),
                Group("chorus", "Chorus", null, List(2) { Slide("Chorus · ${it + 1}") })
            ),
            arrangements = listOf(Arrangement("full", "Full", listOf("verse", "chorus"), totalCues = 5))
        )

        val songC = Presentation(
            uuid = SONG_C,
            name = "Song C",
            groups = listOf(
                Group("v", "Verse 1", null, listOf(Slide("V1 A"), Slide("V1 B", enabled = false), Slide("V1 C")))
            ),
            arrangements = listOf(Arrangement("a", "A", listOf("v"), totalCues = 3))
        )

        val playlist = Playlist(
            uuid = PLAYLIST,
            name = "Arrangement Test",
            items = listOf(
                PlaylistItem(key(0), "Song A", PlaylistItemType.PRESENTATION, PresentationRef(SONG_A, "full", "Full")),
                PlaylistItem(key(1), "Header", PlaylistItemType.HEADER, null),
                PlaylistItem(key(2), "Loop", PlaylistItemType.MEDIA, null),
                PlaylistItem(key(3), "Placeholder", PlaylistItemType.PLACEHOLDER, null),
                PlaylistItem(key(4), "Song C", PlaylistItemType.PRESENTATION, PresentationRef(SONG_C, "a", "A"))
            )
        )
    }
}
