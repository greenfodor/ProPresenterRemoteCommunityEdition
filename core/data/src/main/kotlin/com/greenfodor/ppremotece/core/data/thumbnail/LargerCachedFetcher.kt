package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailKey

/**
 * Wraps the fetchers of [delegate]: a presentation route thumbnail is served from the disk cache
 * under a larger box key of the same slide ([ThumbnailKey.largerKeys]) when one is stored, and
 * fetched by [delegate] otherwise. Other requests go to [delegate] unchanged.
 */
class LargerCachedFetcherFactory(
    private val delegate: Fetcher.Factory<Uri>
) : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        val fetcher = delegate.create(data, options, imageLoader) ?: return null
        val key = options.diskCacheKey
        val diskCache = imageLoader.diskCache
        return if (key != null && diskCache != null && isPresentationThumbnailUrl(data.toString())) {
            LargerCachedFetcher(fetcher, diskCache, ThumbnailKey.largerKeys(key))
        } else {
            fetcher
        }
    }
}

/** Serves the first of [largerKeys] stored in [diskCache]; runs [delegate] when none is stored. */
class LargerCachedFetcher(
    private val delegate: Fetcher,
    private val diskCache: DiskCache,
    private val largerKeys: List<String>
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        largerKeys.forEach { key ->
            diskCache.openSnapshot(key)?.let { snapshot ->
                return SourceFetchResult(
                    source = ImageSource(snapshot.data, diskCache.fileSystem, key, snapshot, null),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            }
        }
        return delegate.fetch()
    }
}
