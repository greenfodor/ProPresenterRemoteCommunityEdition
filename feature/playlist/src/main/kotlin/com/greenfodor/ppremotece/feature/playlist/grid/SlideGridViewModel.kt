package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.arrangement.CueList
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.layout.GridPreferences
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.liveCueIndex
import com.greenfodor.ppremotece.core.domain.live.nextCueIndex
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.map
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import com.greenfodor.ppremotece.core.domain.thumbnail.slideAspect
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.toArrangementLabel
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
 * Slide grid for one playlist item: its cues in the item's arrangement, read through the
 * [ContentRepository], the live and next cues while ProPresenter shows this item, and triggers by
 * item and cue index plus next and previous. Disabled cues are not triggered. Each cue carries its
 * thumbnail request, except in an item whose arrangement did not fully resolve; a successful
 * "Reload slides" also evicts the item's thumbnails and loads them again, as does each new host
 * connection. The slide size step is read and saved for the window's width class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlideGridViewModel(
    private val item: PlaylistItemKey,
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
            val item: PlaylistItem,
            val presentation: Presentation,
            val cueList: CueList
        ) : Content
    }

    private val retries = MutableStateFlow(0)
    private val thumbnailGeneration = MutableStateFlow(0)
    private val widthClass = MutableStateFlow<WidthClass?>(null)
    private val gridStep: Flow<GridStep> =
        widthClass.flatMapLatest { it?.let(gridPreferences::gridStep) ?: flowOf(GridStep.Default) }
    private var loaded: Content.Loaded? = null

    private val content: Flow<Content> =
        retries.flatMapLatest {
            contentRepository
                .playlist(item.playlistUuid)
                .map { result -> result.map { playlist -> playlist.items.firstOrNull { it.key == item } } }
                .distinctUntilChanged()
                .flatMapLatest(::contentOf)
                .onStart { emit(Content.Loading) }
        }.onEach { loaded = it as? Content.Loaded }

    private val grid: Flow<Pair<SlideGridState, Content.Loaded?>> =
        combine(content, thumbnailSource.thumbnailRequests.withIndex(), thumbnailGeneration) {
            content,
            requests,
            reloads
            ->
            when (content) {
                Content.Loading -> SlideGridState(isLoading = true) to null
                is Content.Failed -> SlideGridState(isLoading = false, error = content.error) to null
                is Content.Loaded -> gridState(content, requests.value, reloads + requests.index) to content
            }
        }

    val state: StateFlow<SlideGridState> =
        combine(grid, liveStateRepository.liveState, gridStep) { (state, loaded), live, step ->
            if (loaded == null) {
                state.copy(gridStep = step)
            } else {
                val presentationUuid = loaded.presentation.uuid
                state.copy(
                    liveCueIndex = liveCueIndex(live, item, presentationUuid),
                    nextCueIndex = nextCueIndex(live, item, presentationUuid, loaded.cueList.cues),
                    gridStep = step
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SlideGridState())

    private val _events = Channel<SlideGridEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: SlideGridAction) {
        when (action) {
            is SlideGridAction.OnCueClick -> if (isEnabled(action.index)) send { client.triggerCue(item, action.index) }
            SlideGridAction.OnNextClick -> send { client.triggerNext() }
            SlideGridAction.OnPreviousClick -> send { client.triggerPrevious() }
            SlideGridAction.OnRetryClick -> retries.value++
            SlideGridAction.OnReloadClick -> reload()
            is SlideGridAction.OnWidthClassChange -> widthClass.value = action.widthClass
            is SlideGridAction.OnGridStepChange -> widthClass.value?.let { current ->
                viewModelScope.launch { gridPreferences.setGridStep(current, action.step) }
            }
        }
    }

    private fun gridState(
        content: Content.Loaded,
        requests: ThumbnailRequests?,
        thumbnailGeneration: Int
    ): SlideGridState {
        val presentationUuid = content.presentation.uuid
        val thumbnails = requests.takeUnless { content.cueList.countMismatch }
        return SlideGridState(
            title = content.item.name,
            label = content.cueList.choice.toArrangementLabel(),
            cues = content.cueList.cues.map { cue ->
                CueUi(
                    index = cue.index,
                    groupName = cue.groupName,
                    groupColor = cue.groupColor,
                    text = cue.slideText,
                    label = cue.slideLabel,
                    enabled = cue.enabled,
                    thumbnail = thumbnails?.request(item, presentationUuid, cue)
                )
            },
            aspect = slideAspect(content.presentation),
            countMismatch = content.cueList.countMismatch,
            thumbnailGeneration = thumbnailGeneration,
            isLoading = false
        )
    }

    private fun isEnabled(cueIndex: Int): Boolean =
        loaded?.cueList?.cues?.firstOrNull { it.index == cueIndex }?.enabled == true

    private fun reload() {
        viewModelScope.launch {
            val playlistRead = async { contentRepository.refreshPlaylist(item.playlistUuid) }
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
        thumbnailCache.remove(
            content.cueList.cues.map { requests.request(item, content.presentation.uuid, it).cacheKey }.distinct()
        )
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
                            playlistItem,
                            presentation.data,
                            ArrangementExpander.expand(presentation.data, ref)
                        )
                }
            }
        }
    }

    private fun send(trigger: suspend () -> EmptyResult<DataError.Network>) {
        viewModelScope.launch {
            trigger().onFailure { _events.send(SlideGridEvent.ShowError(it.toUiText())) }
        }
    }
}
