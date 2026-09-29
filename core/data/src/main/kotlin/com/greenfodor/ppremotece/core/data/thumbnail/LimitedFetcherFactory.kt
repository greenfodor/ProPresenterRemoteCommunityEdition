package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Wraps the fetchers of [delegate] so that at most [maxInFlight] fetches run at once. Requests
 * served from the memory cache never reach a fetcher.
 */
class LimitedFetcherFactory<T : Any>(
    private val delegate: Fetcher.Factory<T>,
    maxInFlight: Int = MAX_IN_FLIGHT
) : Fetcher.Factory<T> {
    private val permits = Semaphore(maxInFlight)

    override fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher? =
        delegate.create(data, options, imageLoader)?.let { LimitedFetcher(it, permits) }

    companion object {
        const val MAX_IN_FLIGHT = 4
    }
}

/** Runs [delegate]'s fetch while holding one of [permits]. */
class LimitedFetcher(
    private val delegate: Fetcher,
    private val permits: Semaphore
) : Fetcher {
    override suspend fun fetch(): FetchResult? = permits.withPermit { delegate.fetch() }
}
