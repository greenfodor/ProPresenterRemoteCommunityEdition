package com.greenfodor.ppremotece.core.domain.layout

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class PaneCountTest {
    @Test
    fun `two panes from 840 dp`() {
        assertThat(listOf(599, 600, 839, 840, 1173).map(::paneCount)).containsExactly(1, 1, 1, 2, 2)
    }
}
