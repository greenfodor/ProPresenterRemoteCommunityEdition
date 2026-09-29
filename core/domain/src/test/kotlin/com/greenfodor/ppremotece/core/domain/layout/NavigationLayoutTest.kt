package com.greenfodor.ppremotece.core.domain.layout

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class NavigationLayoutTest {
    @Test
    fun `bar below 600 dp, rail from 600 dp`() {
        assertThat(listOf(599, 600, 839, 840).map(::navigationLayout)).containsExactly(
            NavigationLayout.BAR,
            NavigationLayout.RAIL,
            NavigationLayout.RAIL,
            NavigationLayout.RAIL
        )
    }
}
