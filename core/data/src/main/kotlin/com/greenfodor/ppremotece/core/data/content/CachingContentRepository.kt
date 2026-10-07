package com.greenfodor.ppremotece.core.data.content

import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.live.PlaylistNotFoundException
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.asEmptyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private typealias Read<T> = Result<T, DataError.Network>

/**
 * [ContentRepository] that caches reads per [session] key: a value is looked up under the current
 * key, and values of other keys are dropped whenever the key changes. While the key is null the
 * flows emit nothing and call [restore]. A new collection drops a cached failure before it reads.
 * A read that throws counts as a failed read with [DataError.Network.UNKNOWN].
 * Concurrent reads of one value share one request, and at most [MAX_PARALLEL_READS] run at once.
 * Each [staleSignals] emission re-reads every value that currently has a collector.
 *
 * A playlist that has a collector also keeps its change connection open
 * ([ProPresenterClient.playlistChanges]): each change re-reads the playlist and then calls
 * [onPlaylistChanged]; that read starts after any read already in flight. The connection is
 * reopened, with a re-read, on each [staleSignals] emission and [changesRetryDelay] after it ended,
 * once [isStreamConnected]; it is not reopened for a playlist ProPresenter does not know, and it is
 * closed when the last collector leaves.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class CachingContentRepository(
    private val client: ProPresenterClient,
    private val session: StateFlow<String?>,
    staleSignals: Flow<Unit>,
    private val scope: CoroutineScope,
    private val restore: suspend () -> Unit,
    private val onPlaylistChanged: () -> Unit = {},
    private val isStreamConnected: () -> Boolean = { true },
    private val changesRetryDelay: Duration = 2.seconds
) : ContentRepository {
    private enum class Kind { PLAYLISTS, PLAYLIST, PRESENTATION, LIBRARIES, LIBRARY }

    private data class Key(
        val session: String?,
        val kind: Kind,
        val uuid: String
    )

    private val entries = ConcurrentHashMap<Key, Entry<*>>()
    private val reads = Semaphore(MAX_PARALLEL_READS)

    init {
        scope.launch {
            session.collect { current -> entries.keys.removeAll { it.session != current } }
        }
        scope.launch {
            staleSignals.collect {
                entries.values.filter { it.collectors.get() > 0 }.forEach { entry ->
                    if (!entry.rewatch()) scope.launch { entry.read() }
                }
            }
        }
    }

    override fun playlists(): Flow<Read<List<PlaylistTreeNode>>> = observe(Kind.PLAYLISTS, "") { client.playlists() }

    override fun playlist(uuid: String): Flow<Read<Playlist>> =
        observe(Kind.PLAYLIST, uuid, watch = { readFirst -> watchPlaylist(uuid, this, readFirst) }) {
            client.playlist(uuid)
        }

    override fun presentation(uuid: String): Flow<Read<Presentation>> =
        observe(Kind.PRESENTATION, uuid) { client.presentation(uuid) }

    override fun libraries(): Flow<Read<List<Library>>> = observe(Kind.LIBRARIES, "") { client.libraries() }

    override fun library(uuid: String): Flow<Read<List<LibraryEntry>>> =
        observe(Kind.LIBRARY, uuid) { client.library(uuid) }

    override suspend fun refreshLibraries(): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.LIBRARIES, "")) { client.libraries() }.read().asEmptyResult()

    override suspend fun refreshLibrary(uuid: String): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.LIBRARY, uuid)) { client.library(uuid) }.read().asEmptyResult()

    override suspend fun refreshPlaylists(): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.PLAYLISTS, "")) { client.playlists() }.read().asEmptyResult()

    override suspend fun refreshPlaylist(uuid: String): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.PLAYLIST, uuid)) { client.playlist(uuid) }.read().asEmptyResult()

    override suspend fun refreshPresentation(uuid: String): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.PRESENTATION, uuid)) { client.presentation(uuid) }.read().asEmptyResult()

    internal fun cachedEntries(): Int = entries.size

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T : Any> observe(
        kind: Kind,
        uuid: String,
        watch: (suspend Entry<T>.(readFirst: Boolean) -> Unit)? = null,
        read: suspend () -> Read<T>
    ): Flow<Read<T>> =
        session.flatMapLatest { current ->
            if (current == null) return@flatMapLatest flow { restore() }
            val entry = entry(Key(current, kind, uuid), read)
            entry.value
                .filterNotNull()
                .onStart {
                    entry.join(watch)
                    entry.dropFailure()
                    scope.launch { entry.read() }
                }.onCompletion { entry.leave() }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> entry(key: Key, read: suspend () -> Read<T>): Entry<T> =
        entries.getOrPut(key) { Entry(read) } as Entry<T>

    /**
     * Keeps the change connection of playlist [uuid] open: each change re-reads [entry] and calls
     * [onPlaylistChanged]. With [readFirst], and at each reopening, the playlist is read again.
     */
    private suspend fun watchPlaylist(uuid: String, entry: Entry<Playlist>, readFirst: Boolean) {
        var read = readFirst
        while (true) {
            if (read) scope.launch { entry.read() }
            val failure = runCatching {
                client.playlistChanges(uuid).collect {
                    entry.readAgain()
                    onPlaylistChanged()
                }
            }.exceptionOrNull()
            if (failure is CancellationException) throw failure
            if (failure is PlaylistNotFoundException) return
            delay(changesRetryDelay)
            while (!isStreamConnected()) delay(changesRetryDelay)
            read = true
        }
    }

    /**
     * One cached value; a failed read replaces it only while no successful read is cached. A read
     * started after a value is stored always makes a new request.
     */
    private inner class Entry<T : Any>(
        private val fetch: suspend () -> Read<T>
    ) {
        val value = MutableStateFlow<Read<T>?>(null)
        val collectors = AtomicInteger()
        private val mutex = Mutex()
        private var inFlight: Deferred<Read<T>>? = null
        private var watch: (suspend Entry<T>.(readFirst: Boolean) -> Unit)? = null
        private var watcher: Job? = null

        /** Counts a new collector; the first one starts [watching], when given. */
        @Synchronized
        fun join(watching: (suspend Entry<T>.(readFirst: Boolean) -> Unit)?) {
            if (collectors.incrementAndGet() == 1 && watching != null) {
                watch = watching
                watcher = scope.launch { watching(false) }
            }
        }

        /** Counts a collector that left; the last one stops the watch. */
        @Synchronized
        fun leave() {
            if (collectors.decrementAndGet() == 0) {
                watcher?.cancel()
                watcher = null
            }
        }

        /** Starts the watch again, reading first; false when this entry has none running. */
        @Synchronized
        fun rewatch(): Boolean {
            val watching = watch
            if (watcher == null || watching == null) return false
            watcher?.cancel()
            watcher = scope.launch { watching(true) }
            return true
        }

        suspend fun read(): Read<T> =
            mutex
                .withLock {
                    inFlight?.takeIf { it.isActive } ?: scope
                        .async {
                            val result = reads.withPermit { fetchOrFailure() }
                            mutex.withLock {
                                inFlight = null
                                store(result)
                            }
                            result
                        }.also { inFlight = it }
                }.await()

        /** A read that starts after the one in flight, if any, has finished. */
        suspend fun readAgain(): Read<T> {
            mutex.withLock { inFlight }?.join()
            return read()
        }

        private suspend fun fetchOrFailure(): Read<T> =
            runCatching { fetch() }.getOrElse { error ->
                if (error is CancellationException) throw error
                Result.Failure(DataError.Network.UNKNOWN)
            }

        fun dropFailure() {
            value.update { it as? Result.Success }
        }

        private fun store(result: Read<T>) {
            if (result is Result.Success || value.value !is Result.Success) value.value = result
        }
    }

    private companion object {
        const val MAX_PARALLEL_READS = 4
    }
}
