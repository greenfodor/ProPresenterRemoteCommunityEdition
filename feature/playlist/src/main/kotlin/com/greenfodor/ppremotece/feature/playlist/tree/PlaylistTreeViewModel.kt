package com.greenfodor.ppremotece.feature.playlist.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistFolder
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.PlaylistLeaf
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.toArrangementLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Playlist tree: folders and playlists expand in place. The tree, each expanded playlist and the
 * presentations of its items are read through the [ContentRepository] and shown again whenever
 * they change; each item is labelled with its arrangement. Pull-to-refresh reads them all again.
 */
class PlaylistTreeViewModel(
    private val contentRepository: ContentRepository,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistTreeState())
    val state = _state.asStateFlow()

    private val _events = Channel<PlaylistTreeEvent>()
    val events = _events.receiveAsFlow()

    private var tree: List<PlaylistTreeNode> = emptyList()
    private val expanded = mutableSetOf<String>()
    private val playlists = mutableMapOf<String, Playlist>()
    private val presentations = mutableMapOf<String, Presentation>()
    private val loadingPlaylists = mutableSetOf<String>()
    private var treeJob: Job? = null
    private val playlistJobs = mutableMapOf<String, Job>()
    private val presentationJobs = mutableMapOf<String, Job>()

    init {
        loadTree()
    }

    fun onAction(action: PlaylistTreeAction) {
        when (action) {
            is PlaylistTreeAction.OnFolderClick -> toggle(action.uuid)
            is PlaylistTreeAction.OnPlaylistClick -> {
                toggle(action.uuid)
                if (action.uuid in expanded) observePlaylist(action.uuid) else stopPlaylist(action.uuid)
            }
            is PlaylistTreeAction.OnItemClick -> viewModelScope.launch {
                _events.send(PlaylistTreeEvent.OpenItem(action.key))
            }
            PlaylistTreeAction.OnDisconnectClick -> viewModelScope.launch {
                connectionRepository.disconnect()
                _events.send(PlaylistTreeEvent.Disconnected)
            }
            PlaylistTreeAction.OnRetryClick -> loadTree()
            PlaylistTreeAction.OnRefresh -> refresh()
        }
    }

    private fun loadTree() {
        treeJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        treeJob = viewModelScope.launch {
            contentRepository.playlists().collect { result ->
                when (result) {
                    is Result.Success -> {
                        tree = result.data
                        _state.update { it.copy(isLoading = false, error = null) }
                        publish()
                    }
                    is Result.Failure -> _state.update { it.copy(isLoading = false, error = result.error.toUiText()) }
                }
            }
        }
    }

    private fun observePlaylist(uuid: String) {
        if (uuid in playlistJobs) return
        loadingPlaylists += uuid
        publish()
        playlistJobs[uuid] = viewModelScope.launch {
            val failure = contentRepository
                .playlist(uuid)
                .onEach { result ->
                    if (result is Result.Success) {
                        loadingPlaylists -= uuid
                        playlists[uuid] = result.data
                        result.data.presentationUuids().forEach(::observePresentation)
                        publish()
                    }
                }.filterIsInstance<Result.Failure<DataError.Network>>()
                .first()
            playlistJobs -= uuid
            loadingPlaylists -= uuid
            expanded -= uuid
            publish()
            _events.send(PlaylistTreeEvent.ShowError(failure.error.toUiText()))
        }
    }

    private fun stopPlaylist(uuid: String) {
        playlistJobs.remove(uuid)?.cancel()
        loadingPlaylists -= uuid
        val shown = expanded.mapNotNull { playlists[it] }.flatMap { it.presentationUuids() }.toSet()
        presentationJobs.keys.filterNot { it in shown }.forEach { presentationJobs.remove(it)?.cancel() }
        publish()
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

    private fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val shownPlaylists = expanded.mapNotNull { playlists[it] }
            val reads = listOf(async { contentRepository.refreshPlaylists() }) +
                shownPlaylists.map { async { contentRepository.refreshPlaylist(it.uuid) } } +
                shownPlaylists.flatMap { it.presentationUuids() }.distinct().map {
                    async { contentRepository.refreshPresentation(it) }
                }
            val error = reads.awaitAll().firstNotNullOfOrNull { (it as? Result.Failure)?.error }
            _state.update { it.copy(isRefreshing = false) }
            if (error != null) _events.send(PlaylistTreeEvent.ShowError(error.toUiText()))
        }
    }

    private fun toggle(uuid: String) {
        if (!expanded.remove(uuid)) expanded += uuid
        publish()
    }

    private fun publish() {
        _state.update { it.copy(rows = rowsOf(tree, depth = 0)) }
    }

    private fun rowsOf(nodes: List<PlaylistTreeNode>, depth: Int): List<TreeRowUi> =
        nodes.flatMap { node ->
            val isExpanded = node.uuid in expanded
            when (node) {
                is PlaylistFolder -> listOf(TreeRowUi.Folder(node.uuid, depth, node.name, isExpanded)) +
                    if (isExpanded) rowsOf(node.children, depth + 1) else emptyList()
                is PlaylistLeaf -> listOf(
                    TreeRowUi.Playlist(
                        node.uuid,
                        depth,
                        node.name,
                        isExpanded,
                        isLoading =
                            node.uuid in loadingPlaylists
                    )
                ) + if (isExpanded) itemRowsOf(playlists[node.uuid], depth + 1) else emptyList()
            }
        }

    private fun itemRowsOf(playlist: Playlist?, depth: Int): List<TreeRowUi> =
        playlist?.items.orEmpty().map { item ->
            val id = "${item.key.playlistUuid}/${item.key.index}"
            if (item.type == PlaylistItemType.HEADER) {
                TreeRowUi.Header(id, depth, item.name)
            } else {
                TreeRowUi.Item(
                    id,
                    depth,
                    item.name,
                    item.key,
                    labelOf(item),
                    opensSlides = item.presentation != null
                )
            }
        }

    private fun labelOf(item: PlaylistItem): ArrangementLabel? =
        item.presentation?.let { ref ->
            presentations[ref.presentationUuid]?.let { ArrangementExpander.expand(it, ref).choice.toArrangementLabel() }
        }
}

private fun Playlist.presentationUuids(): List<String> = items.mapNotNull {
    it.presentation?.presentationUuid
}.distinct()
