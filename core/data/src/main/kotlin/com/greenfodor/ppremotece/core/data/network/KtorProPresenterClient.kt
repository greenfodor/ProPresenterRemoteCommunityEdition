package com.greenfodor.ppremotece.core.data.network

import com.greenfodor.ppremotece.core.data.dto.ClearGroupDto
import com.greenfodor.ppremotece.core.data.dto.IdDto
import com.greenfodor.ppremotece.core.data.dto.LibraryResponseDto
import com.greenfodor.ppremotece.core.data.dto.PlaylistDto
import com.greenfodor.ppremotece.core.data.dto.PlaylistTreeNodeDto
import com.greenfodor.ppremotece.core.data.dto.PresentationResponseDto
import com.greenfodor.ppremotece.core.data.dto.SlideIndexResponseDto
import com.greenfodor.ppremotece.core.data.dto.VersionDto
import com.greenfodor.ppremotece.core.data.mapper.toDomain
import com.greenfodor.ppremotece.core.data.mapper.toLibrary
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
import com.greenfodor.ppremotece.core.domain.result.map
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import com.greenfodor.ppremotece.core.domain.status.playlistActiveOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [ProPresenterClient] for the ProPresenter HTTP API at [baseUrl], plus the `status/updates`
 * stream. Sends only GET reads (including clear-group and macro icons, each read once per route as
 * PNG, JPEG or SVG of at most 256 KB), the item-cue, item, presentation-cue, next and previous
 * triggers, the layer and clear-group clears, the timer operations, the macro trigger, and the
 * stream POST.
 */
