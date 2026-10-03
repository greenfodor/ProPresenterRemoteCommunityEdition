package com.greenfodor.ppremotece.feature.playlist.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.toArrangementLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The items of the playlist [playlistUuid]: headers with their colour, and items with their type
 * and arrangement label. The playlist and the presentations of its items are read through the
 * [ContentRepository] and shown again whenever they change; pull-to-refresh reads them all again.
 * A presentation item opens its slides, a media, audio or live-video item its item screen, and a
 * header, placeholder or unknown item nothing.
 */
class PlaylistViewModel(
    private val playlistUuid: String,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistState())
    val state = _state.asStateFlow()

    private val _events = Channel<PlaylistEvent>()
    val events = _events.receiveAsFlow()

    private var playlist: Playlist? = null
    private val presentations = mutableMapOf<String, Presentation>()
    private var playlistJob: Job? = null
    private val presentationJobs = mutableMapOf<String, Job>()

    init {
        load()
    }

    fun onAction(action: PlaylistAction) {
        when (action) {
            is PlaylistAction.OnItemClick -> open(action.key)
            PlaylistAction.OnRetryClick -> load()
            PlaylistAction.OnRefresh -> refresh()
        }
    }

    private fun load() {
        playlistJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        playlistJob = viewModelScope.launch {
            contentRepository.playlist(playlistUuid).collect { result ->
                when (result) {
                    is Result.Success -> {
                        playlist = result.data
                        result.data.presentationUuids().forEach(::observePresentation)
                        publish()
                    }
                    is Result.Failure -> if (playlist == null) {
                        _state.update { it.copy(isLoading = false, error = result.error.toUiText()) }
                    }
                }
            }
        }
    }

    private fun observePresentation(uuid: String) {
        if (uuid in presentationJobs) return
        presentationJobs[uuid] = viewModelScope.launch {
            contentRepository.presentation(uuid).collect { result ->
                if (result is Result.Success) {
                    presentations[uuid] = result.data
                    publish()
                }
            }
        }
    }

    private fun open(key: PlaylistItemKey) {
        val row = _state.value.rows.filterIsInstance<PlaylistRowUi.Item>().firstOrNull { it.key == key } ?: return
        val event = when (row.opens) {
            RowTarget.SLIDES -> PlaylistEvent.OpenSlides(key)
            RowTarget.ITEM -> PlaylistEvent.OpenItem(key)
            RowTarget.NONE -> return
        }
        viewModelScope.launch { _events.send(event) }
    }

    private fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val reads = listOf(async { contentRepository.refreshPlaylist(playlistUuid) }) +
                playlist?.presentationUuids().orEmpty().map { async { contentRepository.refreshPresentation(it) } }
            val error = reads.awaitAll().firstNotNullOfOrNull { (it as? Result.Failure)?.error }
            _state.update { it.copy(isRefreshing = false) }
            if (error != null) _events.send(PlaylistEvent.ShowError(error.toUiText()))
        }
    }

    private fun publish() {
        val current = playlist ?: return
        _state.update {
            it.copy(name = current.name, rows = current.items.map(::rowOf), isLoading = false, error = null)
        }
    }

    private fun rowOf(item: PlaylistItem): PlaylistRowUi {
        val id = "${item.key.playlistUuid}/${item.key.index}"
        return if (item.type == PlaylistItemType.HEADER) {
            PlaylistRowUi.Header(id, item.name, item.headerColor)
        } else {
            PlaylistRowUi.Item(id, item.name, item.key, item.type, labelOf(item), targetOf(item))
        }
    }

    private fun targetOf(item: PlaylistItem): RowTarget =
        when {
            item.presentation != null -> RowTarget.SLIDES
            item.type in ItemScreenTypes -> RowTarget.ITEM
            else -> RowTarget.NONE
        }

    private fun labelOf(item: PlaylistItem): ArrangementLabel? =
        item.presentation?.let { ref ->
            presentations[ref.presentationUuid]?.let { ArrangementExpander.expand(it, ref).choice.toArrangementLabel() }
        }

    private companion object {
        val ItemScreenTypes = setOf(PlaylistItemType.MEDIA, PlaylistItemType.AUDIO, PlaylistItemType.LIVE_VIDEO)
    }
}

private fun Playlist.presentationUuids(): List<String> = items.mapNotNull {
    it.presentation?.presentationUuid
}.distinct()
