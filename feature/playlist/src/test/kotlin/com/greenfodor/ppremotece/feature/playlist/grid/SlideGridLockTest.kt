package com.greenfodor.ppremotece.feature.playlist.grid

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
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
import com.greenfodor.ppremotece.feature.playlist.FakeAppPreferences
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
class SlideGridLockTest {
    private val item = PlaylistItemKey(PLAYLIST, 6)
    private val otherItem = PlaylistItemKey(PLAYLIST, 7)
    private val chorusItem = PlaylistItemKey(PLAYLIST, 8)
    private val content = FakeContentRepository()
    private val client = FakeProPresenterClient()
    private val preferences = FakeAppPreferences()
    private val messages = UiMessages()
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

        override suspend fun setGridStep(widthClass: WidthClass, step: GridStep): EmptyResult<DataError.Local> {
            steps.value += widthClass to step
            return Result.Success(Unit)
        }

        val modes = MutableStateFlow(mapOf<WidthClass, ViewMode>())

        override fun viewMode(widthClass: WidthClass) = modes.map { it[widthClass] ?: ViewMode.GRID }

        override suspend fun setViewMode(widthClass: WidthClass, mode: ViewMode): EmptyResult<DataError.Local> {
            modes.value += widthClass to mode
            return Result.Success(Unit)
        }
    }
    private val thumbnailCache = object : ThumbnailCache {
        override suspend fun clear() = Unit

        override suspend fun remove(keys: Collection<String>) = Unit
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
    fun `while locked a cue tap, next, previous and re-sync send nothing and post Locked`() = runTest {
        preferences.lock.value = true
        val viewModel = viewModel()
        live.value = LiveState(ConnectionStatus.CONNECTED, otherItem, LiveSlide(SONG_C, index = 1, totalCues = 5))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.locked).isTrue()
            assertThat(state.resync).isEqualTo(ResyncTarget.Cue(4))
            listOf(
                SlideGridAction.OnCueClick(0),
                SlideGridAction.OnNextClick,
                SlideGridAction.OnPreviousClick,
                SlideGridAction.OnResyncClick
            ).forEach { action ->
                viewModel.onAction(action)
                assertLockedPosted()
            }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredCues).isEmpty()
        assertThat(client.steps).isEmpty()
    }

    @Test
    fun `while locked a list row tap, the same action as a cell, sends nothing`() = runTest {
        preferences.lock.value = true
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(SlideGridAction.OnWidthClassChange(WidthClass.COMPACT))
            viewModel.onAction(SlideGridAction.OnViewModeChange(ViewMode.LIST))
            viewModel.onAction(SlideGridAction.OnCueClick(2))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredCues).isEmpty()
        assertLockedPosted()
    }

    @Test
    fun `while locked a library presentation's cue tap, next and previous send nothing`() = runTest {
        preferences.lock.value = true
        val viewModel = viewModel(CueSource.Presentation(SONG_C))
        live.value = outside(cue = 3, totalCues = 8)

        viewModel.state.test {
            assertThat(expectMostRecentItem().steps).isEqualTo(relativeSteps)
            listOf(SlideGridAction.OnCueClick(0), SlideGridAction.OnNextClick, SlideGridAction.OnPreviousClick)
                .forEach { action ->
                    viewModel.onAction(action)
                    assertLockedPosted()
                }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredPresentationCues).isEmpty()
        assertThat(client.steps).isEmpty()
    }

    @Test
    fun `unlocked the same calls are sent`() = runTest {
        val viewModel = viewModel()
        live.value = LiveState(ConnectionStatus.CONNECTED, otherItem, LiveSlide(SONG_C, index = 1, totalCues = 5))

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isFalse()
            viewModel.onAction(SlideGridAction.OnCueClick(0))
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
            viewModel.onAction(SlideGridAction.OnResyncClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredCues).containsExactly(item to 0, item to 4)
        assertThat(client.steps).containsExactly("next", "previous")
    }

    @Test
    fun `a library presentation unlocked sends its cue, next and previous`() = runTest {
        val viewModel = viewModel(CueSource.Presentation(SONG_C))
        live.value = outside(cue = 3, totalCues = 8)

        viewModel.state.test {
            assertThat(expectMostRecentItem().steps).isEqualTo(relativeSteps)
            viewModel.onAction(SlideGridAction.OnCueClick(2))
            viewModel.onAction(SlideGridAction.OnNextClick)
            viewModel.onAction(SlideGridAction.OnPreviousClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredPresentationCues).containsExactly(SONG_C to 2)
        assertThat(client.steps).containsExactly("next", "previous")
    }

    @Test
    fun `the grid is locked until the lock is read, then a tap is sent`() = runTest {
        preferences.lock.value = null
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isTrue()
            viewModel.onAction(SlideGridAction.OnCueClick(0))
            assertLockedPosted()
            assertThat(client.triggeredCues).isEmpty()
            preferences.lock.value = false
            assertThat(expectMostRecentItem().locked).isFalse()
            viewModel.onAction(SlideGridAction.OnCueClick(0))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredCues).containsExactly(item to 0)
    }

    @Test
    fun `a lock tap before the lock is read saves nothing`() = runTest {
        preferences.lock.value = null
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(SlideGridAction.OnLockClick)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(preferences.writes).isEmpty()
    }

    @Test
    fun `the grid is locked when the lock cannot be read`() = runTest {
        preferences.failRead = true
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isTrue()
            viewModel.onAction(SlideGridAction.OnCueClick(0))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.triggeredCues).isEmpty()
        assertLockedPosted()
    }

    @Test
    fun `the lock button saves the other state and the grid follows it`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().locked).isFalse()
            viewModel.onAction(SlideGridAction.OnLockClick)
            assertThat(awaitItem().locked).isTrue()
            viewModel.onAction(SlideGridAction.OnLockClick)
            assertThat(awaitItem().locked).isFalse()
        }

        assertThat(preferences.writes).containsExactly(true, false)
    }

    @Test
    fun `a lock that can't be saved shows the setting message`() = runTest {
        preferences.failWrites = true
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.state.test { cancelAndIgnoreRemainingEvents() }
            viewModel.onAction(SlideGridAction.OnLockClick)
            assertThat(awaitItem().messageId()).isEqualTo(DesignR.string.setting_not_saved)
        }
    }

    private val relativeSteps = CueSteps(next = CueStep.Relative, previous = CueStep.Relative)

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
            gridPreferences,
            preferences,
            messages
        )

    private suspend fun assertLockedPosted() {
        messages.messages.test {
            assertThat((awaitItem() as UiText.StringResource).id).isEqualTo(DesignR.string.slide_locked)
        }
    }

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
    }
}
