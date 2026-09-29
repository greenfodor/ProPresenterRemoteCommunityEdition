package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.IOException

/** [ThumbnailCache] over the memory and disk caches of [imageLoader]; disk errors are ignored. */
class CoilThumbnailCache(
    private val imageLoader: ImageLoader
) : ThumbnailCache {
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            imageLoader.memoryCache?.clear()
            try {
                imageLoader.diskCache?.clear()
            } catch (_: IOException) {
                // Entries that could not be deleted stay in the disk cache.
            }
        }
    }

    override suspend fun remove(keys: Collection<String>) {
        withContext(Dispatchers.IO) {
            keys.forEach { key ->
                imageLoader.memoryCache?.remove(MemoryCache.Key(key))
                try {
                    imageLoader.diskCache?.remove(key)
                } catch (_: IOException) {
                    // An entry that could not be deleted stays in the disk cache.
                }
            }
        }
    }
}
