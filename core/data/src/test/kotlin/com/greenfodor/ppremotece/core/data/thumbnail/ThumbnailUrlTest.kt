package com.greenfodor.ppremotece.core.data.thumbnail

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import org.junit.jupiter.api.Test

class ThumbnailUrlTest {
    private val item = PlaylistItemKey("6f760dbf-04b9-46f2-9bb3-33eeea6a6d90", 1)

    @Test
    fun `thumbnail url is the playlist item cue route at quality 400`() {
        assertThat(thumbnailUrl("http://192.0.2.14:60113", item, cueIndex = 3, boxQuality = null))
            .isEqualTo(
                "http://192.0.2.14:60113/v1/playlist/6f760dbf-04b9-46f2-9bb3-33eeea6a6d90/1/thumbnail/3?quality=400"
            )
    }

    @Test
    fun `a prop thumbnail url asks for its width as the quality`() {
        assertThat(propThumbnailUrl("http://192.0.2.14:60113/", "p-0", width = 400))
            .isEqualTo("http://192.0.2.14:60113/v1/prop/p-0/thumbnail?quality=400")
    }

    @Test
    fun `a box quality replaces 400`() {
        assertThat(thumbnailUrl("http://192.0.2.14:60113", item, cueIndex = 3, boxQuality = 800))
            .isEqualTo(
                "http://192.0.2.14:60113/v1/playlist/6f760dbf-04b9-46f2-9bb3-33eeea6a6d90/1/thumbnail/3?quality=800"
            )
    }

    @Test
    fun `the presentation thumbnail url asks for 711 px for the grid and the mapped width for a box`() {
        assertThat(presentationThumbnailUrl("http://192.0.2.14:60113", "p-1", cueIndex = 3, boxQuality = null))
            .isEqualTo("http://192.0.2.14:60113/v1/presentation/p-1/thumbnail/3?quality=711")
        assertThat(presentationThumbnailUrl("http://192.0.2.14:60113/", "p-1", cueIndex = 3, boxQuality = 800))
            .isEqualTo("http://192.0.2.14:60113/v1/presentation/p-1/thumbnail/3?quality=1422")
        assertThat(presentationThumbnailUrl("http://192.0.2.14:60113", "a b/c", cueIndex = 0, boxQuality = 1080))
            .isEqualTo("http://192.0.2.14:60113/v1/presentation/a%20b%2Fc/thumbnail/0?quality=1920")
    }

    @Test
    fun `a trailing slash on the base url is not doubled`() {
        assertThat(thumbnailUrl("http://192.0.2.14:60113/", item, cueIndex = 0, boxQuality = null))
            .isEqualTo(
                "http://192.0.2.14:60113/v1/playlist/6f760dbf-04b9-46f2-9bb3-33eeea6a6d90/1/thumbnail/0?quality=400"
            )
    }

    @Test
    fun `the playlist uuid is encoded as a path segment`() {
        assertThat(
            thumbnailUrl("http://192.0.2.14:60113", PlaylistItemKey("a b/c", 2), cueIndex = 5, boxQuality = null)
        )
            .isEqualTo("http://192.0.2.14:60113/v1/playlist/a%20b%2Fc/2/thumbnail/5?quality=400")
    }
}