@Suppress("TooManyFunctions")
class KtorProPresenterClient(
    private val httpClient: HttpClient,
    baseUrl: String
) : ProPresenterClient {
    private val baseUrl = baseUrl.trimEnd('/')
    private val icons = ConcurrentHashMap<String, ServerIcon>()

    override suspend fun version(): Result<ProPresenterVersion, DataError.Network> =
        safeCall<VersionDto> { httpClient.get("$baseUrl/version") }.map { it.toDomain() }

    override suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network> =
        safeCall<List<PlaylistTreeNodeDto>> { httpClient.get("$baseUrl/v1/playlists") }
            .map { nodes -> nodes.map { it.toDomain() } }

    override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> =
        safeCall<PlaylistDto> { httpClient.get("$baseUrl/v1/playlist/${uuid.encodeURLPathPart()}") }
            .map { it.toDomain() }

    override suspend fun libraries(): Result<List<Library>, DataError.Network> =
        safeCall<List<IdDto>> { httpClient.get("$baseUrl/v1/libraries") }.map { ids -> ids.map { it.toLibrary() } }

    override suspend fun library(uuid: String): Result<List<LibraryEntry>, DataError.Network> =
        safeCall<LibraryResponseDto> { httpClient.get("$baseUrl/v1/library/${uuid.encodeURLPathPart()}") }
            .map { it.toDomain() }

    override suspend fun presentation(uuid: String): Result<Presentation, DataError.Network> =
        safeCall<PresentationResponseDto> { httpClient.get("$baseUrl/v1/presentation/${uuid.encodeURLPathPart()}") }
            .map { it.toDomain() }

    override suspend fun slideIndex(): Result<LiveSlide?, DataError.Network> =
        safeCall<SlideIndexResponseDto> { httpClient.get("$baseUrl/v1/presentation/slide_index") }
            .map { it.toDomain() }

    override suspend fun activePlaylistItem(): Result<StatusEvent.PlaylistActive, DataError.Network> =
        safeCall<JsonObject> { httpClient.get("$baseUrl/v1/playlist/active") }.map(::playlistActiveOf)

    override suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network> =
        safeEmptyCall {
            httpClient.get("$baseUrl/${playlistItemPath(item)}/$cueIndex/trigger")
        }

    override suspend fun triggerItem(item: PlaylistItemKey): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/${playlistItemPath(item)}/trigger") }

    override suspend fun triggerPresentationCue(
        presentationUuid: String,
        cueIndex: Int
    ): EmptyResult<DataError.Network> =
        safeEmptyCall {
            httpClient.get("$baseUrl/v1/presentation/${presentationUuid.encodeURLPathPart()}/$cueIndex/trigger")
        }

    override suspend fun clearLayer(layer: OutputLayer): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/clear/layer/${layer.apiName}") }

    override suspend fun clearGroups(): Result<List<ClearGroup>, DataError.Network> =
        safeCall<List<ClearGroupDto>> { httpClient.get("$baseUrl/v1/clear/groups") }
            .map { groups -> groups.map { it.toDomain() } }

    override suspend fun triggerClearGroup(uuid: String): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/clear/group/${uuid.encodeURLPathPart()}/trigger") }

    override suspend fun clearGroupIcon(uuid: String): Result<ServerIcon, DataError.Network> =
        icon("v1/clear/group/${uuid.encodeURLPathPart()}/icon")

    override suspend fun triggerMacro(uuid: String): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/macro/${uuid.encodeURLPathPart()}/trigger") }

    override suspend fun triggerLook(uuid: String): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/look/${uuid.encodeURLPathPart()}/trigger") }

    override suspend fun triggerProp(uuid: String): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/prop/${uuid.encodeURLPathPart()}/trigger") }

    override suspend fun clearProp(uuid: String): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/prop/${uuid.encodeURLPathPart()}/clear") }

    override suspend fun macroIcon(uuid: String, refresh: Boolean): Result<ServerIcon, DataError.Network> =
        icon("v1/macro/${uuid.encodeURLPathPart()}/icon", refresh)

    /** The icon at [path], read once per client, and again when [refresh] is set. */
    private suspend fun icon(path: String, refresh: Boolean = false): Result<ServerIcon, DataError.Network> =
        icons[path]?.takeUnless { refresh }?.let { Result.Success(it) } ?: readIcon(path).onSuccess { icons[path] = it }

    private suspend fun readIcon(path: String): Result<ServerIcon, DataError.Network> =
        when (val read = safeCall<ByteArray> { httpClient.get("$baseUrl/$path") }) {
            is Result.Failure -> Result.Failure(read.error)
            is Result.Success ->
                withContext(Dispatchers.Default) { read.data.takeIf { it.size <= MAX_ICON_BYTES }?.let(::iconOf) }
                    ?.let { Result.Success(it) }
                    ?: Result.Failure(DataError.Network.SERIALIZATION)
        }

    override suspend fun timerOperation(uuid: String, operation: TimerOperation): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/timer/${uuid.encodeURLPathPart()}/${operation.apiName}") }

    override suspend fun triggerNext(): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/trigger/next") }

    override suspend fun triggerPrevious(): EmptyResult<DataError.Network> =
        safeEmptyCall { httpClient.get("$baseUrl/v1/trigger/previous") }

    /**
     * Posts [subscriptions] to `status/updates` and emits the response body as it arrives, one
     * array per read. Completes when the server ends the stream and throws [IOException] when the
     * server rejects it.
     */
    fun statusUpdates(subscriptions: List<String>): Flow<ByteArray> =
        flow {
            httpClient.preparePost("$baseUrl/v1/status/updates") {
                contentType(ContentType.Application.Json)
                setBody(subscriptions)
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            }.execute { response ->
                if (!response.status.isSuccess()) throw IOException("status/updates rejected: ${response.status}")
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(READ_BUFFER_SIZE)
                var read = channel.readAvailable(buffer)
                while (read >= 0) {
                    if (read > 0) emit(buffer.copyOf(read))
                    read = channel.readAvailable(buffer)
                }
            }
        }

    private fun iconOf(bytes: ByteArray): ServerIcon? =
        if (IMAGE_SIGNATURES.any { signature -> bytes.take(signature.size) == signature }) {
            ServerIcon.Image(bytes)
        } else {
            parseSvgIcon(bytes.decodeToString())
        }

    private companion object {
        const val READ_BUFFER_SIZE = 8 * 1024
        const val MAX_ICON_BYTES = 256 * 1024
        val IMAGE_SIGNATURES = listOf(
            listOf(0x89, 0x50, 0x4E, 0x47).map { it.toByte() },
            listOf(0xFF, 0xD8, 0xFF).map { it.toByte() }
        )
    }
}
