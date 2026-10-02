package com.greenfodor.ppremotece.feature.playlist

import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
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

/** Records triggers; reads are not served. */
class FakeProPresenterClient : ProPresenterClient {
    val triggeredCues = mutableListOf<Pair<PlaylistItemKey, Int>>()
    val triggeredPresentationCues = mutableListOf<Pair<String, Int>>()
    val steps = mutableListOf<String>()

    override suspend fun version(): Result<ProPresenterVersion, DataError.Network> = notServed()

    override suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network> = notServed()

    override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> = notServed()

    override suspend fun presentation(uuid: String): Result<Presentation, DataError.Network> = notServed()

    override suspend fun slideIndex(): Result<LiveSlide?, DataError.Network> = notServed()

    override suspend fun activePlaylistItem(): Result<StatusEvent.PlaylistActive, DataError.Network> = notServed()

    override suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network> {
        triggeredCues += item to cueIndex
        return Result.Success(Unit)
    }

    override suspend fun triggerItem(item: PlaylistItemKey): EmptyResult<DataError.Network> = notServed()

    override suspend fun triggerPresentationCue(
        presentationUuid: String,
        cueIndex: Int
    ): EmptyResult<DataError.Network> {
        triggeredPresentationCues += presentationUuid to cueIndex
        return Result.Success(Unit)
    }

    override suspend fun libraries(): Result<List<Library>, DataError.Network> = notServed()

    override suspend fun library(uuid: String): Result<List<LibraryEntry>, DataError.Network> = notServed()

    override suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network> = notServed()

    override suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearGroupIcon(uuid: String): Result<ServerIcon, DataError.Network> = notServed()

    override suspend fun triggerMacro(uuid: String): EmptyResult<DataError.Network> = notServed()

    override suspend fun triggerLook(uuid: String): EmptyResult<DataError.Network> = notServed()

    override suspend fun macroIcon(uuid: String, refresh: Boolean): Result<ServerIcon, DataError.Network> =
        notServed()

    override suspend fun timerOperation(uuid: String, operation: TimerOperation): EmptyResult<DataError.Network> =
        notServed()

    override suspend fun triggerNext(): EmptyResult<DataError.Network> {
        steps += "next"
        return Result.Success(Unit)
    }

    override suspend fun triggerPrevious(): EmptyResult<DataError.Network> {
        steps += "previous"
        return Result.Success(Unit)
    }

    private fun notServed() = Result.Failure(DataError.Network.UNKNOWN)
}
