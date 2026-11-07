package com.greenfodor.ppremotece.feature.remote

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class CueCentringTest {
    private val viewportHeight = 1800
    private val cueHeight = 400

    /** Where a cue scrolled to with [offset] has its centre, from the top of a viewport whose content padding is [padding]. */
    private fun centreFromTop(offset: Int, padding: Int) = padding - offset + cueHeight / 2

    @Test
    fun `a cue's centre is put at the viewport's centre`() {
        val offset = centringOffset(viewportStart = 0, viewportEnd = viewportHeight, itemSize = cueHeight)

        assertThat(offset).isEqualTo(-(viewportHeight - cueHeight) / 2)
        assertThat(centreFromTop(offset, padding = 0)).isEqualTo(viewportHeight / 2)
    }

    @Test
    fun `the list's content padding is counted`() {
        val padding = 22
        val offset =
            centringOffset(viewportStart = -padding, viewportEnd = viewportHeight - padding, itemSize = cueHeight)

        assertThat(centreFromTop(offset, padding)).isEqualTo(viewportHeight / 2)
    }

    @Test
    fun `a cue taller than the viewport is centred with a positive offset`() {
        assertThat(centringOffset(viewportStart = 0, viewportEnd = 300, itemSize = 500)).isEqualTo(100)
    }
}
