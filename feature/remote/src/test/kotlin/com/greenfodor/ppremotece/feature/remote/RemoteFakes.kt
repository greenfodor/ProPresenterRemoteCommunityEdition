package com.greenfodor.ppremotece.feature.remote

import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.CueSource
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
import com.greenfodor.ppremotece.core.domain.remote.RemoteCommand
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Records every trigger as the [RemoteCommand] it stands for. */
class FakeProPresenterClient : ProPresenterClient {
    val sent = mutableListOf<RemoteCommand>()
    var failTriggers: DataError.Network? = null

    override suspend fun version(): Result<ProPresenterVersion, DataError.Network> = notServed()

    override suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network> = notServed()

    override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> = notServed()

    override suspend fun presentation(uuid: String): Result<Presentation, DataError.Network> = notServed()

    override suspend fun slideIndex(): Result<LiveSlide?, DataError.Network> = notServed()

    override suspend fun activePlaylistItem(): Result<StatusEvent.PlaylistActive, DataError.Network> = notServed()

    override suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int) = record(
        RemoteCommand.TriggerCue(item, cueIndex)
    )

    override suspend fun triggerItem(item: PlaylistItemKey) = record(RemoteCommand.TriggerItem(item))

    override suspend fun triggerPresentationCue(presentationUuid: String, cueIndex: Int) =
        record(RemoteCommand.TriggerPresentationCue(presentationUuid, cueIndex))

    override suspend fun libraries(): Result<List<Library>, DataError.Network> = notServed()

    override suspend fun library(uuid: String): Result<List<LibraryEntry>, DataError.Network> = notServed()

    override suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network> = notServed()

    override suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network> = notServed()

    override suspend fun clearGroupIcon(uuid: String): Result<ServerIcon, DataError.Network> = notServed()

    override suspend fun triggerMacro(uuid: String): EmptyResult<DataError.Network> = notServed()

    override suspend fun macroIcon(uuid: String): Result<ServerIcon, DataError.Network> = notServed()

    override suspend fun timerOperation(uuid: String, operation: TimerOperation): EmptyResult<DataError.Network> =
        notServed()

    override suspend fun triggerNext() = record(RemoteCommand.TriggerNext)

    override suspend fun triggerPrevious() = record(RemoteCommand.TriggerPrevious)

    private fun record(command: RemoteCommand): EmptyResult<DataError.Network> {
        sent += command
        return failTriggers?.let { Result.Failure(it) } ?: Result.Success(Unit)
    }

    private fun notServed() = Result.Failure(DataError.Network.UNKNOWN)
}

class FakeLiveStateRepository : LiveStateRepository {
    override val liveState = MutableStateFlow(LiveState.Initial)
    override val lastLive = MutableStateFlow<LiveCue?>(null)

    /** Reports [cue] live, as the stream does, and remembers it; a presentation cue is live with [totalCues]. */
    fun goLive(cue: LiveCue, totalCues: Int = 0) {
        lastLive.value = cue
        liveState.value = liveState.value.copy(
            item = (cue.source as? CueSource.PlaylistItem)?.key,
            slide = LiveSlide(cue.presentationUuid, cue.cueIndex, totalCues)
        )
    }

    fun clear() {
        liveState.value = liveState.value.copy(item = null, slide = null)
    }
}

/** Serves fixed playlists and presentations, counting each read; uuids in [failing] fail with a server error. */
class FakeContentRepository(
    private val playlists: Map<String, Playlist>,
    private val presentations: Map<String, Presentation>
) : ContentRepository {
    val reads = mutableListOf<String>()
    val failing = mutableSetOf<String>()

    override fun playlists(): Flow<Result<List<PlaylistTreeNode>, DataError.Network>> = flowOf(
        Result.Success(emptyList())
    )

    override fun playlist(uuid: String): Flow<Result<Playlist, DataError.Network>> = flow {
        emit(read(uuid, playlists[uuid]))
    }

    override fun presentation(uuid: String): Flow<Result<Presentation, DataError.Network>> =
        flow { emit(read(uuid, presentations[uuid])) }

    override suspend fun refreshPlaylists(): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun refreshPlaylist(uuid: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun refreshPresentation(uuid: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override fun libraries(): Flow<Result<List<Library>, DataError.Network>> = flowOf(Result.Success(emptyList()))

    override fun library(uuid: String): Flow<Result<List<LibraryEntry>, DataError.Network>> =
        flowOf(Result.Success(emptyList()))

    override suspend fun refreshLibraries(): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun refreshLibrary(uuid: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    private fun <T : Any> read(uuid: String, value: T?): Result<T, DataError.Network> {
        reads += uuid
        return when {
            uuid in failing -> Result.Failure(DataError.Network.SERVER)
            value != null -> Result.Success(value)
            else -> Result.Failure(DataError.Network.NOT_FOUND)
        }
    }
}
