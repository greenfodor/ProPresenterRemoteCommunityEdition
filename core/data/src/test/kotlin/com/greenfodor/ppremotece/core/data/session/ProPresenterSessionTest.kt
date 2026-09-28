package com.greenfodor.ppremotece.core.data.session

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.greenfodor.ppremotece.core.data.network.FakeProPresenter
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.domain.model.ProPresenterHost
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProPresenterSessionTest {
    @StartStop
    private val server = MockWebServer()

    private val cache = FakeThumbnailCache()
    private val savedHosts = FakeSavedHosts()
    private lateinit var session: ProPresenterSession

    @BeforeEach
    fun setUp() {
        server.dispatcher = FakeProPresenter()
        session = ProPresenterSession(HttpClientFactory.create(), savedHosts, cache)
    }

    @Test
    fun `every successful connect clears the thumbnail cache`() = runBlocking {
        val host = ProPresenterHost(name = "Host 01", address = server.hostName, port = server.port)

        assertThat(session.connect(host)).isInstanceOf<Result.Success<*>>()
        assertThat(session.connect(host)).isInstanceOf<Result.Success<*>>()

        assertThat(cache.clears).isEqualTo(2)
    }

    @Test
    fun `a failed connect keeps the thumbnail cache`() = runBlocking {
        val unreachable = ProPresenterHost(name = "Host 01", address = "127.0.0.1", port = 1)

        assertThat(session.connect(unreachable)).isInstanceOf<Result.Failure<*>>()

        assertThat(cache.clears).isEqualTo(0)
    }

    private class FakeThumbnailCache : ThumbnailCache {
        var clears = 0
        val removed = mutableListOf<String>()

        override suspend fun clear() {
            clears++
        }

        override suspend fun remove(keys: Collection<String>) {
            removed += keys
        }
    }

    private class FakeSavedHosts : SavedHosts {
        var host: ProPresenterHost? = null

        override suspend fun read(): ProPresenterHost? = host

        override suspend fun save(host: ProPresenterHost) {
            this.host = host
        }

        override suspend fun clear() {
            host = null
        }
    }
}
