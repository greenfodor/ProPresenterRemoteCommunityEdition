package com.greenfodor.ppremotece.core.designsystem.ui

import android.content.ContextWrapper
import assertk.assertThat
import assertk.assertions.isEqualTo
import coil3.request.CachePolicy
import org.junit.jupiter.api.Test

class PropThumbnailTest {
    private val context = ContextWrapper(null)

    @Test
    fun `a prop thumbnail is kept in memory under its key and never on disk`() {
        val request = propThumbnailImageRequest(context, URL, KEY, refresh = false)

        assertThat(request.data).isEqualTo(URL)
        assertThat(request.memoryCacheKey).isEqualTo(KEY)
        assertThat(request.memoryCachePolicy).isEqualTo(CachePolicy.ENABLED)
        assertThat(request.diskCachePolicy).isEqualTo(CachePolicy.DISABLED)
    }

    @Test
    fun `a refreshed prop thumbnail is read again and written over its memory entry`() {
        val request = propThumbnailImageRequest(context, URL, KEY, refresh = true)

        assertThat(request.memoryCachePolicy).isEqualTo(CachePolicy.WRITE_ONLY)
        assertThat(request.diskCachePolicy).isEqualTo(CachePolicy.DISABLED)
    }

    private companion object {
        const val URL = "http://192.0.2.14:60113/v1/prop/p-0/thumbnail?quality=400"
        const val KEY = "prop:Host 01:p-0:w400"
    }
}
