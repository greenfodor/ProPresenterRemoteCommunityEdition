package com.greenfodor.ppremotece.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.remote.RemoteBox
import com.greenfodor.ppremotece.core.domain.remote.RemoteCommand
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.domain.remote.RemoteInputs
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * The Remote tab: what [RemoteDisplay] shows for the live state, the last live cue, the item cued
 * with ⏮/⏭ and the media item this app triggered, read through the [ContentRepository]. Taps and
 * buttons send the display's commands; ⏮/⏭ and "Back to live" only change the cued item. A cued
 * item and a triggered media item are dropped once another cue is reported live. Presentations
 * read once stay available to the display, each with its latest read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModel(
    private val client: ProPresenterClient,
    private val liveStateRepository: LiveStateRepository,
    private val contentRepository: ContentRepository,
    thumbnailSource: ThumbnailSource
) : ViewModel() {
    /** An item chosen while [since] was the last live cue. */
    private data class Chosen(
        val item: PlaylistItemKey,
        val since: LiveCue?
    )

    private val cued = MutableStateFlow<Chosen?>(null)
    private val mediaLive = MutableStateFlow<Chosen?>(null)

    private val inputs: Flow<RemoteInputs> =
        combine(liveStateRepository.liveState, liveStateRepository.lastLive, cued, mediaLive) {
            live,
            last,
            cued,
            media
            ->
            RemoteInputs(
                live = live,
                lastLive = last,
                cued = cued?.takeIf { it.since == last }?.item,
                mediaLive = media?.takeIf { it.since == last }?.item
            )
        }.distinctUntilChanged()

    private val playlist: Flow<Playlist?> =
        inputs
            .map(RemoteDisplay::playlistNeeded)
            .distinctUntilChanged()
            .flatMapLatest { uuid ->
                uuid?.let { contentRepository.playlist(it).map { result -> (result as? Result.Success)?.data } }
                    ?: flowOf(null)
            }

    private val presentations: Flow<Map<String, Presentation>> =
        combine(inputs, playlist, RemoteDisplay::presentationsNeeded)
            .distinctUntilChanged()
            .flatMapLatest(::presentationsOf)
            .scan(emptyMap<String, Presentation>()) { read, latest -> read + latest }

    val state: StateFlow<RemoteState> =
        combine(inputs, playlist, presentations, thumbnailSource.thumbnailRequests) {
            inputs,
            playlist,
            presentations,
            requests
            ->
            val display = RemoteDisplay.reduce(inputs, playlist, presentations)
            RemoteState(
                display = display,
                currentThumbnail = display.current.thumbnail(requests),
                nextThumbnail = display.next.thumbnail(requests)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RemoteState())

    private val _events = Channel<RemoteEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: RemoteAction) {
        val display = state.value.display
        when (action) {
            RemoteAction.OnCurrentClick -> send(display.tapCurrent)
            RemoteAction.OnNextBoxClick -> send(display.tapNext)
            RemoteAction.OnNextClick -> send(display.nextButton)
            RemoteAction.OnPreviousClick -> send(display.previousButton)
            RemoteAction.OnPreviousItemClick -> display.previousItem?.let { cue(it, display) }
            RemoteAction.OnNextItemClick -> display.nextItem?.let { cue(it, display) }
            RemoteAction.OnBackToLiveClick -> cued.value = null
        }
    }

    private fun cue(item: PlaylistItemKey, display: RemoteDisplay) {
        cued.value = if (item == display.baseItem) null else Chosen(item, liveStateRepository.lastLive.value)
    }

    private fun send(command: RemoteCommand?) {
        command ?: return
        viewModelScope.launch {
            val result = when (command) {
                is RemoteCommand.TriggerCue -> client.triggerCue(command.item, command.cueIndex)
                is RemoteCommand.TriggerItem -> client.triggerItem(command.item).also {
                    if (it is Result.Success) mediaLive.value = Chosen(command.item, liveStateRepository.lastLive.value)
                }
                RemoteCommand.TriggerNext -> client.triggerNext()
                RemoteCommand.TriggerPrevious -> client.triggerPrevious()
            }
            result.onFailure { _events.send(RemoteEvent.ShowError(it.toUiText())) }
        }
    }

    private fun presentationsOf(uuids: Set<String>): Flow<Map<String, Presentation>> =
        if (uuids.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                uuids.map { uuid ->
                    contentRepository.presentation(uuid).map { uuid to (it as? Result.Success)?.data }
                }
            ) { pairs -> pairs.mapNotNull { (uuid, presentation) -> presentation?.let { uuid to it } }.toMap() }
        }

    private fun RemoteBox.thumbnail(requests: ThumbnailRequests?): ThumbnailRequest? =
        (this as? RemoteBox.Slide)
            ?.takeIf { it.thumbnails }
            ?.let { requests?.request(it.item, it.presentationUuid, it.cue) }
}
