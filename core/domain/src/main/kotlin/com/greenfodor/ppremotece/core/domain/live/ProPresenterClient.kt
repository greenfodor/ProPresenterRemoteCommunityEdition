package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.AudioTrack
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
import kotlinx.coroutines.flow.Flow

/** Reads and triggers on one ProPresenter host over its HTTP API. */
@Suppress("TooManyFunctions")
interface ProPresenterClient {
    suspend fun version(): Result<ProPresenterVersion, DataError.Network>

    suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network>

    suspend fun playlist(uuid: String): Result<Playlist, DataError.Network>

    /**
     * One emission each time ProPresenter reports that playlist [uuid] was edited, over a
     * connection that stays open while the flow is collected. The flow completes when ProPresenter
     * ends the connection, and fails with [PlaylistNotFoundException] when it does not know the
     * playlist and with the transport's error when the connection fails.
     */
    fun playlistChanges(uuid: String): Flow<Unit>

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

    /** Triggers look [uuid] as the live audience look. */
    suspend fun triggerLook(uuid: String): EmptyResult<DataError.Network>

    /** Shows prop [uuid]; an active prop stays on. */
    suspend fun triggerProp(uuid: String): EmptyResult<DataError.Network>

    /** Clears prop [uuid]. */
    suspend fun clearProp(uuid: String): EmptyResult<DataError.Network>

    /** Starts, stops or resets timer [uuid]. */
    suspend fun timerOperation(uuid: String, operation: TimerOperation): EmptyResult<DataError.Network>

    /** The tracks of the audio playlist [uuid]. */
    suspend fun audioPlaylist(uuid: String): Result<List<AudioTrack>, DataError.Network>

    /** Plays the track [trackUuid] of the audio playlist [playlistUuid]. */
    suspend fun triggerAudioTrack(playlistUuid: String, trackUuid: String): EmptyResult<DataError.Network>

    /** Plays the next track of the active audio playlist. */
    suspend fun audioNext(): EmptyResult<DataError.Network>

    /** Plays the previous track of the active audio playlist. */
    suspend fun audioPrevious(): EmptyResult<DataError.Network>

    /** Resumes the audio layer's loaded audio. */
    suspend fun audioPlay(): EmptyResult<DataError.Network>

    /** Pauses the audio layer's loaded audio. */
    suspend fun audioPause(): EmptyResult<DataError.Network>

    suspend fun triggerNext(): EmptyResult<DataError.Network>

    suspend fun triggerPrevious(): EmptyResult<DataError.Network>
}

/** ProPresenter does not know the playlist whose changes were asked for. */
class PlaylistNotFoundException : Exception()
