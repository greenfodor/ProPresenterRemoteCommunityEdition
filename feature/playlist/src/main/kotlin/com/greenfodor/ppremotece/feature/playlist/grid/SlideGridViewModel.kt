package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.toArrangementLabel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Slide grid for one playlist item: its cues in the item's arrangement, the live cue while
 * ProPresenter shows this item, and triggers by item and cue index plus next and previous.
 */
class SlideGridViewModel(
    private val item: PlaylistItemKey,
    private val client: ProPresenterClient,
    liveStateRepository: LiveStateRepository
) : ViewModel() {
    private val content = MutableStateFlow(SlideGridState())

    val state: StateFlow<SlideGridState> =
        combine(content, liveStateRepository.liveState) { state, live ->
            state.copy(liveCueIndex = liveCueIndex(live, item, state.presentationUuid))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SlideGridState())

    private val _events = Channel<SlideGridEvent>()
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: SlideGridAction) {
        when (action) {
            is SlideGridAction.OnCueClick -> send { client.triggerCue(item, action.index) }
            SlideGridAction.OnNextClick -> send { client.triggerNext() }
            SlideGridAction.OnPreviousClick -> send { client.triggerPrevious() }
            SlideGridAction.OnRetryClick -> load()
        }
    }

    private fun send(trigger: suspend () -> EmptyResult<DataError.Network>) {
        viewModelScope.launch {
            trigger().onFailure { _events.send(SlideGridEvent.ShowError(it.toUiText())) }
        }
    }

    private fun load() {
        content.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val playlist = when (val result = client.playlist(item.playlistUuid)) {
                is Result.Success -> result.data
                is Result.Failure -> return@launch fail(result.error.toUiText())
            }
            val playlistItem = playlist.items.firstOrNull { it.key == item }
            val ref =
                playlistItem?.presentation
                    ?: return@launch fail(UiText.StringResource(R.string.grid_item_has_no_slides))
            when (val result = client.presentation(ref.presentationUuid)) {
                is Result.Failure -> fail(result.error.toUiText())
                is Result.Success -> {
                    val cueList = ArrangementExpander.expand(result.data, ref)
                    content.update {
                        it.copy(
                            title = playlistItem.name,
                            label = cueList.choice.toArrangementLabel(),
                            presentationUuid = ref.presentationUuid,
                            cues = cueList.cues.map { cue ->
                                CueUi(cue.index, cue.groupName, cue.groupColor, cue.slideText)
                            },
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    private fun fail(error: UiText) {
        content.update { it.copy(isLoading = false, error = error) }
    }
}
