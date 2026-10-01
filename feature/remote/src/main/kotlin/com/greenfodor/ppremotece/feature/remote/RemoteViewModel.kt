package com.greenfodor.ppremotece.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.remote.BoxMark
import com.greenfodor.ppremotece.core.domain.remote.RemoteBox
import com.greenfodor.ppremotece.core.domain.remote.RemoteCommand
import com.greenfodor.ppremotece.core.domain.remote.RemoteDisplay
import com.greenfodor.ppremotece.core.domain.remote.RemoteInputs
import com.greenfodor.ppremotece.core.domain.remote.RemoteSidebar
import com.greenfodor.ppremotece.core.domain.remote.RemoteStatus
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * The Remote tab: what [RemoteDisplay] shows for the live state, the last live cue, the item cued
 * with ⏮/⏭ and the media item this app triggered, read through the [ContentRepository]. Taps and
 * buttons send the display's commands; ⏮/⏭ and "Back to live" only change the cued item. A cued
 * item and a triggered media item are dropped once another cue is reported live. Each needed
 * presentation is read once and stays available with its latest read; a failed read that leaves
 * the display loading is shown as an error, and retry reads the content again. A tap on an
 * enabled cue of the sidebar sends its item-cue trigger, or its presentation-cue trigger for a
 * presentation played outside a playlist. The current and next boxes ask for
 * thumbnails at their measured widths; the sidebar asks for grid thumbnails.
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

    /** The read of playlist [uuid]; a null [result] is not read yet. */
    private data class PlaylistRead(
        val uuid: String?,
        val result: Result<Playlist, DataError.Network>?
    )

    /** The measured image widths of the current and next boxes in px; 0 until measured. */
    private data class BoxWidths(
        val current: Int = 0,
        val next: Int = 0
    )

    private val cued = MutableStateFlow<Chosen?>(null)
    private val mediaLive = MutableStateFlow<Chosen?>(null)
    private val retries = MutableStateFlow(0)
    private val boxWidths = MutableStateFlow(BoxWidths())

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
                cued = this.cued.current(cued, last),
                mediaLive = mediaLive.current(media, last)
            )
        }.distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val playlist: Flow<PlaylistRead> =
        combine(inputs.map(RemoteDisplay::playlistNeeded).distinctUntilChanged(), retries) { uuid, _ -> uuid }
            .flatMapLatest { uuid ->
                uuid?.let { contentRepository.playlist(it).map { result -> PlaylistRead(uuid, result) } }
                    ?: flowOf(PlaylistRead(null, null))
            }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val presentations: Flow<Map<String, Result<Presentation, DataError.Network>>> =
        retries.flatMapLatest {
            channelFlow {
                val subscribed = mutableSetOf<String>()
                combine(inputs, playlist) { inputs, read ->
                    RemoteDisplay.presentationsNeeded(inputs, read.playlistFor(inputs))
                }.collect { needed ->
                    (needed - subscribed).forEach { uuid ->
                        subscribed += uuid
                        launch { contentRepository.presentation(uuid).collect { send(uuid to it) } }
                    }
                }
            }.scan(emptyMap<String, Result<Presentation, DataError.Network>>()) { read, latest -> read + latest }
        }

    val state: StateFlow<RemoteState> =
        combine(inputs, playlist, presentations, thumbnailSource.thumbnailRequests, boxWidths) {
            inputs,
            playlistRead,
            presentationReads,
            requests,
            widths
            ->
            val playlist = playlistRead.playlistFor(inputs)
            val presentations = buildMap {
                presentationReads.forEach { (uuid, read) -> (read as? Result.Success)?.let { put(uuid, it.data) } }
            }
            val display = RemoteDisplay.reduce(inputs, playlist, presentations)
            val failure = if (display.status == RemoteStatus.LOADING) {
                (playlistRead.result as? Result.Failure)?.error
                    ?: RemoteDisplay.presentationsNeeded(inputs, playlist)
                        .firstNotNullOfOrNull { (presentationReads[it] as? Result.Failure)?.error }
            } else {
                null
            }
            RemoteState(
                display = display,
                currentThumbnail = display.current.thumbnail(requests, widths.current),
                nextThumbnail = display.next.thumbnail(requests, widths.next),
                error = failure?.toUiText(),
                sidebar = display.sidebar?.rows(requests).orEmpty(),
                sidebarFocus = display.sidebar?.focus,
                sidebarSource = display.sidebar?.source
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
            RemoteAction.OnRetryClick -> retries.value++
            is RemoteAction.OnSidebarCueClick -> sendSidebarCue(action.index, display)
            is RemoteAction.OnCurrentBoxSized -> boxWidths.update { it.copy(current = action.px) }
            is RemoteAction.OnNextBoxSized -> boxWidths.update { it.copy(next = action.px) }
        }
    }

    private fun sendSidebarCue(index: Int, display: RemoteDisplay) {
        val sidebar = display.sidebar ?: return
        val cue = sidebar.cues.firstOrNull { it.index == index && it.enabled } ?: return
        send(
            when (val source = sidebar.source) {
                is CueSource.PlaylistItem -> RemoteCommand.TriggerCue(source.key, cue.index)
                is CueSource.Presentation -> RemoteCommand.TriggerPresentationCue(source.uuid, cue.index)
            }
        )
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
                is RemoteCommand.TriggerPresentationCue ->
                    client.triggerPresentationCue(command.presentationUuid, command.cueIndex)
                RemoteCommand.TriggerNext -> client.triggerNext()
                RemoteCommand.TriggerPrevious -> client.triggerPrevious()
            }
            result.onFailure { _events.send(RemoteEvent.ShowError(it.toUiText())) }
        }
    }

    /** The item of [chosen] while [last] is still the cue it was chosen under; otherwise clears it. */
    private fun MutableStateFlow<Chosen?>.current(chosen: Chosen?, last: LiveCue?): PlaylistItemKey? =
        chosen?.takeIf { it.since == last }?.item ?: run {
            chosen?.let { compareAndSet(it, null) }
            null
        }

    /** The playlist read, when it is the one [inputs] needs. */
    private fun PlaylistRead.playlistFor(inputs: RemoteInputs): Playlist? =
        takeIf { it.uuid == RemoteDisplay.playlistNeeded(inputs) }?.let { (it.result as? Result.Success)?.data }

    private fun RemoteSidebar.rows(requests: ThumbnailRequests?): List<SidebarCueUi> =
        cues.map { cue ->
            SidebarCueUi(
                cue = cue,
                mark = marks[cue.index] ?: BoxMark.NONE,
                thumbnail = requests?.takeIf {
                    thumbnails
                }?.request(source, presentationUuid, cue, ThumbnailQuality.Grid)
            )
        }

    private fun RemoteBox.thumbnail(requests: ThumbnailRequests?, px: Int): ThumbnailRequest? {
        val quality = if (px > 0) ThumbnailQuality.Box(px) else ThumbnailQuality.Grid
        return (this as? RemoteBox.Slide)
            ?.takeIf { it.thumbnails }
            ?.let { requests?.request(it.source, it.presentationUuid, it.cue, quality) }
    }
}
