package com.greenfodor.ppremotece.core.data.content

import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistTreeNode
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.result.asEmptyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private typealias Read<T> = Result<T, DataError.Network>

/**
 * [ContentRepository] that caches reads per [session] key: a value is looked up under the current
 * key, and values of other keys are dropped whenever the key changes (null while disconnected).
 * Concurrent reads of one value share one request, and at most [MAX_PARALLEL_READS] run at once.
 * Each [staleSignals] emission re-reads every value that currently has a collector.
 */
class CachingContentRepository(
    private val client: ProPresenterClient,
    private val session: StateFlow<String?>,
    staleSignals: Flow<Unit>,
    private val scope: CoroutineScope
) : ContentRepository {
    private enum class Kind { PLAYLISTS, PLAYLIST, PRESENTATION }

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
                entries.values.filter { it.collectors.get() > 0 }.forEach { entry -> scope.launch { entry.read() } }
            }
        }
    }

    override fun playlists(): Flow<Read<List<PlaylistTreeNode>>> = observe(Kind.PLAYLISTS, "") { client.playlists() }

    override fun playlist(uuid: String): Flow<Read<Playlist>> = observe(Kind.PLAYLIST, uuid) { client.playlist(uuid) }

    override fun presentation(uuid: String): Flow<Read<Presentation>> =
        observe(Kind.PRESENTATION, uuid) { client.presentation(uuid) }

    override suspend fun refreshPlaylists(): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.PLAYLISTS, "")) { client.playlists() }.read().asEmptyResult()

    override suspend fun refreshPlaylist(uuid: String): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.PLAYLIST, uuid)) { client.playlist(uuid) }.read().asEmptyResult()

    override suspend fun refreshPresentation(uuid: String): EmptyResult<DataError.Network> =
        entry(Key(session.value, Kind.PRESENTATION, uuid)) { client.presentation(uuid) }.read().asEmptyResult()

    internal fun cachedEntries(): Int = entries.size

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T : Any> observe(kind: Kind, uuid: String, read: suspend () -> Read<T>): Flow<Read<T>> =
        session.flatMapLatest { current ->
            val entry = entry(Key(current, kind, uuid), read)
            entry.value
                .filterNotNull()
                .onStart {
                    entry.collectors.incrementAndGet()
                    scope.launch { entry.read() }
                }.onCompletion { entry.collectors.decrementAndGet() }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> entry(key: Key, read: suspend () -> Read<T>): Entry<T> =
        entries.getOrPut(key) { Entry(read) } as Entry<T>

    /** One cached value; a failed read replaces it only while no successful read is cached. */
    private inner class Entry<T : Any>(
        private val fetch: suspend () -> Read<T>
    ) {
        val value = MutableStateFlow<Read<T>?>(null)
        val collectors = AtomicInteger()
        private val mutex = Mutex()
        private var inFlight: Deferred<Read<T>>? = null

        suspend fun read(): Read<T> =
            mutex
                .withLock {
                    inFlight?.takeIf { it.isActive } ?: scope
                        .async { reads.withPermit { fetch() }.also(::store) }
                        .also { inFlight = it }
                }.await()

        private fun store(result: Read<T>) {
            if (result is Result.Success || value.value !is Result.Success) value.value = result
        }
    }

    private companion object {
        const val MAX_PARALLEL_READS = 4
    }
}
