package com.greenfodor.ppremotece.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import okhttp3.Dispatcher

object HttpClientFactory {
    private const val CONNECT_TIMEOUT_MS = 3_000L
    private const val REQUEST_TIMEOUT_MS = 5_000L
    private const val MAX_REQUESTS_PER_HOST = 8

    fun create(engine: HttpClientEngine = okHttpEngine()): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(ProPresenterJson)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }

    private fun okHttpEngine(): HttpClientEngine =
        OkHttp.create {
            config {
                dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
            }
        }
}
