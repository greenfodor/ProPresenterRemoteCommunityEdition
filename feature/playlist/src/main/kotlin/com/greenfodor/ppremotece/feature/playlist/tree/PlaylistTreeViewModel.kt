package com.greenfodor.ppremotece.feature.playlist.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.model.PlaylistFolder
import com.greenfodor.ppremotece.core.domain.model.PlaylistLeaf
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Playlist tree: folders expand in place and a playlist opens its own screen. The tree is read
 * through the [ContentRepository] and shown again whenever it changes; pull-to-refresh reads it
 * again.
 */
class PlaylistTreeViewModel(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistTreeState())
    val state = _state.asStateFlow()

    private val _events = Channel<PlaylistTreeEvent>()
    val events = _events.receiveAsFlow()

    private var tree: List<PlaylistTreeNode> = emptyList()
    private val expanded = mutableSetOf<String>()
    private var treeJob: Job? = null

    init {
        loadTree()
    }

    fun onAction(action: PlaylistTreeAction) {
        when (action) {
            is PlaylistTreeAction.OnFolderClick -> toggle(action.uuid)
            is PlaylistTreeAction.OnPlaylistClick -> viewModelScope.launch {
                _events.send(PlaylistTreeEvent.OpenPlaylist(action.uuid))
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

    private fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = contentRepository.refreshPlaylists()
            _state.update { it.copy(isRefreshing = false) }
            if (result is Result.Failure) _events.send(PlaylistTreeEvent.ShowError(result.error.toUiText()))
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
            when (node) {
                is PlaylistFolder -> {
                    val isExpanded = node.uuid in expanded
                    listOf(TreeRowUi.Folder(node.uuid, depth, node.name, isExpanded)) +
                        if (isExpanded) rowsOf(node.children, depth + 1) else emptyList()
                }
                is PlaylistLeaf -> listOf(TreeRowUi.Playlist(node.uuid, depth, node.name))
            }
        }
}
