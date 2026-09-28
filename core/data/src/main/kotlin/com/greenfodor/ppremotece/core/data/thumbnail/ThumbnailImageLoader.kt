package com.greenfodor.ppremotece.core.data.thumbnail

import android.content.Context
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import okio.Path.Companion.toOkioPath

private const val DISK_CACHE_BYTES = 64L * 1024 * 1024

/**
 * The app's Coil [ImageLoader]: network reads on [httpClient], at most
 * [ThumbnailConcurrencyInterceptor.MAX_IN_FLIGHT] requests at once, the default memory cache, a
 * 64 MB disk cache in `cacheDir/thumbnails` and no crossfade.
 */
@OptIn(ExperimentalCoilApi::class)
fun thumbnailImageLoader(context: Context, httpClient: HttpClient): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            add(ThumbnailConcurrencyInterceptor())
            add(KtorNetworkFetcherFactory(httpClient))
        }.diskCache {
            DiskCache
                .Builder()
                .directory(context.cacheDir.resolve("thumbnails").toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }.crossfade(false)
        .build()
