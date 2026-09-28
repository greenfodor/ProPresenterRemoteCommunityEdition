package com.greenfodor.ppremotece.core.data.network

import com.greenfodor.ppremotece.core.data.dto.PlaylistDto
import com.greenfodor.ppremotece.core.data.dto.PlaylistTreeNodeDto
import com.greenfodor.ppremotece.core.data.dto.PresentationResponseDto
import com.greenfodor.ppremotece.core.data.dto.SlideIndexResponseDto
import com.greenfodor.ppremotece.core.data.dto.VersionDto
import com.greenfodor.ppremotece.core.data.mapper.toDomain
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.map
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * [ProPresenterClient] for the ProPresenter HTTP API at [baseUrl], plus the `status/updates`
 * stream. Sends only GET reads, the item-cue, next and previous triggers, and the stream POST.
 */
class KtorProPresenterClient(
    private val httpClient: HttpClient,
    baseUrl: String
) : ProPresenterClient {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun version(): Result<ProPresenterVersion, DataError.Network> =
        safeCall<VersionDto> { httpClient.get("$baseUrl/version") }.map { it.toDomain() }

    override suspend fun playlists(): Result<List<PlaylistTreeNode>, DataError.Network> =
        safeCall<List<PlaylistTreeNodeDto>> { httpClient.get("$baseUrl/v1/playlists") }
            .map { nodes -> nodes.map { it.toDomain() } }

    override suspend fun playlist(uuid: String): Result<Playlist, DataError.Network> =
        safeCall<PlaylistDto> { httpClient.get("$baseUrl/v1/playlist/${uuid.encodeURLPathPart()}") }
            .map { it.toDomain() }

    override suspend fun presentation(uuid: String): Result<Presentation, DataError.Network> =
        safeCall<PresentationResponseDto> { httpClient.get("$baseUrl/v1/presentation/${uuid.encodeURLPathPart()}") }
            .map { it.toDomain() }

    override suspend fun slideIndex(): Result<LiveSlide?, DataError.Network> =
        safeCall<SlideIndexResponseDto> { httpClient.get("$baseUrl/v1/presentation/slide_index") }
            .map { it.toDomain() }

    override suspend fun triggerCue(item: PlaylistItemKey, cueIndex: Int): EmptyResult<DataError.Network> =
        safeEmptyCall {
            httpClient.get(
                "$baseUrl/v1/playlist/${item.playlistUuid.encodeURLPathPart()}/${item.index}/$cueIndex/trigger"
            )
        }

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

    private companion object {
        const val READ_BUFFER_SIZE = 8 * 1024
    }
}
