package com.greenfodor.ppremotece.core.domain.stage

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class StageLayoutThumbnailTest {
    @Test
    fun `the width asked is the box width rounded up to the next 200`() {
        assertThat(listOf(1, 200, 201, 400, 401, 600, 601, 800).map(::stageLayoutThumbnailWidth))
            .isEqualTo(listOf(200, 200, 400, 400, 600, 600, 800, 800))
    }

    @Test
    fun `the width asked is at least 200 and at most 800`() {
        assertThat(stageLayoutThumbnailWidth(0)).isEqualTo(200)
        assertThat(stageLayoutThumbnailWidth(801)).isEqualTo(800)
        assertThat(stageLayoutThumbnailWidth(3_000)).isEqualTo(800)
    }

    @Test
    fun `the cache key names the host, the layout and the width`() {
        assertThat(stageLayoutThumbnailKey("Host 01", "l-0", 600)).isEqualTo("stage-layout:Host 01:l-0:w600")
    }
}
