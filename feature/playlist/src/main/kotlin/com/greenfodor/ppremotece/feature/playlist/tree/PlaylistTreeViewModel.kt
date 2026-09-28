package com.greenfodor.ppremotece.feature.playlist.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistFolder
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.PlaylistLeaf
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onFailure
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.toArrangementLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_PARALLEL_PRESENTATION_READS = 4

/**
 * Playlist tree: folders and playlists expand in place. Each time a playlist is expanded its items
 * and their presentations are read again, and each item is labelled with its arrangement.
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
    private val labels = mutableMapOf<PlaylistItemKey, ArrangementLabel?>()

    init {
        loadTree()
    }

    fun onAction(action: PlaylistTreeAction) {
        when (action) {
            is PlaylistTreeAction.OnFolderClick -> toggle(action.uuid)
            is PlaylistTreeAction.OnPlaylistClick -> {
                toggle(action.uuid)
                if (action.uuid in expanded) loadPlaylist(action.uuid)
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
                    expanded.filter { it in playlists }.forEach(::loadPlaylist)
                }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.toUiText()) } }
        }
    }

    private fun loadPlaylist(uuid: String) {
        if (!loadingPlaylists.add(uuid)) return
        publish()
        viewModelScope.launch {
            client.playlist(uuid)
                .onSuccess { playlist ->
                    labels.keys.removeAll { it.playlistUuid == uuid }
                    labels += labelsOf(playlist, readPresentations(playlist))
                    playlists[uuid] = playlist
                }
                .onFailure { error ->
                    expanded -= uuid
                    _events.send(PlaylistTreeEvent.ShowError(error.toUiText()))
                }
            loadingPlaylists -= uuid
            publish()
        }
    }

    private suspend fun readPresentations(playlist: Playlist): Map<String, Presentation> {
        val permits = Semaphore(MAX_PARALLEL_PRESENTATION_READS)
        val uuids = playlist.items.mapNotNull { it.presentation?.presentationUuid }.distinct()
        return coroutineScope {
            uuids
                .map { uuid -> async { permits.withPermit { uuid to client.presentation(uuid) } } }
                .awaitAll()
                .mapNotNull { (uuid, result) -> (result as? Result.Success)?.let { uuid to it.data } }
                .toMap()
        }
    }

    private fun labelsOf(
        playlist: Playlist,
        presentations: Map<String, Presentation>
    ): Map<PlaylistItemKey, ArrangementLabel?> =
        playlist.items.mapNotNull { item ->
            val ref = item.presentation ?: return@mapNotNull null
            val presentation = presentations[ref.presentationUuid] ?: return@mapNotNull null
            item.key to ArrangementExpander.expand(presentation, ref).choice.toArrangementLabel()
        }.toMap()

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
                    labels[item.key],
                    opensSlides = item.presentation != null
                )
            }
        }
}
