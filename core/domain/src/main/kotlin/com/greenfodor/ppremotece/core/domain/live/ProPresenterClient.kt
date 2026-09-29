package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result

/** Reads and triggers on one ProPresenter host over its HTTP API. */
@Suppress("TooManyFunctions")
interface ProPresenterClient {
    suspend fun version(): Result<ProPresenterVersion, DataError.Network>

    suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network>

    suspend fun playlist(uuid: String): Result<Playlist, DataError.Network>

    suspend fun presentation(uuid: String): Result<Presentation, DataError.Network>

    suspend fun slideIndex(): Result<LiveSlide?, DataError.Network>

    suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network>

    /** Triggers a playlist item: a presentation at its first cue, a media or audio item as a whole. */
    suspend fun triggerItem(item: PlaylistItemKey): EmptyResult<DataError.Network>

    suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network>

    suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network>

    suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network>

    suspend fun triggerNext(): EmptyResult<DataError.Network>

    suspend fun triggerPrevious(): EmptyResult<DataError.Network>
}
