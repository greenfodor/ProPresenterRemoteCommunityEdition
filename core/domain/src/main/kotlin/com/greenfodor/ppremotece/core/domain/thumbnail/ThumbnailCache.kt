package com.greenfodor.ppremotece.core.domain.thumbnail

/** Stored slide thumbnails, addressed by [ThumbnailKey]. */
interface ThumbnailCache {
    suspend fun clear()

    suspend fun remove(keys: Collection<String>)
}
