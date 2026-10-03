package com.greenfodor.ppremotece.feature.playlist.items

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.FakeContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {
    private val content = FakeContentRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        content.playlists[PLAYLIST] = Playlist(
            uuid = PLAYLIST,
            name = "Arrangement Test",
            items = listOf(
                item(
                    0,
                    "Song A",
                    PlaylistItemType.PRESENTATION,
                    presentation = PresentationRef(SONG_A, "a-full", "Full")
                ),
                item(1, "Header 01", PlaylistItemType.HEADER),
                item(2, "Media 01", PlaylistItemType.MEDIA),
                item(3, "Track 01", PlaylistItemType.AUDIO),
                item(4, "Header 02", PlaylistItemType.HEADER, headerColor = OLIVE),
                item(5, "Live Video 01", PlaylistItemType.LIVE_VIDEO),
                item(6, "Placeholder 01", PlaylistItemType.PLACEHOLDER),
                item(7, "Item 01", PlaylistItemType.OTHER)
            )
        )
        content.presentations[SONG_A] = Presentation(
            uuid = SONG_A,
            name = "Song A",
            groups = listOf(Group("g-verse", "Verse 1", color = null, slides = listOf(Slide("Verse 1 · 1")))),
            arrangements = listOf(Arrangement("a-full", "Full", listOf("g-verse"), totalCues = 1))
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlaylistViewModel(PLAYLIST, content)

    @Test
    fun `the playlist is named and lists every item in order`() = runTest {
        val state = viewModel().state.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.name).isEqualTo("Arrangement Test")
        assertThat(state.rows.map { it.name }).containsExactly(
            "Song A",
            "Header 01",
            "Media 01",
            "Track 01",
            "Header 02",
            "Live Video 01",
            "Placeholder 01",
            "Item 01"
        )
    }

    @Test
    fun `a header carries its colour and a header without one carries none`() = runTest {
        val headers = viewModel().state.value.rows.filterIsInstance<PlaylistRowUi.Header>()

        assertThat(headers.map { it.color }).containsExactly(null, OLIVE)
    }

    @Test
    fun `each item row says what it opens`() = runTest {
        val items = viewModel().state.value.rows.filterIsInstance<PlaylistRowUi.Item>()

        assertThat(items.map { it.type to it.opens }).containsExactly(
            PlaylistItemType.PRESENTATION to RowTarget.SLIDES,
            PlaylistItemType.MEDIA to RowTarget.ITEM,
            PlaylistItemType.AUDIO to RowTarget.ITEM,
            PlaylistItemType.LIVE_VIDEO to RowTarget.ITEM,
            PlaylistItemType.PLACEHOLDER to RowTarget.NONE,
            PlaylistItemType.OTHER to RowTarget.NONE
        )
    }

    @Test
    fun `a presentation item is labelled with its arrangement`() = runTest {
        val items = viewModel().state.value.rows.filterIsInstance<PlaylistRowUi.Item>()

        assertThat(items.first().label).isEqualTo(ArrangementLabel.Named("Full"))
        assertThat(items[1].label).isNull()
    }

    @Test
    fun `a presentation row opens its slides and a media row opens its item screen`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(PlaylistAction.OnItemClick(PlaylistItemKey(PLAYLIST, 0)))
            assertThat(awaitItem()).isEqualTo(PlaylistEvent.OpenSlides(PlaylistItemKey(PLAYLIST, 0)))
            viewModel.onAction(PlaylistAction.OnItemClick(PlaylistItemKey(PLAYLIST, 2)))
            assertThat(awaitItem()).isEqualTo(PlaylistEvent.OpenItem(PlaylistItemKey(PLAYLIST, 2)))
            viewModel.onAction(PlaylistAction.OnItemClick(PlaylistItemKey(PLAYLIST, 3)))
            assertThat(awaitItem()).isEqualTo(PlaylistEvent.OpenItem(PlaylistItemKey(PLAYLIST, 3)))
        }
    }

    @Test
    fun `a header, a placeholder and an unknown item open nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(PlaylistAction.OnItemClick(PlaylistItemKey(PLAYLIST, 1)))
            viewModel.onAction(PlaylistAction.OnItemClick(PlaylistItemKey(PLAYLIST, 6)))
            viewModel.onAction(PlaylistAction.OnItemClick(PlaylistItemKey(PLAYLIST, 7)))
            expectNoEvents()
        }
    }

    @Test
    fun `a refresh reads the playlist and its presentations again`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(PlaylistAction.OnRefresh)

        assertThat(content.refreshed).containsExactly(PLAYLIST, SONG_A)
        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `a failed refresh shows its error and keeps the rows`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            content.failWith = DataError.Network.TIMEOUT
            viewModel.onAction(PlaylistAction.OnRefresh)
            assertThat(awaitItem() is PlaylistEvent.ShowError).isEqualTo(true)
        }
        assertThat(viewModel.state.value.rows.size).isEqualTo(8)
    }

    @Test
    fun `a playlist that cannot be read shows its error and a retry reads it again`() = runTest {
        content.failWith = DataError.Network.TIMEOUT
        val viewModel = viewModel()

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.isLoading).isFalse()

        content.failWith = null
        viewModel.onAction(PlaylistAction.OnRetryClick)

        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.name).isEqualTo("Arrangement Test")
    }

    private fun item(
        index: Int,
        name: String,
        type: PlaylistItemType,
        presentation: PresentationRef? = null,
        headerColor: GroupColor? = null
    ) = PlaylistItem(PlaylistItemKey(PLAYLIST, index), name, type, presentation, headerColor = headerColor)

    private companion object {
        const val PLAYLIST = "pl-1"
        const val SONG_A = "song-a"
        val OLIVE = GroupColor(0.33f, 0.42f, 0.18f, 1f)
    }
}
