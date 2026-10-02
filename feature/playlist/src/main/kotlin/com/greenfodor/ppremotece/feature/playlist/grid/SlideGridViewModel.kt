package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementBanner
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.arrangement.CueList
import com.greenfodor.ppremotece.core.domain.arrangement.ResyncTarget
import com.greenfodor.ppremotece.core.domain.arrangement.currentCueList
import com.greenfodor.ppremotece.core.domain.arrangement.groupSequence
import com.greenfodor.ppremotece.core.domain.arrangement.resyncTarget
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.layout.GridPreferences
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.map
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailKey
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import com.greenfodor.ppremotece.feature.playlist.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Slide grid for one [source]: a playlist item's cues in the item's arrangement, or a library
 * presentation's cues in its current arrangement, read through the [ContentRepository], with the
 * live and next cues while ProPresenter shows that source. A playlist item triggers by item and
 * cue index; a presentation triggers by presentation and cue index. Next and previous send
 * `trigger/next|previous`, always for a playlist item and for a presentation only while it is live
 * outside a playlist. Disabled cues are not triggered. Each cue carries its thumbnail request,
 * except in List mode or when the arrangement did not fully resolve; a successful "Reload slides"
 * also evicts the thumbnails and loads them again, as does each new host connection. The slide
 * size step and the view mode are read for the window's width class; a step being dragged is shown
 * at once and saved when the drag ends, and a chosen view mode is saved for the width class.
 * [firstVisibleCue] keeps the first cue shown across view mode switches. A playlist item's grid
 * shows the [ArrangementBanner] while its presentation is live in another arrangement, and Re-sync
 * triggers this item's cue at the live slide ([resyncTarget]), and is disabled without a match. A
 * setting that can't be saved shows a message. The group strip lists the
 * [groupSequence], and a pill tap scrolls to the occurrence's first cue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlideGridViewModel(
    private val source: CueSource,
    private val contentRepository: ContentRepository,
    private val client: ProPresenterClient,
    liveStateRepository: LiveStateRepository,
    private val thumbnailSource: ThumbnailSource,
    private val thumbnailCache: ThumbnailCache,
    private val gridPreferences: GridPreferences
) : ViewModel() {
    private sealed interface Content {
        data object Loading : Content

        data class Failed(
            val error: UiText
        ) : Content

        data class Loaded(
            val title: String,
            val presentation: Presentation,
            val cueList: CueList
        ) : Content
    }

    private val alwaysSteps = source is CueSource.PlaylistItem
    private val retries = MutableStateFlow(0)
    private val thumbnailGeneration = MutableStateFlow(0)
    private val widthClass = MutableStateFlow<WidthClass?>(null)
    private val draggedStep = MutableStateFlow<GridStep?>(null)
    private val gridStep: Flow<GridStep?> =
        combine(widthClass.flatMapLatest { it?.let(gridPreferences::gridStep) ?: flowOf(null) }, draggedStep) {
            saved,
            dragged
            ->
            dragged ?: saved
        }
    private val viewMode: Flow<ViewMode?> =
        widthClass.flatMapLatest { it?.let(gridPreferences::viewMode) ?: flowOf(null) }
    private var loaded: Content.Loaded? = null
    private val live = liveStateRepository.liveState

    /** Another live playlist item of this grid's playlist item and its presentation ref, null until read. */
    private val liveItemRef: Flow<Pair<PlaylistItemKey, PresentationRef?>?> =
        if (source is CueSource.PlaylistItem) {
            val liveItem = live.map { state -> state.item?.takeIf { it != source.key } }.distinctUntilChanged()
            val livePlaylist = liveItem.map { it?.playlistUuid }.distinctUntilChanged().flatMapLatest { uuid ->
                if (uuid == null) {
                    flowOf(null)
                } else {
                    contentRepository.playlist(uuid).map { (it as? Result.Success)?.data }.onStart { emit(null) }
                }
            }
            combine(liveItem, livePlaylist) { key, playlist ->
                key?.let { it to playlist?.items?.firstOrNull { item -> item.key == it }?.presentation }
            }
        } else {
            flowOf(null)
        }

    private val content: Flow<Content> =
        retries.flatMapLatest {
            when (source) {
                is CueSource.PlaylistItem ->
                    contentRepository
                        .playlist(source.key.playlistUuid)
                        .map { result ->
                            result.map { playlist -> playlist.items.firstOrNull { it.key == source.key } }
                        }
                        .distinctUntilChanged()
                        .flatMapLatest(::contentOf)
                is CueSource.Presentation -> contentRepository.presentation(source.uuid).map(::presentationContentOf)
            }.onStart { emit(Content.Loading) }
        }.onEach { loaded = it as? Content.Loaded }

    private val grid: Flow<Pair<SlideGridState, Content.Loaded?>> =
        combine(content, thumbnailSource.thumbnailRequests.withIndex(), thumbnailGeneration, viewMode) {
            content,
            requests,
            reloads,
            mode
            ->
            when (content) {
                Content.Loading -> SlideGridState(stepsEnabled = alwaysSteps, isLoading = true) to null
                is Content.Failed ->
                    SlideGridState(stepsEnabled = alwaysSteps, isLoading = false, error = content.error) to null
                is Content.Loaded -> gridState(
                    source,
                    content.title,
                    content.presentation,
                    content.cueList,
                    requests.value.takeIf { mode != ViewMode.LIST },
                    reloads + requests.index
                ) to content
            }.let { (state, loaded) -> state.copy(viewMode = mode) to loaded }
        }

    val state: StateFlow<SlideGridState> =
        combine(grid, live, gridStep, liveItemRef) { (state, loaded), live, step, liveItem ->
            val liveItemRef = liveItem?.takeIf { it.first == live.item }?.second
            if (loaded == null) {
                state.copy(gridStep = step)
            } else {
                val withLive = state.withLive(source, loaded.presentation, loaded.cueList, live, liveItemRef)
                withLive.copy(stepsEnabled = alwaysSteps || withLive.liveCueIndex != null, gridStep = step)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            SlideGridState(stepsEnabled = alwaysSteps)
        )

    /** The index of the first cue shown, as last reported by [SlideGridAction.OnFirstVisibleCueChange]. */
    var firstVisibleCue: Int = 0
        private set

    private val _events = Channel<SlideGridEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: SlideGridAction) {
        when (action) {
            is SlideGridAction.OnCueClick ->
                if (loaded?.cueList.isEnabled(action.index)) send { client.trigger(source, action.index) }
            SlideGridAction.OnNextClick -> step { client.triggerNext() }
            SlideGridAction.OnPreviousClick -> step { client.triggerPrevious() }
            SlideGridAction.OnResyncClick -> resync()
            is SlideGridAction.OnGroupPillClick -> viewModelScope.launch {
                _events.send(SlideGridEvent.ScrollToCue(action.firstCueIndex))
            }
            is SlideGridAction.OnFirstVisibleCueChange -> firstVisibleCue = action.cueIndex
            SlideGridAction.OnRetryClick -> retries.value++
            SlideGridAction.OnReloadClick -> reload()
            is SlideGridAction.Layout -> onLayoutAction(action)
        }
    }

    private fun onLayoutAction(action: SlideGridAction.Layout) {
        when (action) {
            is SlideGridAction.OnWidthClassChange -> {
                draggedStep.value = null
                widthClass.value = action.widthClass
            }
            is SlideGridAction.OnGridStepChange -> draggedStep.value = action.step
            SlideGridAction.OnGridStepChangeFinished -> saveDraggedStep()
            is SlideGridAction.OnViewModeChange -> widthClass.value?.let { current ->
                viewModelScope.launch {
                    gridPreferences.setViewMode(current, action.mode).onFailure { showWriteError(it) }
                }
            }
        }
    }

    private fun resync() {
        val item = (source as? CueSource.PlaylistItem)?.key ?: return
        val cue = (state.value.resync as? ResyncTarget.Cue)?.index ?: return
        send { client.triggerCue(item, cue) }
    }

    private fun saveDraggedStep() {
        val step = draggedStep.value ?: return
        val current = widthClass.value ?: return
        viewModelScope.launch {
            gridPreferences.setGridStep(current, step).onFailure { showWriteError(it) }
            draggedStep.compareAndSet(step, null)
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val playlistRead = (source as? CueSource.PlaylistItem)?.let {
                async { contentRepository.refreshPlaylist(it.key.playlistUuid) }
            }
            val presentationRead = loaded?.presentation?.uuid?.let {
                async { contentRepository.refreshPresentation(it) }
            }
            val error = listOfNotNull(playlistRead, presentationRead)
                .awaitAll()
                .firstNotNullOfOrNull { (it as? Result.Failure)?.error }
            if (error != null) {
                _events.send(SlideGridEvent.ShowError(error.toUiText()))
            } else {
                evictThumbnails()
            }
        }
    }

    private suspend fun evictThumbnails() {
        val content = loaded?.takeUnless { it.cueList.countMismatch } ?: return
        val requests = thumbnailSource.thumbnailRequests.first() ?: return
        val gridKeys = content.cueList.cues
            .map { requests.request(source, content.presentation.uuid, it, ThumbnailQuality.Grid).cacheKey }
            .distinct()
        thumbnailCache.remove(gridKeys + gridKeys.flatMap { ThumbnailKey.boxKeys(it) })
        thumbnailGeneration.value++
    }

    private fun contentOf(result: Result<PlaylistItem?, DataError.Network>): Flow<Content> {
        val playlistItem = (result as? Result.Success)?.data
        val ref = playlistItem?.presentation
        return when {
            result is Result.Failure -> flowOf(Content.Failed(result.error.toUiText()))
            playlistItem == null || ref == null ->
                flowOf(Content.Failed(UiText.StringResource(R.string.grid_item_has_no_slides)))
            else -> contentRepository.presentation(ref.presentationUuid).map { presentation ->
                when (presentation) {
                    is Result.Failure -> Content.Failed(presentation.error.toUiText())
                    is Result.Success ->
                        Content.Loaded(
                            playlistItem.name,
                            presentation.data,
                            ArrangementExpander.expand(presentation.data, ref)
                        )
                }
            }
        }
    }

    private fun presentationContentOf(result: Result<Presentation, DataError.Network>): Content =
        when (result) {
            is Result.Failure -> Content.Failed(result.error.toUiText())
            is Result.Success -> Content.Loaded(result.data.name, result.data, currentCueList(result.data))
        }

    private suspend fun showWriteError(error: DataError.Local) {
        _events.send(SlideGridEvent.ShowError(error.toUiText()))
    }

    private fun step(trigger: suspend () -> EmptyResult<DataError.Network>) {
        if (state.value.stepsEnabled) send(trigger)
    }

    private fun send(trigger: suspend () -> EmptyResult<DataError.Network>) {
        viewModelScope.launch {
            trigger().onFailure { _events.send(SlideGridEvent.ShowError(it.toUiText())) }
        }
    }
}
