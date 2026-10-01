package com.greenfodor.ppremotece.core.domain.thumbnail

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class BoxQualityTest {
    @Test
    fun `a 1284 px box asks for q 800, sent as 800 on the playlist route and 1422 on the presentation route`() {
        assertThat(boxQuality(1284)).isEqualTo(800)
        assertThat(ThumbnailRoute.PLAYLIST.query(800)).isEqualTo(800)
        assertThat(ThumbnailRoute.PRESENTATION.query(800)).isEqualTo(1422)
    }

    @Test
    fun `a 1860 px box asks for q 1080, sent as 1080 and 1920`() {
        assertThat(boxQuality(1860)).isEqualTo(1080)
        assertThat(ThumbnailRoute.PLAYLIST.query(1080)).isEqualTo(1080)
        assertThat(ThumbnailRoute.PRESENTATION.query(1080)).isEqualTo(1920)
    }

    @Test
    fun `a 2500 px box is capped at q 1080`() {
        assertThat(boxQuality(2500)).isEqualTo(1080)
    }

    @Test
    fun `a 500 px box uses the grid request`() {
        assertThat(boxQuality(500)).isNull()
    }

    @Test
    fun `a box just above the grid width asks for the next multiple of 200`() {
        assertThat(boxQuality(712)).isEqualTo(600)
    }

    @Test
    fun `a box at the grid width or an unmeasured box uses the grid request`() {
        assertThat(boxQuality(711)).isNull()
        assertThat(boxQuality(0)).isNull()
    }

    @Test
    fun `the grid request is sent as 400 on the playlist route and 711 on the presentation route`() {
        assertThat(ThumbnailRoute.PLAYLIST.query(null)).isEqualTo(400)
        assertThat(ThumbnailRoute.PRESENTATION.query(null)).isEqualTo(711)
    }

    @Test
    fun `every box quality is sent within the allowed ranges`() {
        assertThat(boxQualities().map { ThumbnailRoute.PLAYLIST.query(it) }).containsExactly(600, 800, 1000, 1080)
        assertThat(boxQualities().map { ThumbnailRoute.PRESENTATION.query(it) }).containsExactly(1066, 1422, 1777, 1920)
    }

    @Test
    fun `box qualities list every value boxQuality gives`() {
        assertThat(boxQualities()).isEqualTo((1..4000).mapNotNull { boxQuality(it) }.distinct())
    }
}
