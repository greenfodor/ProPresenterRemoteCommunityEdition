package com.greenfodor.ppremotece.core.data.session

import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
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
import com.greenfodor.ppremotece.core.domain.status.StatusEvent

/**
 * [ProPresenterClient] that forwards to [current]; calls fail with [DataError.Network.NO_CONNECTION]
 * while it is null.
 */
@Suppress("TooManyFunctions")
internal class CurrentHostClient(
    private val current: suspend () -> ProPresenterClient?
) : ProPresenterClient {
    override suspend fun version(): Result<ProPresenterVersion, DataError.Network> =
        current()?.version() ?: notConnected()

    override suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network> =
        current()?.playlists() ?: notConnected()

    override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> =
        current()?.playlist(uuid) ?: notConnected()

    override suspend fun libraries(): Result<List<Library>, DataError.Network> =
        current()?.libraries() ?: notConnected()

    override suspend fun library(uuid: String): Result<List<LibraryEntry>, DataError.Network> =
        current()?.library(uuid) ?: notConnected()

    override suspend fun presentation(uuid: String): Result<Presentation, DataError.Network> =
        current()?.presentation(uuid) ?: notConnected()

    override suspend fun slideIndex(): Result<LiveSlide?, DataError.Network> = current()?.slideIndex() ?: notConnected()

    override suspend fun activePlaylistItem(): Result<StatusEvent.PlaylistActive, DataError.Network> =
        current()?.activePlaylistItem() ?: notConnected()

    override suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network> =
        current()?.triggerCue(item, cueIndex) ?: notConnected()

    override suspend fun triggerItem(item: PlaylistItemKey): EmptyResult<DataError.Network> =
        current()?.triggerItem(item) ?: notConnected()

    override suspend fun triggerPresentationCue(
        presentationUuid: String,
        cueIndex: Int
    ): EmptyResult<DataError.Network> = current()?.triggerPresentationCue(presentationUuid, cueIndex) ?: notConnected()

    override suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network> =
        current()?.clearLayer(layer) ?: notConnected()

    override suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network> =
        current()?.clearGroups() ?: notConnected()

    override suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network> =
        current()?.triggerClearGroup(uuid) ?: notConnected()

    override suspend fun clearGroupIcon(uuid: String): Result<ClearGroupIcon, DataError.Network> =
        current()?.clearGroupIcon(uuid) ?: notConnected()

    override suspend fun triggerNext(): EmptyResult<DataError.Network> = current()?.triggerNext() ?: notConnected()

    override suspend fun triggerPrevious(): EmptyResult<DataError.Network> =
        current()?.triggerPrevious() ?: notConnected()

    private fun notConnected() = Result.Failure(DataError.Network.NO_CONNECTION)
}
