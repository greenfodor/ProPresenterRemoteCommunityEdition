package com.greenfodor.ppremotece.core.data.thumbnail

import android.content.Context
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import io.ktor.client.HttpClient
import okio.Path.Companion.toOkioPath

private const val DISK_CACHE_BYTES = 64L * 1024 * 1024

/**
 * The app's Coil [ImageLoader]: network reads on [httpClient] with at most
 * [LimitedFetcherFactory.MAX_IN_FLIGHT] fetches at once, thumbnails served from a larger stored
 * image of the same slide when there is one ([LargerCachedFetcherFactory]), stored bodies checked
 * against their `content-length` ([ValidatingCacheFetcherFactory]), the default memory cache and a
 * 64 MB disk cache in `cacheDir/thumbnails`. Components found by Coil's service loader are not added.
 */
@OptIn(ExperimentalCoilApi::class)
fun thumbnailImageLoader(context: Context, httpClient: HttpClient): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            add(
                LargerCachedFetcherFactory(
                    ValidatingCacheFetcherFactory(LimitedFetcherFactory(KtorNetworkFetcherFactory(httpClient)))
                )
            )
        }.diskCache {
            DiskCache
                .Builder()
                .directory(context.cacheDir.resolve("thumbnails").toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }.serviceLoaderEnabled(false)
        .build()
