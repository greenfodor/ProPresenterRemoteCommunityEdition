package com.greenfodor.ppremotece.feature.playlist.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.orNull
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.transport.TransportRepository
import com.greenfodor.ppremotece.core.domain.transport.formatDuration
import com.greenfodor.ppremotece.core.domain.transport.itemLive
import com.greenfodor.ppremotece.core.domain.trigger.InFlightTriggers
import com.greenfodor.ppremotece.feature.playlist.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * The card of the playlist item [key]: its name, type and duration, read through the
 * [ContentRepository] and kept while a later read fails, and whether a transport of the
 * [TransportRepository] plays it or holds it paused ([itemLive]); an unavailable transport marks
 * nothing. An item that is not a media, audio or live-video
 * item is shown as no longer in the playlist.
 * A tap triggers the item; taps are ignored while its request is in flight, and a failure posts
 * "Couldn't start {item}".
 */
class PlaylistItemViewModel(
    private val key: PlaylistItemKey,
    private val contentRepository: ContentRepository,
    transportRepository: TransportRepository,
    private val client: ProPresenterClient,
    private val messages: UiMessages
) : ViewModel() {
    private val triggers = InFlightTriggers()
    private val reads = MutableStateFlow(0)

    /** The item as last read, or why it could not be read; neither while it is being read. */
    private data class Read(
        val item: PlaylistItem? = null,
        val error: UiText? = null
    )

    @Volatile
    private var lastRead = Read()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val read = reads.flatMapLatest {
        contentRepository.playlist(key.playlistUuid).map { result ->
            val last = lastRead
            when (result) {
                is Result.Success ->
                    result.data.items.firstOrNull {
                        it.key == key && it.type in ItemScreenTypes
                    }?.let { Read(item = it) }
                        ?: Read(error = UiText.StringResource(R.string.playlist_item_not_found))
                is Result.Failure -> if (last.item != null) last else Read(error = result.error.toUiText())
            }.also { lastRead = it }
        }
    }

    val state: StateFlow<PlaylistItemState> =
        combine(
            read,
            transportRepository.presentationTransport,
            transportRepository.audioTransport
        ) { read, presentation, audio ->
            val item = read.item
            if (item == null) {
                PlaylistItemState(isLoading = read.error == null, error = read.error)
            } else {
                PlaylistItemState(
                    name = item.name,
                    type = item.type,
                    duration = item.durationSeconds?.let(::formatDuration),
                    live = itemLive(item, presentation.orNull(), audio.orNull()),
                    isLoading = false
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlaylistItemState())

    fun onAction(action: PlaylistItemAction) {
        when (action) {
            PlaylistItemAction.OnCardClick -> trigger()
            PlaylistItemAction.OnRetryClick -> reads.update { it + 1 }
        }
    }

    private fun trigger() {
        val shown = state.value
        if (shown.type == null) return
        viewModelScope.launch {
            triggers.run(TRIGGER_KEY) {
                client.triggerItem(key).onFailure {
                    messages.post(UiText.StringResource(R.string.playlist_item_error_start, listOf(shown.name)))
                }
            }
        }
    }

    private companion object {
        const val TRIGGER_KEY = "item"
    }
}
