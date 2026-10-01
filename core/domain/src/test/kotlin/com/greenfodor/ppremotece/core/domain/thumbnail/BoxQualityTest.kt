package com.greenfodor.ppremotece.core.domain.thumbnail

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class BoxQualityTest {
    @Test
    fun `a 1284 px box asks for 800 on the playlist route and 1400 on the presentation route`() {
        assertThat(boxQuality(ThumbnailRoute.PLAYLIST, 1284)).isEqualTo(800)
        assertThat(boxQuality(ThumbnailRoute.PRESENTATION, 1284)).isEqualTo(1400)
    }

    @Test
    fun `a 1860 px box asks for 1080 on the playlist route and 1920 on the presentation route`() {
        assertThat(boxQuality(ThumbnailRoute.PLAYLIST, 1860)).isEqualTo(1080)
        assertThat(boxQuality(ThumbnailRoute.PRESENTATION, 1860)).isEqualTo(1920)
    }

    @Test
    fun `a 2500 px box is capped at 1080 and 1920`() {
        assertThat(boxQuality(ThumbnailRoute.PLAYLIST, 2500)).isEqualTo(1080)
        assertThat(boxQuality(ThumbnailRoute.PRESENTATION, 2500)).isEqualTo(1920)
    }

    @Test
    fun `a 500 px box uses the grid request on both routes`() {
        assertThat(boxQuality(ThumbnailRoute.PLAYLIST, 500)).isNull()
        assertThat(boxQuality(ThumbnailRoute.PRESENTATION, 500)).isNull()
    }

    @Test
    fun `a box just above the grid width asks for the next multiple of 200`() {
        assertThat(boxQuality(ThumbnailRoute.PLAYLIST, 712)).isEqualTo(600)
        assertThat(boxQuality(ThumbnailRoute.PRESENTATION, 712)).isEqualTo(800)
    }

    @Test
    fun `a box that rounds to no more than the grid value or an unmeasured box uses the grid request`() {
        assertThat(boxQuality(ThumbnailRoute.PLAYLIST, 711)).isNull()
        assertThat(boxQuality(ThumbnailRoute.PRESENTATION, 600)).isNull()
        assertThat(boxQuality(ThumbnailRoute.PLAYLIST, 0)).isNull()
    }

    @Test
    fun `the grid request asks for 400 on the playlist route and 711 on the presentation route`() {
        assertThat(ThumbnailRoute.PLAYLIST.gridQuality).isEqualTo(400)
        assertThat(ThumbnailRoute.PRESENTATION.gridQuality).isEqualTo(711)
    }
}
