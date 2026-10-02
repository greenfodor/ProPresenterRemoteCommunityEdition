package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.ImageResult

/** An [ImageLoader] that only provides [diskCache] and an optional [memoryCache] to fetcher factories. */
class DiskOnlyImageLoader(
    override val diskCache: DiskCache?,
    override val memoryCache: MemoryCache? = null
) : ImageLoader {
    override val defaults: ImageRequest.Defaults get() = ImageRequest.Defaults.DEFAULT
    override val components: ComponentRegistry get() = ComponentRegistry()

    override fun enqueue(request: ImageRequest): Disposable = error("Not used")

    override suspend fun execute(request: ImageRequest): ImageResult = error("Not used")

    override fun shutdown() = Unit

    override fun newBuilder(): ImageLoader.Builder = error("Not used")
}
