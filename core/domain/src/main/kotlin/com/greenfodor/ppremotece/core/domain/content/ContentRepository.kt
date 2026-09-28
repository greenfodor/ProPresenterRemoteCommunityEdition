package com.greenfodor.ppremotece.core.domain.content

import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Playlists and presentations of the connected host, cached for the session.
 *
 * Each flow emits the cached value, if any, and starts one read of it; it emits again only when
 * the value read differs. A failed read is emitted only when nothing is cached. The `refresh`
 * functions read a value again now and return that read's result.
 */
interface ContentRepository {
    fun playlists(): Flow<Result<List<PlaylistTreeNode>, DataError.Network>>

    fun playlist(uuid: String): Flow<Result<Playlist, DataError.Network>>

    fun presentation(uuid: String): Flow<Result<Presentation, DataError.Network>>

    suspend fun refreshPlaylists(): EmptyResult<DataError.Network>

    suspend fun refreshPlaylist(uuid: String): EmptyResult<DataError.Network>

    suspend fun refreshPresentation(uuid: String): EmptyResult<DataError.Network>
}
