package com.greenfodor.ppremotece.core.data.session

import com.greenfodor.ppremotece.core.data.live.StreamingLiveStateRepository
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.data.thumbnail.presentationThumbnailUrl
import com.greenfodor.ppremotece.core.data.thumbnail.propThumbnailUrl
import com.greenfodor.ppremotece.core.data.thumbnail.thumbnailUrl
import com.greenfodor.ppremotece.core.domain.audio.AudioRepository
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.looks.LooksRepository
import com.greenfodor.ppremotece.core.domain.macros.MacrosRepository
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioPlaylist
import com.greenfodor.ppremotece.core.domain.model.ConnectedHost
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion
import com.greenfodor.ppremotece.core.domain.model.PropCollection
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailRequest
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailRequests
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailSource
import com.greenfodor.ppremotece.core.domain.props.PropsRepository
import com.greenfodor.ppremotece.core.domain.props.propThumbnailKey
import com.greenfodor.ppremotece.core.domain.props.propThumbnailWidth
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailKey
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailQuality
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequests
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import com.greenfodor.ppremotece.core.domain.thumbnail.boxQuality
import com.greenfodor.ppremotece.core.domain.timers.LiveTimer
import com.greenfodor.ppremotece.core.domain.timers.TimersRepository
import com.greenfodor.ppremotece.core.domain.transport.TransportRepository
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The connection to the current ProPresenter host: its [KtorProPresenterClient] and its
 * [StreamingLiveStateRepository], which lives in a scope that [disconnect] cancels; [disconnect] keeps the saved host
 * and marks it to stay disconnected.
 * [sessionKey] names the current connection (`{n}@{address}:{port}`, new on each connect, null
 * while disconnected), and [streamReconnects] emits each time the live stream is reopened. Each
 * successful connect clears the [ThumbnailCache] in the background; the connection's
 * [thumbnailRequests] are null until that clear has finished. [timers] and [collections] are the connection's
 * timers and macro collections, and [looks] and [currentLook] its looks and live look,
 * [Loadable.NotLoaded] and null while disconnected; [propCollections] its prop collections and
 * [propThumbnailRequests] its prop thumbnail requests, keyed by the host's name and null until the
 * thumbnail cache is cleared; [presentationTransport] and [audioTransport] what its transport layers
 * have loaded, not loaded while disconnected; [audioPlaylists], [activeAudio] and [audioPosition] its audio
 * bin, the track it plays and the audio position, not loaded and null while disconnected.
 */
