package com.greenfodor.ppremotece.core.data.thumbnail

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailKey
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class LargerCachedFetcherTest {
    @TempDir
    lateinit var dir: Path

    private val cache by lazy { DiskCache.Builder().directory(dir.toOkioPath()).build() }
    private val fetches = AtomicInteger()
    private val network = Fetcher {
        fetches.incrementAndGet()
        null
    }

    @Test
    fun `a larger image of the slide on disk is served without a fetch`() = runBlocking {
        store("k:q1000", "q1000")

        val result = LargerCachedFetcher(network, cache, ThumbnailKey.largerKeys("k:q800")).fetch()

        assertThat(result).isNotNull().isInstanceOf<SourceFetchResult>()
        assertThat((result as SourceFetchResult).dataSource).isEqualTo(DataSource.DISK)
        assertThat(result.text()).isEqualTo("q1000")
        assertThat(fetches.get()).isEqualTo(0)
    }

    @Test
    fun `the smallest larger image is served`() = runBlocking {
        store("k:q1080", "q1080")
        store("k:q600", "q600")

        val result = LargerCachedFetcher(network, cache, ThumbnailKey.largerKeys("k")).fetch()

        assertThat((result as SourceFetchResult).text()).isEqualTo("q600")
    }

    @Test
    fun `without a larger image on disk the image is fetched`() = runBlocking {
        store("k:q600", "q600")

        val result = LargerCachedFetcher(network, cache, ThumbnailKey.largerKeys("k:q800")).fetch()

        assertThat(result).isNull()
        assertThat(fetches.get()).isEqualTo(1)
    }

    @Test
    fun `only presentation route thumbnails look for a larger image`() {
        assertThat(isPresentationThumbnailUrl("http://192.0.2.14:60113/v1/presentation/p-1/thumbnail/3?quality=711"))
            .isTrue()
        assertThat(isPresentationThumbnailUrl("http://192.0.2.14:60113/v1/playlist/pl/1/thumbnail/3?quality=400"))
            .isFalse()
        assertThat(isPresentationThumbnailUrl("http://192.0.2.14:60113/v1/presentation/p-1")).isFalse()
    }

    private fun store(key: String, content: String) {
        val editor = checkNotNull(cache.openEditor(key))
        cache.fileSystem.write(editor.data) { writeUtf8(content) }
        editor.commit()
    }

    private fun SourceFetchResult.text(): String = source.use { it.source().readUtf8() }
}
