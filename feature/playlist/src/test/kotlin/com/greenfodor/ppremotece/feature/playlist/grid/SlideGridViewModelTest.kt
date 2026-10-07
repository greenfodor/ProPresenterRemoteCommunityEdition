package com.greenfodor.ppremotece.feature.playlist.grid

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementBanner
import com.greenfodor.ppremotece.core.domain.arrangement.NoMatchReason
import com.greenfodor.ppremotece.core.domain.arrangement.ResyncTarget
import com.greenfodor.ppremotece.core.domain.layout.GridPreferences
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.CueStep
import com.greenfodor.ppremotece.core.domain.live.CueSteps
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
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
import com.greenfodor.ppremotece.core.domain.model.SlideSize
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import com.greenfodor.ppremotece.feature.playlist.FakeContentRepository
import com.greenfodor.ppremotece.feature.playlist.FakeProPresenterClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

@OptIn(ExperimentalCoroutinesApi::class)
class SlideGridViewModelTest {
    private val item = PlaylistItemKey(PLAYLIST, 6)
    private val otherItem = PlaylistItemKey(PLAYLIST, 7)
    private val chorusItem = PlaylistItemKey(PLAYLIST, 8)
    private val content = FakeContentRepository()
    private val client = FakeProPresenterClient()
    private val live = MutableStateFlow(LiveState.Initial)
    private val liveStateRepository = object : LiveStateRepository {
        override val liveState = live
        override val lastLive = MutableStateFlow<LiveCue?>(null)
    }
    private val thumbnailSource = object : ThumbnailSource {
        override val thumbnailRequests = MutableStateFlow<ThumbnailRequests?>(
            ThumbnailRequests { source, presentationUuid, cue, _ ->
                val path = when (source) {
                    is CueSource.PlaylistItem -> "${source.key.playlistUuid}/${source.key.index}"
                    is CueSource.Presentation -> "presentation/${source.uuid}"
                }
                ThumbnailRequest(
                    url = "http://host/$path/thumbnail/${cue.index}",
                    cacheKey = "$presentationUuid:${cue.groupUuid}:${cue.slideIndexInGroup}"
                )
            }
        )
    }
    private val gridPreferences = object : GridPreferences {
        val steps = MutableStateFlow(mapOf<WidthClass, GridStep>())

        override fun gridStep(widthClass: WidthClass) = steps.map { it[widthClass] ?: GridStep.Default }

        var writes = 0
        var failWrites = false

        override suspend fun setGridStep(widthClass: WidthClass, step: GridStep): EmptyResult<DataError.Local> {
            writes++
            if (failWrites) return Result.Failure(DataError.Local.WRITE_FAILED)
            steps.value += widthClass to step
            return Result.Success(Unit)
        }

        val modes = MutableStateFlow(mapOf<WidthClass, ViewMode>())

        override fun viewMode(widthClass: WidthClass) = modes.map { it[widthClass] ?: ViewMode.GRID }

        override suspend fun setViewMode(widthClass: WidthClass, mode: ViewMode): EmptyResult<DataError.Local> {
            if (failWrites) return Result.Failure(DataError.Local.WRITE_FAILED)
            modes.value += widthClass to mode
            return Result.Success(Unit)
        }
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
                ),
                PlaylistItem(
                    key = otherItem,
                    name = "Song C",
                    type = PlaylistItemType.PRESENTATION,
                    presentation = PresentationRef(SONG_C, arrangementUuid = "a-b", arrangementName = "B")
                ),
                PlaylistItem(
                    key = chorusItem,
                    name = "Song C",
                    type = PlaylistItemType.PRESENTATION,
                    presentation = PresentationRef(SONG_C, arrangementUuid = "a-c", arrangementName = "Chorus Only")
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
            assertThat(awaitItem().marked?.next).isNull()
            live.value = LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(SONG_C, index = 0, totalCues = 8))

