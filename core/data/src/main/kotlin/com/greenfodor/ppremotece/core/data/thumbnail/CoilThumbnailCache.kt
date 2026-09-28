package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ThumbnailCache] over the memory and disk caches of [imageLoader]. */
class CoilThumbnailCache(
    private val imageLoader: ImageLoader
) : ThumbnailCache {
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        }
    }

    override suspend fun remove(keys: Collection<String>) {
        withContext(Dispatchers.IO) {
            keys.forEach { key ->
                imageLoader.memoryCache?.remove(MemoryCache.Key(key))
                imageLoader.diskCache?.remove(key)
            }
        }
    }
}
