package com.greenfodor.ppremotece.core.data.session

import com.greenfodor.ppremotece.core.data.live.StreamingLiveStateRepository
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The connection to the current ProPresenter host: its [KtorProPresenterClient] and its
 * [StreamingLiveStateRepository], which lives in a scope that [disconnect] cancels.
 * [sessionKey] names the current connection (`{n}@{address}:{port}`, new on each connect, null
 * while disconnected), and [streamReconnects] emits each time the live stream is reopened.
 */
class ProPresenterSession(
    private val httpClient: HttpClient,
    private val savedHostStore: SavedHostStore
) : ConnectionRepository,
    LiveStateRepository {
    private class Connection(
        val client: KtorProPresenterClient,
        val live: StreamingLiveStateRepository,
        val scope: CoroutineScope
    )

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connection = MutableStateFlow<Connection?>(null)
    private val restoreMutex = Mutex()
    private val connectCount = AtomicInteger()

    private val _sessionKey = MutableStateFlow<String?>(null)
    val sessionKey: StateFlow<String?> = _sessionKey.asStateFlow()

    private val _streamReconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val streamReconnects: SharedFlow<Unit> = _streamReconnects.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val liveState: Flow<LiveState> =
        connection.flatMapLatest { it?.live?.liveState ?: flowOf(LiveState.Initial) }

    override suspend fun savedHost(): ProPresenterHost? = savedHostStore.read()

    override suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network> {
        val client = KtorProPresenterClient(httpClient, "http://${host.address}:${host.port}/")
        return client.version().onSuccess {
            val scope = CoroutineScope(sessionScope.coroutineContext + SupervisorJob(sessionScope.coroutineContext.job))
            val live = StreamingLiveStateRepository(client, scope, onReconnected = { _streamReconnects.tryEmit(Unit) })
            connection.getAndUpdate { Connection(client, live, scope) }?.scope?.cancel()
            _sessionKey.value = "${connectCount.incrementAndGet()}@${host.address}:${host.port}"
            try {
                savedHostStore.save(host)
            } catch (_: IOException) {
                // The connection stays open; only the saved host is not updated.
            }
        }
    }

    override suspend fun disconnect() {
        try {
            savedHostStore.clear()
        } finally {
            connection.getAndUpdate { null }?.scope?.cancel()
            _sessionKey.value = null
        }
    }

    /** Reconnects to the saved host when no host is connected. */
    suspend fun restore() {
        currentClient()
    }

    /** Forwards to the current host's client; with none connected, first reconnects to the saved host. */
    val client: ProPresenterClient = CurrentHostClient(::currentClient)

    private suspend fun currentClient(): ProPresenterClient? =
        connection.value?.client ?: restoreMutex.withLock {
            connection.value?.client ?: savedHostStore.read()?.let { host ->
                connect(host)
                connection.value?.client
            }
        }
}
