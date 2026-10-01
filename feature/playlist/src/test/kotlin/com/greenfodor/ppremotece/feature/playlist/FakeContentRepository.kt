package com.greenfodor.ppremotece.feature.playlist

import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.asEmptyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart

/**
 * Serves [tree], [playlists], [presentations], [libraries] and [libraryEntries] as the host's
 * current content; each open or refresh reads them.
 */
class FakeContentRepository : ContentRepository {
    var tree: List<PlaylistTreeNode> = emptyList()
    var libraries: List<Library> = emptyList()
    val libraryEntries = mutableMapOf<String, List<LibraryEntry>>()

    /** Libraries whose reads never answer. */
    val pendingLibraries = mutableSetOf<String>()
    val playlists = mutableMapOf<String, Playlist>()
    val presentations = mutableMapOf<String, Presentation>()
    var failWith: DataError.Network? = null
    val refreshed = mutableListOf<String>()

    private val cache = mutableMapOf<String, MutableStateFlow<Result<Any, DataError.Network>?>>()

    override fun playlists(): Flow<Result<List<PlaylistTreeNode>, DataError.Network>> = observe(TREE) { tree }

    override fun playlist(uuid: String): Flow<Result<Playlist, DataError.Network>> = observe(uuid) { playlists[uuid] }

    override fun presentation(uuid: String): Flow<Result<Presentation, DataError.Network>> =
        observe(uuid) { presentations[uuid] }

    override fun libraries(): Flow<Result<List<Library>, DataError.Network>> = observe(LIBRARIES) { libraries }

    override fun library(uuid: String): Flow<Result<List<LibraryEntry>, DataError.Network>> =
        if (uuid in pendingLibraries) emptyFlow() else observe("library/$uuid") { libraryEntries[uuid] }

    override suspend fun refreshLibraries(): EmptyResult<DataError.Network> = refresh(LIBRARIES) { libraries }

    override suspend fun refreshLibrary(uuid: String): EmptyResult<DataError.Network> =
        refresh("library/$uuid") { libraryEntries[uuid] }

    override suspend fun refreshPlaylists(): EmptyResult<DataError.Network> = refresh(TREE) { tree }

    override suspend fun refreshPlaylist(uuid: String): EmptyResult<DataError.Network> =
        refresh(uuid) { playlists[uuid] }

    override suspend fun refreshPresentation(uuid: String): EmptyResult<DataError.Network> =
        refresh(uuid) { presentations[uuid] }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> observe(key: String, current: () -> T?): Flow<Result<T, DataError.Network>> {
        val flow = cache.getOrPut(key) { MutableStateFlow(null) }
        return flow.onStart { read(key, current) }.filterNotNull() as Flow<Result<T, DataError.Network>>
    }

    private fun refresh(key: String, current: () -> Any?): EmptyResult<DataError.Network> {
        refreshed += key
        return read(key, current).asEmptyResult()
    }

    private fun read(key: String, current: () -> Any?): Result<Any, DataError.Network> {
        val result = failWith?.let { Result.Failure(it) }
            ?: current()?.let { Result.Success(it) }
            ?: Result.Failure(DataError.Network.NOT_FOUND)
        val flow = cache.getOrPut(key) { MutableStateFlow(null) }
        if (result is Result.Success || flow.value !is Result.Success) flow.value = result
        return result
    }

    private companion object {
        const val TREE = "tree"
        const val LIBRARIES = "libraries"
    }
}
