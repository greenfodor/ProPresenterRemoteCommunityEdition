package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.model.ServerIcon
import com.greenfodor.ppremotece.core.domain.model.TimerOperation
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.status.StatusEvent

/** Reads and triggers on one ProPresenter host over its HTTP API. */
@Suppress("TooManyFunctions")
interface ProPresenterClient {
    suspend fun version(): Result<ProPresenterVersion, DataError.Network>

    suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network>

    suspend fun playlist(uuid: String): Result<Playlist, DataError.Network>

    suspend fun libraries(): Result<List<Library>, DataError.Network>

    /** The presentations of library [uuid]. */
    suspend fun library(uuid: String): Result<List<LibraryEntry>, DataError.Network>

    suspend fun presentation(uuid: String): Result<Presentation, DataError.Network>

    suspend fun slideIndex(): Result<LiveSlide?, DataError.Network>

    /** The live playlist item, as `GET /v1/playlist/active` reports it. */
    suspend fun activePlaylistItem(): Result<StatusEvent.PlaylistActive, DataError.Network>

    suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network>

    /** Triggers a playlist item: a presentation at its first cue, a media or audio item as a whole. */
    suspend fun triggerItem(item: PlaylistItemKey): EmptyResult<DataError.Network>

    /** Triggers cue [cueIndex] of a presentation outside a playlist, in its current arrangement. */
    suspend fun triggerPresentationCue(presentationUuid: String, cueIndex: Int): EmptyResult<DataError.Network>

    suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network>

    suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network>

    suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network>

    suspend fun clearGroupIcon(uuid: String): Result<ServerIcon, DataError.Network>

    suspend fun triggerMacro(uuid: String): EmptyResult<DataError.Network>

    /** Macro [uuid]'s icon; a client may serve an icon it read before unless [refresh] is set. */
    suspend fun macroIcon(uuid: String, refresh: Boolean = false): Result<ServerIcon, DataError.Network>

    /** Starts, stops or resets timer [uuid]. */
    suspend fun timerOperation(uuid: String, operation: TimerOperation): EmptyResult<DataError.Network>

    suspend fun triggerNext(): EmptyResult<DataError.Network>

    suspend fun triggerPrevious(): EmptyResult<DataError.Network>
}
