package com.greenfodor.ppremotece.core.domain.props

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class PropThumbnailTest {
    @Test
    fun `the width is the tile width rounded up to 200, 400 or 600`() {
        assertThat(listOf(1, 200, 201, 400, 401, 600, 900).map(::propThumbnailWidth))
            .containsExactly(200, 200, 400, 400, 600, 600, 600)
    }

    @Test
    fun `the key names the host, the prop and the width`() {
        assertThat(propThumbnailKey("Host 01", "p-0", 400)).isEqualTo("prop:Host 01:p-0:w400")
    }
}