            val state = awaitItem()
            assertThat(state.marked?.index).isEqualTo(0)
            assertThat(state.marked?.next).isEqualTo(2)
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
            assertThat(state.marked?.index).isNull()
            assertThat(state.marked?.next).isNull()
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
    fun `reload evicts the item's grid and box thumbnails once per key and loads them again`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().thumbnailGeneration).isEqualTo(0)

            viewModel.onAction(SlideGridAction.OnReloadClick)

            assertThat(awaitItem().thumbnailGeneration).isEqualTo(1)
            val gridKeys =
                listOf("p-c:g-verse:0", "p-c:g-verse:1", "p-c:g-verse:2", "p-c:g-chorus:0", "p-c:g-chorus:1")
            assertThat(thumbnailCache.removed.single()).isEqualTo(
                gridKeys + gridKeys.flatMap { key -> listOf(600, 800, 1000, 1080).map { "$key:q$it" } }
            )
        }
    }

    @Test
    fun `a new host connection loads the thumbnails again`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().thumbnailGeneration).isEqualTo(0)

            thumbnailSource.thumbnailRequests.value = ThumbnailRequests { _, presentationUuid, cue, _ ->
                ThumbnailRequest("http://other/${cue.index}", "$presentationUuid:${cue.index}")
            }

            assertThat(awaitItem().thumbnailGeneration).isEqualTo(1)
        }
    }

    @Test
    fun `the slide size waits for the width class and follows it`() = runTest {
        gridPreferences.steps.value = mapOf(WidthClass.EXPANDED to GridStep.SIZE_280)
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().gridStep).isNull()

            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.EXPANDED))
            assertThat(viewModel.state.value.gridStep).isEqualTo(GridStep.SIZE_280)

            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))
            assertThat(viewModel.state.value.gridStep).isEqualTo(GridStep.Default)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a dragged step shows at once and is saved once when the drag ends`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))
            assertThat(expectMostRecentItem().gridStep).isEqualTo(GridStep.Default)

            viewModel.onAction(SlideGridAction.OnGridStepChange(GridStep.SIZE_160))
            viewModel.onAction(SlideGridAction.OnGridStepChange(GridStep.SIZE_120))
            assertThat(expectMostRecentItem().gridStep).isEqualTo(GridStep.SIZE_120)
            assertThat(gridPreferences.writes).isEqualTo(0)

            viewModel.onAction(SlideGridAction.OnGridStepChangeFinished)
            expectNoEvents()
        }
        assertThat(gridPreferences.writes).isEqualTo(1)
        assertThat(gridPreferences.steps.value).isEqualTo(mapOf(WidthClass.COMPACT to GridStep.SIZE_120))
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
            expectNoEvents()
        }
        assertThat(thumbnailCache.removed).isEmpty()
    }

    @Test
    fun `an arrangement that expands to fewer cues than expected is flagged`() = runTest {
        content.presentations[SONG_C] = songC(chorusText = "Chorus · 1").let {
            it.copy(arrangements = it.arrangements.map { a -> if (a.uuid == "a-a") a.copy(totalCues = 9) else a })
        }
        val viewModel = viewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.countMismatch).isTrue()
            assertThat(state.cues.mapNotNull { it.thumbnail }).isEmpty()

            viewModel.onAction(SlideGridAction.OnReloadClick)
            expectNoEvents()
        }
        assertThat(thumbnailCache.removed).isEmpty()
    }

    @Test
    fun `a library presentation shows its current arrangement's cues and is live only outside a playlist`() = runTest {
        val viewModel = viewModel(CueSource.Presentation(SONG_C))

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.title).isEqualTo("Song C")
            assertThat(state.cues.map { it.index }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7)
            assertThat(state.steps).isEqualTo(disabledSteps)
            assertThat(state.cues[0].thumbnail?.url).isEqualTo("http://host/presentation/$SONG_C/thumbnail/0")

            live.value = outside(cue = 2, totalCues = 8)
            assertThat(awaitItem().marked?.index).isEqualTo(2)
            live.value = outside(cue = 2, totalCues = 9)
            assertThat(awaitItem().marked?.index).isNull()
            live.value = outside(cue = 0, totalCues = 8)
            assertThat(awaitItem().marked?.next).isEqualTo(2)
            live.value = outside(cue = 0, totalCues = 8).copy(item = item)
            assertThat(awaitItem().marked?.index).isNull()
        }
    }

    @Test
    fun `a library presentation's next and previous are disabled while loading or after a failed read`() = runTest {
        content.failWith = DataError.Network.SERVER
        val viewModel = viewModel(CueSource.Presentation(SONG_C))

        assertThat(viewModel.state.value.steps).isEqualTo(disabledSteps)
        viewModel.state.test {
            val failed = awaitItem()
            assertThat(failed.error).isNotNull()
            assertThat(failed.steps).isEqualTo(disabledSteps)
        }
    }

    @Test
    fun `a library presentation's next and previous send only while it is live outside a playlist`() = runTest {
        val viewModel = viewModel(CueSource.Presentation(SONG_C))

        viewModel.state.test {
            assertThat(awaitItem().steps).isEqualTo(disabledSteps)
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
            assertThat(client.steps).isEmpty()

            live.value = outside(cue = 3, totalCues = 8)
            assertThat(awaitItem().steps).isEqualTo(relativeSteps)
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
            assertThat(client.steps).containsExactly("next", "previous")

            live.value = outside(cue = 3, totalCues = 8).copy(item = item)
            assertThat(awaitItem().steps).isEqualTo(disabledSteps)
            viewModel.onAction(SlideGridAction.OnNextClick)
            assertThat(client.steps).containsExactly("next", "previous")
        }
        assertThat(client.triggeredPresentationCues).isEmpty()
    }

    @Test
    fun `a playlist item's next and previous are always enabled`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().steps).isEqualTo(relativeSteps)
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
        }
        assertThat(client.steps).containsExactly("next", "previous")
    }

    @Test
    fun `list mode builds no thumbnail requests`() = runTest {
        gridPreferences.modes.value = mapOf(WidthClass.COMPACT to ViewMode.LIST)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))
            val list = viewModel.state.value
            assertThat(list.viewMode).isEqualTo(ViewMode.LIST)
            assertThat(list.cues.size).isEqualTo(8)
            assertThat(list.cues.mapNotNull { it.thumbnail }).isEmpty()

            viewModel.onAction(SlideGridAction.OnViewModeChange(ViewMode.GRID))
            assertThat(viewModel.state.value.cues.mapNotNull { it.thumbnail }.size).isEqualTo(8)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the first visible cue is kept across a view mode switch`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))
            assertThat(viewModel.firstVisibleCue).isEqualTo(0)
            viewModel.onAction(SlideGridAction.OnFirstVisibleCueChange(5))
            viewModel.onAction(SlideGridAction.OnViewModeChange(ViewMode.LIST))
            assertThat(viewModel.state.value.viewMode).isEqualTo(ViewMode.LIST)
            assertThat(viewModel.firstVisibleCue).isEqualTo(5)
            viewModel.onAction(SlideGridAction.OnViewModeChange(ViewMode.GRID))
            assertThat(viewModel.firstVisibleCue).isEqualTo(5)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a playlist item is not live while its presentation plays outside the playlist`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            live.value = outside(cue = 2, totalCues = 8)
            expectNoEvents()
            assertThat(viewModel.state.value.marked?.index).isNull()
        }
    }

    @Test
    fun `a tap on a library presentation's cue sends the presentation trigger`() = runTest {
        val viewModel = viewModel(CueSource.Presentation(SONG_C))

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SlideGridAction.OnCueClick(3))
            viewModel.onAction(SlideGridAction.OnCueClick(1))
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
        }

        assertThat(client.triggeredPresentationCues).containsExactly(SONG_C to 3)
        assertThat(client.triggeredCues).isEmpty()
        assertThat(client.steps).isEmpty()
    }

    @Test
    fun `reloading a library presentation reads only the presentation`() = runTest {
        val viewModel = viewModel(CueSource.Presentation(SONG_C))

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SlideGridAction.OnReloadClick)
            assertThat(awaitItem().thumbnailGeneration).isEqualTo(1)
        }

        assertThat(content.refreshed).containsExactly(SONG_C)
    }

    @Test
    fun `the view mode is read and saved for the current width class`() = runTest {
        gridPreferences.modes.value = mapOf(WidthClass.EXPANDED to ViewMode.LIST)
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().viewMode).isNull()
            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.EXPANDED))
            assertThat(viewModel.state.value.viewMode).isEqualTo(ViewMode.LIST)

            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))
            assertThat(viewModel.state.value.viewMode).isEqualTo(ViewMode.GRID)
            viewModel.onAction(SlideGridAction.OnViewModeChange(ViewMode.LIST))
            assertThat(viewModel.state.value.viewMode).isEqualTo(ViewMode.LIST)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(gridPreferences.modes.value)
            .isEqualTo(mapOf(WidthClass.EXPANDED to ViewMode.LIST, WidthClass.COMPACT to ViewMode.LIST))
    }

    @Test
    fun `another item live in another arrangement shows the banner`() = runTest {
        val viewModel = viewModel()
        live.value = LiveState(ConnectionStatus.CONNECTED, otherItem, LiveSlide(SONG_C, index = 1, totalCues = 5))

        viewModel.state.test {
            assertThat(expectMostRecentItem().banner)
                .isEqualTo(ArrangementBanner(arrangementName = "B", isSongOrder = false, totalCues = 5))
            live.value = LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(SONG_C, index = 4, totalCues = 8))
            assertThat(expectMostRecentItem().banner).isNull()
        }
    }

    @Test
    fun `a live item whose playlist is still being read names no earlier item's arrangement`() = runTest {
        content.pendingPlaylists += PENDING_PLAYLIST
        val viewModel = viewModel()
        live.value = LiveState(ConnectionStatus.CONNECTED, otherItem, LiveSlide(SONG_C, index = 1, totalCues = 5))

        viewModel.state.test {
            assertThat(expectMostRecentItem().banner?.arrangementName).isEqualTo("B")
            live.value = LiveState(
                ConnectionStatus.CONNECTED,
                PlaylistItemKey(PENDING_PLAYLIST, 0),
                LiveSlide(SONG_C, index = 1, totalCues = 5)
            )
            assertThat(expectMostRecentItem().banner)
                .isEqualTo(ArrangementBanner(arrangementName = null, isSongOrder = false, totalCues = 5))
        }
    }

    @Test
    fun `the grid loads while the live item's playlist is still being read`() = runTest {
        content.pendingPlaylists += PENDING_PLAYLIST
        live.value = LiveState(
            ConnectionStatus.CONNECTED,
            PlaylistItemKey(PENDING_PLAYLIST, 0),
            LiveSlide(SONG_C, index = 1, totalCues = 5)
        )
        val viewModel = viewModel()

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.cues.size).isEqualTo(8)
        }
    }

    @Test
    fun `re-sync triggers this item's cue showing the live slide`() = runTest {
        val viewModel = viewModel()
        live.value = LiveState(ConnectionStatus.CONNECTED, otherItem, LiveSlide(SONG_C, index = 1, totalCues = 5))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.banner).isNotNull()
            assertThat(state.resync).isEqualTo(ResyncTarget.Cue(4))
            viewModel.onAction(SlideGridAction.OnResyncClick)
        }

        assertThat(client.triggeredCues).containsExactly(item to 4)
    }

    @Test
    fun `re-sync is disabled and sends nothing when the live slide is not in this item's arrangement`() = runTest {
        val viewModel = viewModel(CueSource.PlaylistItem(chorusItem))
        live.value = LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(SONG_C, index = 0, totalCues = 8))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.banner).isNotNull()
            assertThat(state.resync).isEqualTo(ResyncTarget.NoMatch(NoMatchReason.NOT_IN_ARRANGEMENT))
            viewModel.onAction(SlideGridAction.OnResyncClick)
        }

        assertThat(client.triggeredCues).isEmpty()
    }

    @Test
    fun `re-sync is disabled without a reason while the live item's playlist is loading`() = runTest {
        content.pendingPlaylists += PENDING_PLAYLIST
        val viewModel = viewModel()
        live.value = LiveState(
            ConnectionStatus.CONNECTED,
            PlaylistItemKey(PENDING_PLAYLIST, 0),
            LiveSlide(SONG_C, index = 1, totalCues = 5)
        )

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.banner).isNotNull()
            assertThat(state.resync).isNull()
            viewModel.onAction(SlideGridAction.OnResyncClick)
        }

        assertThat(client.triggeredCues).isEmpty()
    }

    @Test
    fun `re-sync is disabled and sends nothing when the live arrangement is unknown`() = runTest {
        content.playlists[UNRESOLVED_PLAYLIST] = Playlist(
            uuid = UNRESOLVED_PLAYLIST,
            name = "Playlist 02",
            items = listOf(
                PlaylistItem(
                    key = PlaylistItemKey(UNRESOLVED_PLAYLIST, 0),
                    name = "Song C",
                    type = PlaylistItemType.PRESENTATION,
                    presentation = PresentationRef(SONG_C, arrangementUuid = "a-missing", arrangementName = "Missing")
                )
            )
        )
        val viewModel = viewModel()
        live.value = LiveState(
            ConnectionStatus.CONNECTED,
            PlaylistItemKey(UNRESOLVED_PLAYLIST, 0),
            LiveSlide(SONG_C, index = 1, totalCues = 5)
        )

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.banner).isNotNull()
            assertThat(state.resync).isEqualTo(ResyncTarget.NoMatch(NoMatchReason.LIVE_ARRANGEMENT_UNKNOWN))
            viewModel.onAction(SlideGridAction.OnResyncClick)
        }

        assertThat(client.triggeredCues).isEmpty()
    }

    @Test
    fun `a view mode that can't be saved shows the setting message`() = runTest {
        gridPreferences.failWrites = true
        val viewModel = viewModel()
        viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))

        viewModel.events.test {
            viewModel.onAction(SlideGridAction.OnViewModeChange(ViewMode.LIST))

            assertThat(awaitItem().messageId()).isEqualTo(DesignR.string.setting_not_saved)
        }
    }

    @Test
    fun `a slide size that can't be saved shows the setting message`() = runTest {
        gridPreferences.failWrites = true
        val viewModel = viewModel()
        viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))

        viewModel.events.test {
            viewModel.onAction(SlideGridAction.OnGridStepChange(GridStep.SIZE_120))
            viewModel.onAction(SlideGridAction.OnGridStepChangeFinished)

            assertThat(awaitItem().messageId()).isEqualTo(DesignR.string.setting_not_saved)
        }
    }

    @Test
    fun `the group strip lists each group occurrence and marks the live one`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().groupSequence.pills.map { it.name to it.firstCueIndex })
                .containsExactly("Verse 1" to 0, "Chorus" to 3, "Verse 1" to 5)
            live.value = LiveState(ConnectionStatus.CONNECTED, item, LiveSlide(SONG_C, index = 6, totalCues = 8))
            assertThat(expectMostRecentItem().groupSequence.livePill).isEqualTo(2)
        }
    }

    @Test
    fun `a group pill tap scrolls to the occurrence's first cue`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SlideGridAction.OnGroupPillClick(firstCueIndex = 5))

            assertThat(awaitItem()).isEqualTo(SlideGridEvent.ScrollToCue(5))
        }
    }

    private val relativeSteps = CueSteps(next = CueStep.Relative, previous = CueStep.Relative)
    private val disabledSteps = CueSteps(next = CueStep.Disabled, previous = CueStep.Disabled)

    private fun SlideGridEvent.messageId() = ((this as SlideGridEvent.ShowError).message as UiText.StringResource).id

    private fun outside(cue: Int, totalCues: Int) =
        LiveState(ConnectionStatus.CONNECTED, item = null, slide = LiveSlide(SONG_C, cue, totalCues))

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
            arrangements = listOf(
                Arrangement("a-a", "A", listOf("g-verse", "g-chorus", "g-verse"), totalCues = 8),
                Arrangement("a-b", "B", listOf("g-chorus", "g-verse"), totalCues = 5),
                Arrangement("a-c", "Chorus Only", listOf("g-chorus"), totalCues = 2)
            ),
            currentArrangementUuid = "a-a"
        )
    }

    private companion object {
        const val PLAYLIST = "pl-1"
        const val SONG_C = "p-c"
        const val PENDING_PLAYLIST = "pl-2"
        const val UNRESOLVED_PLAYLIST = "pl-3"
    }
}
