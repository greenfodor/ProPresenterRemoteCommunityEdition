package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Lets at most [maxInFlight] image requests proceed at once. */
class ThumbnailConcurrencyInterceptor(
    maxInFlight: Int = MAX_IN_FLIGHT
) : Interceptor {
    private val permits = Semaphore(maxInFlight)

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult = permits.withPermit { chain.proceed() }

    companion object {
        const val MAX_IN_FLIGHT = 4
    }
}