class ProPresenterSession(
    private val httpClient: HttpClient,
    private val savedHostStore: SavedHosts,
    private val thumbnailCache: ThumbnailCache
) : ConnectionRepository,
    LiveStateRepository,
    TimersRepository,
    MacrosRepository,
    LooksRepository,
    PropsRepository,
    TransportRepository,
    AudioRepository,
    PropThumbnailSource,
    ThumbnailSource {
    private class Connection(
        val client: KtorProPresenterClient,
        val live: StreamingLiveStateRepository,
        val thumbnails: StateFlow<ThumbnailRequests?>,
        val propThumbnails: StateFlow<PropThumbnailRequests?>,
        val scope: CoroutineScope
    )

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connection = MutableStateFlow<Connection?>(null)
    private val restoreMutex = Mutex()
    private val connectCount = AtomicInteger()

    @Volatile
    private var restoreAllowed = true

    private val _sessionKey = MutableStateFlow<String?>(null)
    val sessionKey: StateFlow<String?> = _sessionKey.asStateFlow()

    private val _connectedHost = MutableStateFlow<ConnectedHost?>(null)
    override val connectedHost: StateFlow<ConnectedHost?> = _connectedHost.asStateFlow()

    private val _streamReconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val streamReconnects: SharedFlow<Unit> = _streamReconnects.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val liveState: Flow<LiveState> =
        connection.flatMapLatest { it?.live?.liveState ?: flowOf(LiveState.Initial) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val lastLive: StateFlow<LiveCue?> =
        connection
            .flatMapLatest { it?.live?.lastLive ?: flowOf(null) }
            .stateIn(sessionScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val timers: StateFlow<Loadable<List<LiveTimer>>> =
        connection
            .flatMapLatest { it?.live?.timers ?: flowOf(Loadable.NotLoaded) }
            .stateIn(sessionScope, SharingStarted.Eagerly, Loadable.NotLoaded)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val collections: StateFlow<Loadable<List<MacroCollection>>> =
        connection
            .flatMapLatest { it?.live?.collections ?: flowOf(Loadable.NotLoaded) }
            .stateIn(sessionScope, SharingStarted.Eagerly, Loadable.NotLoaded)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val looks: StateFlow<Loadable<List<Look>>> =
        connection
            .flatMapLatest { it?.live?.looks ?: flowOf(Loadable.NotLoaded) }
            .stateIn(sessionScope, SharingStarted.Eagerly, Loadable.NotLoaded)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentLook: StateFlow<Look?> =
        connection
            .flatMapLatest { it?.live?.currentLook ?: flowOf(null) }
            .stateIn(sessionScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val thumbnailRequests: Flow<ThumbnailRequests?> =
        connection.flatMapLatest { it?.thumbnails ?: flowOf(null) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val propThumbnailRequests: Flow<PropThumbnailRequests?> =
        connection.flatMapLatest { it?.propThumbnails ?: flowOf(null) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val propCollections: StateFlow<Loadable<List<PropCollection>>> =
        connection
            .flatMapLatest { it?.live?.propCollections ?: flowOf(Loadable.NotLoaded) }
            .stateIn(sessionScope, SharingStarted.Eagerly, Loadable.NotLoaded)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val presentationTransport: StateFlow<Loadable<Transport>> =
        connection
            .flatMapLatest { it?.live?.presentationTransport ?: flowOf(Loadable.NotLoaded) }
            .stateIn(sessionScope, SharingStarted.Eagerly, Loadable.NotLoaded)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val audioTransport: StateFlow<Loadable<Transport>> =
        connection
            .flatMapLatest { it?.live?.audioTransport ?: flowOf(Loadable.NotLoaded) }
            .stateIn(sessionScope, SharingStarted.Eagerly, Loadable.NotLoaded)

    /** Whether the current connection's status stream is connected. */
    fun isStreamConnected(): Boolean =
        connection.value?.live?.liveState?.value?.connection == ConnectionStatus.CONNECTED

    /** Makes the current connection read its live slide and item again. */
    fun requestLiveRead() {
        connection.value?.live?.requestLiveRead()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val audioPlaylists: StateFlow<Loadable<List<AudioPlaylist>>> =
        connection
            .flatMapLatest { it?.live?.audioPlaylists ?: flowOf(Loadable.NotLoaded) }
            .stateIn(sessionScope, SharingStarted.Eagerly, Loadable.NotLoaded)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val audioPlaylistFrames: StateFlow<Int> =
        connection
            .flatMapLatest { it?.live?.audioPlaylistFrames ?: flowOf(0) }
            .stateIn(sessionScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val activeAudio: StateFlow<ActiveAudio?> =
        connection
            .flatMapLatest { it?.live?.activeAudio ?: flowOf(null) }
            .stateIn(sessionScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val audioPosition: StateFlow<Double?> =
        connection
            .flatMapLatest { it?.live?.audioPosition ?: flowOf(null) }
            .stateIn(sessionScope, SharingStarted.Eagerly, null)

    override suspend fun savedHost(): ProPresenterHost? = readSavedHost()?.host

    override suspend fun stayDisconnected(): Boolean = readSavedHost()?.stayDisconnected == true

    override suspend fun connect(host: ProPresenterHost): Result<ProPresenterVersion, DataError.Network> {
        val baseUrl = "http://${host.address}:${host.port}/"
        val client = KtorProPresenterClient(httpClient, baseUrl)
        return client.version().onSuccess { version ->
            val namedByVersion = host.name == host.address ||
                readSavedHost()?.let {
                    it.namedByVersion && it.host.address == host.address && it.host.port == host.port
                } ==
                true
            val named = if (namedByVersion) host.copy(name = version.name.ifBlank { host.address }) else host
            val scope = CoroutineScope(sessionScope.coroutineContext + SupervisorJob(sessionScope.coroutineContext.job))
            val live = StreamingLiveStateRepository(client, scope, onReconnected = { _streamReconnects.tryEmit(Unit) })
            val thumbnails = MutableStateFlow<ThumbnailRequests?>(null)
            val propThumbnails = MutableStateFlow<PropThumbnailRequests?>(null)
            connection.getAndUpdate { Connection(client, live, thumbnails, propThumbnails, scope) }?.scope?.cancel()
            _sessionKey.value = "${connectCount.incrementAndGet()}@${host.address}:${host.port}"
            _connectedHost.value = ConnectedHost(named, version)
            restoreAllowed = true
            scope.launch {
                thumbnailCache.clear()
                thumbnails.value = thumbnailRequests(baseUrl, version.name)
                propThumbnails.value = propThumbnailRequests(baseUrl, version.name)
            }
            try {
                savedHostStore.save(named, namedByVersion)
            } catch (_: IOException) {
                // The connection stays open; only the saved host is not updated.
            }
        }
    }

    private suspend fun readSavedHost(): SavedHost? = savedHostStore.read()

    override suspend fun disconnect() {
        restoreAllowed = false
        restoreMutex.withLock {
            restoreAllowed = false
            connection.getAndUpdate { null }?.scope?.cancel()
            _sessionKey.value = null
            _connectedHost.value = null
        }
        try {
            savedHostStore.setStayDisconnected()
        } catch (_: IOException) {
            // The saved host stays as it was.
        }
    }

    private fun propThumbnailRequests(baseUrl: String, instanceName: String) =
        PropThumbnailRequests { uuid, px ->
            val width = propThumbnailWidth(px)
            PropThumbnailRequest(propThumbnailUrl(baseUrl, uuid, width), propThumbnailKey(instanceName, uuid, width))
        }

    private fun thumbnailRequests(baseUrl: String, instanceName: String) =
        ThumbnailRequests { source, presentationUuid, cue, quality ->
            val box = (quality as? ThumbnailQuality.Box)?.let { boxQuality(it.px) }
            val gridKey = ThumbnailKey.of(instanceName, presentationUuid, cue)
            ThumbnailRequest(
                url = when (source) {
                    is CueSource.PlaylistItem -> thumbnailUrl(baseUrl, source.key, cue.index, box)
                    is CueSource.Presentation -> presentationThumbnailUrl(baseUrl, source.uuid, cue.index, box)
                },
                cacheKey = ThumbnailKey.of(instanceName, presentationUuid, cue, box),
                placeholderKey = gridKey.takeIf { box != null }
            )
        }

    /** Reconnects to the saved host when no host is connected, unless [disconnect] was called since the last connect. */
    suspend fun restore() {
        currentClient()
    }

    /**
     * Forwards to the current host's client; with none connected, first reconnects to the saved host
     * unless [disconnect] was called since the last connect.
     */
    val client: ProPresenterClient = CurrentHostClient(::currentClient)

    private suspend fun currentClient(): ProPresenterClient? =
        connection.value?.client ?: restoreMutex.withLock {
            connection.value?.client ?: savedHostStore.read()?.host?.takeIf { restoreAllowed }?.let { host ->
                connect(host)
                connection.value?.client
            }
        }
}
