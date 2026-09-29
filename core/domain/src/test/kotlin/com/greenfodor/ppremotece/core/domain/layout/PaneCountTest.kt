package com.greenfodor.ppremotece.core.domain.layout

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class PaneCountTest {
    @Test
    fun `two panes from 840 dp`() {
        assertThat(listOf(599, 600, 839, 840, 1173).map(::paneCount)).containsExactly(1, 1, 1, 2, 2)
    }

    @Test
    fun `width classes break at 600 and 840 dp`() {
        assertThat(listOf(527, 599, 600, 777, 839, 840, 1173).map(::widthClassOf)).containsExactly(
            WidthClass.COMPACT,
            WidthClass.COMPACT,
            WidthClass.MEDIUM,
            WidthClass.MEDIUM,
            WidthClass.MEDIUM,
            WidthClass.EXPANDED,
            WidthClass.EXPANDED
        )
    }
}
