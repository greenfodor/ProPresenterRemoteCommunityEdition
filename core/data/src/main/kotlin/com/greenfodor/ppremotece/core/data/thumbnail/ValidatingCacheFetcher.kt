package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.ImageLoader
import coil3.Uri
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.Options
import okio.IOException

private const val ATTEMPTS = 2

/** Wraps the fetchers of [delegate] in a [ValidatingCacheFetcher] for requests with a disk cache key. */
class ValidatingCacheFetcherFactory(
    private val delegate: Fetcher.Factory<Uri>
) : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        val fetcher = delegate.create(data, options, imageLoader) ?: return null
        val key = options.diskCacheKey
        val diskCache = imageLoader.diskCache
        return if (key != null && diskCache != null) {
            ValidatingCacheFetcher(fetcher, diskCache, imageLoader.memoryCache, key)
        } else {
            fetcher
        }
    }
}

/**
 * Runs [delegate] for the entry stored under [key]. An entry whose body is not as long as its
 * stored `content-length` ([holdsWholeBody]) is removed from [diskCache] and [memoryCache] before
 * [delegate] runs; when [delegate] stores such an entry, it is removed and [delegate] runs once
 * more, and a second one ends the fetch with an [IOException].
 */
class ValidatingCacheFetcher(
    private val delegate: Fetcher,
    private val diskCache: DiskCache,
    private val memoryCache: MemoryCache?,
    private val key: String
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (!storedEntryWhole()) remove()
        repeat(ATTEMPTS) {
            val result = delegate.fetch()
            if (storedEntryWhole()) return result
            (result as? SourceFetchResult)?.source?.close()
            remove()
        }
        throw IOException("The body stored under $key is shorter than its content-length")
    }

    private fun storedEntryWhole(): Boolean = diskCache.openSnapshot(key)?.use { diskCache.holdsWholeBody(it) } ?: true

    private fun remove() {
        diskCache.remove(key)
        memoryCache?.remove(MemoryCache.Key(key))
    }
}
