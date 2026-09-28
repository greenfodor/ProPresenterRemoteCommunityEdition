package com.greenfodor.ppremotece.feature.playlist.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistFolder
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.PlaylistLeaf
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import com.greenfodor.ppremotece.feature.playlist.toArrangementLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Playlist tree: folders and playlists expand in place. Expanding a playlist loads its items and
 * the presentations they play, so each item row can show the arrangement chosen for that item.
 */
class PlaylistTreeViewModel(
    private val client: ProPresenterClient,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistTreeState())
    val state = _state.asStateFlow()

    private val _events = Channel<PlaylistTreeEvent>()
    val events = _events.receiveAsFlow()

    private var tree: List<PlaylistTreeNode> = emptyList()
    private val expanded = mutableSetOf<String>()
    private val playlists = mutableMapOf<String, Playlist>()
    private val loadingPlaylists = mutableSetOf<String>()
    private val presentations = mutableMapOf<String, Presentation>()

    init {
        loadTree()
    }

    fun onAction(action: PlaylistTreeAction) {
        when (action) {
            is PlaylistTreeAction.OnFolderClick -> toggle(action.uuid)
            is PlaylistTreeAction.OnPlaylistClick -> {
                toggle(action.uuid)
                if (action.uuid in expanded && action.uuid !in playlists) loadPlaylist(action.uuid)
            }
            is PlaylistTreeAction.OnItemClick -> viewModelScope.launch {
                _events.send(PlaylistTreeEvent.OpenItem(action.key))
            }
            PlaylistTreeAction.OnDisconnectClick -> viewModelScope.launch {
                connectionRepository.disconnect()
                _events.send(PlaylistTreeEvent.Disconnected)
            }
            PlaylistTreeAction.OnRetryClick -> loadTree()
        }
    }

    private fun loadTree() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            client.playlists()
                .onSuccess { nodes ->
                    tree = nodes
                    _state.update { it.copy(isLoading = false) }
                    publish()
                }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.toUiText()) } }
        }
    }

    private fun loadPlaylist(uuid: String) {
        loadingPlaylists += uuid
        publish()
        viewModelScope.launch {
            client.playlist(uuid)
                .onSuccess { playlist ->
                    playlists[uuid] = playlist
                    loadPresentations(playlist)
                }
                .onFailure { error ->
                    expanded -= uuid
                    _events.send(PlaylistTreeEvent.ShowError(error.toUiText()))
                }
            loadingPlaylists -= uuid
            publish()
        }
    }

    private suspend fun loadPresentations(playlist: Playlist) {
        val missing = playlist.items.mapNotNull { it.presentation?.presentationUuid }.distinct() - presentations.keys
        missing
            .map { uuid -> viewModelScope.async { uuid to client.presentation(uuid) } }
            .awaitAll()
            .forEach { (uuid, result) -> if (result is Result.Success) presentations[uuid] = result.data }
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
                val ref = item.presentation
                val label = ref?.let { presentations[it.presentationUuid] }
                    ?.let { ArrangementExpander.expand(it, ref).choice.toArrangementLabel() }
                TreeRowUi.Item(id, depth, item.name, item.key, label, opensSlides = ref != null)
            }
        }
}
