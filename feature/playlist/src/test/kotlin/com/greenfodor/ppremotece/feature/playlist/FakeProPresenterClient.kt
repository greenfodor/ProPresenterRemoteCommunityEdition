package com.greenfodor.ppremotece.feature.playlist

import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
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

/** Records triggers; reads are not served. */
class FakeProPresenterClient : ProPresenterClient {
    val triggeredCues = mutableListOf<Pair<PlaylistItemKey, Int>>()

    override suspend fun version(): Result<ProPresenterVersion, DataError.Network> = notServed()

    override suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network> = notServed()

    override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> = notServed()

    override suspend fun presentation(uuid: String): Result<Presentation, DataError.Network> = notServed()

    override suspend fun slideIndex(): Result<LiveSlide?, DataError.Network> = notServed()

    override suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network> {
        triggeredCues += item to cueIndex
        return Result.Success(Unit)
    }

    override suspend fun triggerItem(item: PlaylistItemKey): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network> = notServed()

    override suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearGroupIcon(uuid: String): Result<ClearGroupIcon, DataError.Network> = notServed()

    override suspend fun triggerNext(): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun triggerPrevious(): EmptyResult<DataError.Network> = Result.Success(Unit)

    private fun notServed() = Result.Failure(DataError.Network.UNKNOWN)
}
