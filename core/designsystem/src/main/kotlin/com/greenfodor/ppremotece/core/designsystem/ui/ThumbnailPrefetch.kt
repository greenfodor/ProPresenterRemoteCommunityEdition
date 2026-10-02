package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest

/**
 * Enqueues each of [requests] on the app's image loader, under the cache keys [SlideThumbnail]
 * uses, whenever the list changes. Enqueued loads run to the end after the list changes or this
 * leaves the composition.
 */
@Composable
fun ThumbnailPrefetch(requests: List<ThumbnailRequest>) {
    val context = LocalPlatformContext.current
    LaunchedEffect(context, requests) {
        requests.forEach {
            SingletonImageLoader.get(context).enqueue(thumbnailImageRequest(context, it.url, it.cacheKey))
        }
    }
}
