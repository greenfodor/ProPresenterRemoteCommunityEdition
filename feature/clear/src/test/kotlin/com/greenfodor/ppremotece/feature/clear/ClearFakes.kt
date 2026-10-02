package com.greenfodor.ppremotece.feature.clear

import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
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
import kotlinx.coroutines.flow.MutableStateFlow

/** Records the clears it is sent; groups in [failingGroups] fail with a server error. */
class FakeClearClient : ProPresenterClient {
    val clearedLayers = mutableListOf<OutputLayer>()
    val triggeredGroups = mutableListOf<String>()
    val failingGroups = mutableSetOf<String>()
    var groups: List<ClearGroup> = emptyList()
    var groupReads = 0
    val icons = mutableMapOf<String, ServerIcon>()
    val iconReads = mutableListOf<String>()

    override suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network> {
        clearedLayers += layer
        return Result.Success(Unit)
    }

    override suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network> {
        groupReads++
        return Result.Success(groups)
    }

    override suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network> {
        triggeredGroups += uuid
        return if (uuid in failingGroups) Result.Failure(DataError.Network.SERVER) else Result.Success(Unit)
    }

    override suspend fun version(): Result<ProPresenterVersion, DataError.Network> = notServed()

    override suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network> = notServed()

    override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> = notServed()

    override suspend fun presentation(uuid: String): Result<Presentation, DataError.Network> = notServed()

    override suspend fun slideIndex(): Result<LiveSlide?, DataError.Network> = notServed()

    override suspend fun activePlaylistItem(): Result<StatusEvent.PlaylistActive, DataError.Network> = notServed()

    override suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network> = notServed()

    override suspend fun triggerItem(item: PlaylistItemKey): EmptyResult<DataError.Network> = notServed()

    override suspend fun libraries(): Result<List<Library>, DataError.Network> = notServed()

    override suspend fun library(uuid: String): Result<List<LibraryEntry>, DataError.Network> = notServed()

    override suspend fun triggerPresentationCue(
        presentationUuid: String,
        cueIndex: Int
    ): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearGroupIcon(uuid: String): Result<ServerIcon, DataError.Network> {
        iconReads += uuid
        return icons[uuid]?.let { Result.Success(it) } ?: Result.Failure(DataError.Network.NOT_FOUND)
    }

    override suspend fun triggerMacro(uuid: String): EmptyResult<DataError.Network> = notServed()

    override suspend fun macroIcon(uuid: String, refresh: Boolean): Result<ServerIcon, DataError.Network> =
        notServed()

    override suspend fun timerOperation(uuid: String, operation: TimerOperation): EmptyResult<DataError.Network> =
        notServed()

    override suspend fun triggerNext(): EmptyResult<DataError.Network> = notServed()

    override suspend fun triggerPrevious(): EmptyResult<DataError.Network> = notServed()

    private fun notServed() = Result.Failure(DataError.Network.UNKNOWN)
}

class FakeLiveStateRepository : LiveStateRepository {
    override val liveState = MutableStateFlow(LiveState.Initial)
    override val lastLive = MutableStateFlow<LiveCue?>(null)
}
