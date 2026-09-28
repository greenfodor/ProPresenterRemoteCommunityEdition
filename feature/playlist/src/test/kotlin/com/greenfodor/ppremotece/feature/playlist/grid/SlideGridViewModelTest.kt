package com.greenfodor.ppremotece.feature.playlist.grid

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideSize
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import com.greenfodor.ppremotece.feature.playlist.FakeContentRepository
import com.greenfodor.ppremotece.feature.playlist.FakeProPresenterClient
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
class SlideGridViewModelTest {
    private val item = PlaylistItemKey(PLAYLIST, 6)
    private val content = FakeContentRepository()
    private val client = FakeProPresenterClient()
    private val live = MutableStateFlow(LiveState.Initial)
    private val liveStateRepository = object : LiveStateRepository {
        override val liveState = live
    }
    private val thumbnailSource = object : ThumbnailSource {
        override val thumbnailRequests = MutableStateFlow<ThumbnailRequests?>(
            ThumbnailRequests { item, presentationUuid, cue ->
                ThumbnailRequest(
                    url = "http://host/${item.playlistUuid}/${item.index}/thumbnail/${cue.index}",
                    cacheKey = "$presentationUuid:${cue.groupUuid}:${cue.slideIndexInGroup}"
                )
            }
        )
    }
    private val thumbnailCache = object : ThumbnailCache {
        val removed = mutableListOf<List<String>>()

        override suspend fun clear() = Unit

        override suspend fun remove(keys: Collection<String>) {
            removed += keys.toList()
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        content.playlists[PLAYLIST] = Playlist(
            uuid = PLAYLIST,
            name = "Arrangement Test",
            items = listOf(
                PlaylistItem(
                    key = item,
                    name = "Song C",
                    type = PlaylistItemType.PRESENTATION,
                    presentation = PresentationRef(SONG_C, arrangementUuid = "a-a", arrangementName = "A")
                )
            )
        )
        content.presentations[SONG_C] = songC(chorusText = "Chorus · 1")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cues follow the item's arrangement with disabled cues kept in place`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.cues.map { it.index }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7)
            assertThat(state.cues.filterNot { it.enabled }.map { it.index }).containsExactly(1, 6)
            assertThat(state.aspect).isEqualTo(1920f / 858f)
        }
    }

    @Test
    fun `live and next mark the live cue and the next enabled cue`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().nextCueIndex).isNull()
            live.value = LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(SONG_C, index = 0, totalCues = 8))

            val state = awaitItem()
            assertThat(state.liveCueIndex).isEqualTo(0)
            assertThat(state.nextCueIndex).isEqualTo(2)
        }
    }

    @Test
    fun `next is not shown while another item is live`() = runTest {
        val viewModel = viewModel()
        live.value = LiveState(
            ConnectionStatus.CONNECTED,
            PlaylistItemKey(PLAYLIST, 5),
            LiveSlide(SONG_C, index = 0, totalCues = 8)
        )

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.liveCueIndex).isNull()
            assertThat(state.nextCueIndex).isNull()
        }
    }

    @Test
    fun `a disabled cue is not triggered`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SlideGridAction.OnCueClick(1))
            assertThat(client.triggeredCues).isEmpty()

            viewModel.onAction(SlideGridAction.OnCueClick(0))
            assertThat(client.triggeredCues).containsExactly(item to 0)
        }
    }

    @Test
    fun `reload reads the item again and shows the changed slides`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().cues[3].text).isEqualTo("Chorus · 1")
            content.presentations[SONG_C] = songC(chorusText = "Chorus · 1 edited")

            viewModel.onAction(SlideGridAction.OnReloadClick)

            assertThat(awaitItem().cues[3].text).isEqualTo("Chorus · 1 edited")
            assertThat(content.refreshed).containsExactly(PLAYLIST, SONG_C)
        }
    }

    @Test
    fun `each cue carries its thumbnail request and repeats share a cache key`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            val cues = awaitItem().cues
            assertThat(cues[0].thumbnail?.url).isEqualTo("http://host/$PLAYLIST/6/thumbnail/0")
            assertThat(cues[5].thumbnail?.cacheKey).isEqualTo(cues[0].thumbnail?.cacheKey)
            assertThat(cues.mapNotNull { it.thumbnail?.cacheKey }.distinct().size).isEqualTo(5)
        }
    }

    @Test
    fun `reload evicts the item's thumbnails once per key and loads them again`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().thumbnailGeneration).isEqualTo(0)

            viewModel.onAction(SlideGridAction.OnReloadClick)

            assertThat(awaitItem().thumbnailGeneration).isEqualTo(1)
            assertThat(thumbnailCache.removed.single().size).isEqualTo(5)
            assertThat(thumbnailCache.removed.single()).isEqualTo(
                listOf("p-c:g-verse:0", "p-c:g-verse:1", "p-c:g-verse:2", "p-c:g-chorus:0", "p-c:g-chorus:1")
            )
        }
    }

    @Test
    fun `a failed reload keeps the slides and reports the error`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().cues.size).isEqualTo(8)
            viewModel.events.test {
                content.failWith = DataError.Network.TIMEOUT
                viewModel.onAction(SlideGridAction.OnReloadClick)

                assertThat(awaitItem()).isInstanceOf<SlideGridEvent.ShowError>()
            }
            val state = awaitItem()
            assertThat(state.cues.size).isEqualTo(8)
            assertThat(state.error).isNull()
        }
    }

    @Test
    fun `an arrangement that expands to fewer cues than expected is flagged`() = runTest {
        content.presentations[SONG_C] = songC(chorusText = "Chorus · 1").let {
            it.copy(arrangements = listOf(it.arrangements.single().copy(totalCues = 9)))
        }
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().countMismatch).isTrue()
        }
    }

    private fun viewModel() =
        SlideGridViewModel(item, content, client, liveStateRepository, thumbnailSource, thumbnailCache)

    private fun songC(chorusText: String): Presentation {
        val size = SlideSize(1920, 858)
        val verse = Group(
            uuid = "g-verse",
            name = "Verse 1",
            color = null,
            slides = listOf(
                Slide("Verse 1 · 1", size = size),
                Slide("Verse 1 · 2", enabled = false, size = size),
                Slide("Verse 1 · 3", size = size)
            )
        )
        val chorus = Group(
            uuid = "g-chorus",
            name = "Chorus",
            color = null,
            slides = listOf(Slide(chorusText, size = size), Slide("Chorus · 2", size = size))
        )
        return Presentation(
            uuid = SONG_C,
            name = "Song C",
            groups = listOf(verse, chorus),
            arrangements = listOf(Arrangement("a-a", "A", listOf("g-verse", "g-chorus", "g-verse"), totalCues = 8))
        )
    }

    private companion object {
        const val PLAYLIST = "pl-1"
        const val SONG_C = "p-c"
    }
}
