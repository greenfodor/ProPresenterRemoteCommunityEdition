package com.greenfodor.ppremotece.feature.playlist.grid

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.layout.GridPreferences
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.CueStep
import com.greenfodor.ppremotece.core.domain.live.CueSteps
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.MarkedCue
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CueSource
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
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import com.greenfodor.ppremotece.feature.playlist.FakeContentRepository
import com.greenfodor.ppremotece.feature.playlist.FakeProPresenterClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The slide grid after a clear, on Song C "A": Verse 1 (cue 1 disabled), Chorus, Verse 1 (cue 6 disabled). */
@OptIn(ExperimentalCoroutinesApi::class)
class SlideGridClearedTest {
    private val item = PlaylistItemKey(PLAYLIST, 6)
    private val otherItem = PlaylistItemKey(PLAYLIST, 7)
    private val content = FakeContentRepository()
    private val client = FakeProPresenterClient()
    private val live = MutableStateFlow(LiveState.Initial)
    private val remembered = MutableStateFlow<LiveCue?>(null)
    private val liveStateRepository = object : LiveStateRepository {
        override val liveState = live
        override val lastLive = remembered
    }
    private val thumbnailSource = object : ThumbnailSource {
        override val thumbnailRequests = MutableStateFlow<ThumbnailRequests?>(null)
    }
    private val gridPreferences = object : GridPreferences {
        override fun gridStep(widthClass: WidthClass) = flowOf(GridStep.Default)

        override suspend fun setGridStep(widthClass: WidthClass, step: GridStep): EmptyResult<DataError.Local> =
            Result.Success(Unit)

        override fun viewMode(widthClass: WidthClass) = flowOf(ViewMode.GRID)

        override suspend fun setViewMode(widthClass: WidthClass, mode: ViewMode): EmptyResult<DataError.Local> =
            Result.Success(Unit)
    }
    private val thumbnailCache = object : ThumbnailCache {
        override suspend fun clear() = Unit

        override suspend fun remove(keys: Collection<String>) = Unit
    }
    private val cleared = LiveState(ConnectionStatus.CONNECTED, item = null, slide = null)
    private val relativeSteps = CueSteps(next = CueStep.Relative, previous = CueStep.Relative)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val verse = Group(
            "g-verse",
            "Verse 1",
            null,
            listOf(Slide("Verse 1 · 1"), Slide("Verse 1 · 2", enabled = false), Slide("Verse 1 · 3"))
        )
        val chorus = Group("g-chorus", "Chorus", null, listOf(Slide("Chorus · 1"), Slide("Chorus · 2")))
        content.presentations[SONG_C] = Presentation(
            uuid = SONG_C,
            name = "Song C",
            groups = listOf(verse, chorus),
            arrangements = listOf(Arrangement("a-a", "A", listOf("g-verse", "g-chorus", "g-verse"), totalCues = 8)),
            currentArrangementUuid = "a-a"
        )
        content.playlists[PLAYLIST] = Playlist(
            uuid = PLAYLIST,
            name = "Arrangement Test",
            items = listOf(item, otherItem).map {
                PlaylistItem(it, "Song C", PlaylistItemType.PRESENTATION, PresentationRef(SONG_C, "a-a", "A"))
            }
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `after a clear the remembered cue is marked cleared with the next enabled cue`() = runTest {
        val viewModel = viewModel()
        remembered.value = LiveCue(CueSource.PlaylistItem(item), SONG_C, cueIndex = 5)
        live.value = cleared

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.marked).isEqualTo(MarkedCue(index = 5, cleared = true, next = 7))
            assertThat(state.steps).isEqualTo(CueSteps(next = CueStep.Explicit(7), previous = CueStep.Explicit(4)))
            assertThat(state.banner).isNull()
        }
    }

    @Test
    fun `while cleared next and previous send the enabled cues around the remembered one`() = runTest {
        val viewModel = viewModel()
        remembered.value = LiveCue(CueSource.PlaylistItem(item), SONG_C, cueIndex = 2)
        live.value = cleared

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
        }
        assertThat(client.triggeredCues).containsExactly(item to 3, item to 0)
        assertThat(client.steps).isEmpty()
    }

    @Test
    fun `while cleared on the last cue next is disabled and sends nothing`() = runTest {
        val viewModel = viewModel()
        remembered.value = LiveCue(CueSource.PlaylistItem(item), SONG_C, cueIndex = 7)
        live.value = cleared

        viewModel.state.test {
            assertThat(expectMostRecentItem().steps.next).isEqualTo(CueStep.Disabled)
            viewModel.onAction(SlideGridAction.OnNextClick)
        }
        assertThat(client.triggeredCues).isEmpty()
        assertThat(client.steps).isEmpty()
    }

    @Test
    fun `while a slide is live next and previous send trigger next and previous`() = runTest {
        val viewModel = viewModel()
        remembered.value = LiveCue(CueSource.PlaylistItem(item), SONG_C, cueIndex = 2)
        live.value = LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(SONG_C, index = 2, totalCues = 8))

        viewModel.state.test {
            assertThat(expectMostRecentItem().steps).isEqualTo(relativeSteps)
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
        }
        assertThat(client.steps).containsExactly("next", "previous")
        assertThat(client.triggeredCues).isEmpty()
    }

    @Test
    fun `a cleared library presentation steps over the presentation route`() = runTest {
        val viewModel = viewModel(CueSource.Presentation(SONG_C))
        remembered.value = LiveCue(CueSource.Presentation(SONG_C), SONG_C, cueIndex = 2)
        live.value = cleared

        viewModel.state.test {
            assertThat(expectMostRecentItem().marked).isEqualTo(MarkedCue(index = 2, cleared = true, next = 3))
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
        }
        assertThat(client.triggeredPresentationCues).containsExactly(SONG_C to 3, SONG_C to 0)
        assertThat(client.triggeredCues).isEmpty()
        assertThat(client.steps).isEmpty()
    }

    @Test
    fun `the grid of another item shows no mark for the remembered cue`() = runTest {
        val viewModel = viewModel()
        remembered.value = LiveCue(CueSource.PlaylistItem(otherItem), SONG_C, cueIndex = 2)
        live.value = cleared

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.marked).isNull()
            assertThat(state.steps).isEqualTo(relativeSteps)
        }
    }

    @Test
    fun `the group strip keeps the cleared cue's group picked out`() = runTest {
        val viewModel = viewModel()
        remembered.value = LiveCue(CueSource.PlaylistItem(item), SONG_C, cueIndex = 6)
        live.value = cleared

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.groupSequence.livePill).isEqualTo(2)
            assertThat(state.marked?.cleared).isEqualTo(true)
        }
    }

    @Test
    fun `cues carry whether they start a group`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().cues.map { it.startsGroup })
                .containsExactly(true, false, false, true, false, true, false, false)
        }
    }

    private fun viewModel(source: CueSource = CueSource.PlaylistItem(item)) =
        SlideGridViewModel(
            source,
            content,
            client,
            liveStateRepository,
            thumbnailSource,
            thumbnailCache,
            gridPreferences
        )

    private companion object {
        const val PLAYLIST = "pl-1"
        const val SONG_C = "p-c"
    }
}
