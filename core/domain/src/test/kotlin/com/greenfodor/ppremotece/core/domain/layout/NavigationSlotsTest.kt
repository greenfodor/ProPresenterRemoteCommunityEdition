package com.greenfodor.ppremotece.core.domain.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class NavigationSlotsTest {
    private val three = listOf("presentation", "remote", "settings")
    private val five = three + listOf("timers", "macros")

    @Test
    fun `a rail tall enough for every destination shows them all`() {
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 192, destinations = three))
            .isEqualTo(NavigationSlots(shown = three, more = emptyList()))
    }

    @Test
    fun `a single destination that does not fit on the rail takes the more slot itself`() {
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 191, destinations = three))
            .isEqualTo(NavigationSlots(shown = three, more = emptyList()))
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 100, destinations = three))
            .isEqualTo(NavigationSlots(shown = three, more = emptyList()))
    }

    @Test
    fun `several destinations that do not fit on the rail go to more in priority order`() {
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 200, destinations = five))
            .isEqualTo(
                NavigationSlots(shown = listOf("presentation", "remote"), more = listOf("settings", "timers", "macros"))
            )
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 256, destinations = five))
            .isEqualTo(NavigationSlots(shown = three, more = listOf("timers", "macros")))
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 320, destinations = five))
            .isEqualTo(NavigationSlots(shown = five, more = emptyList()))
    }

    @Test
    fun `presentation and remote are shown on a rail too short for them`() {
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 64, destinations = five))
            .isEqualTo(
                NavigationSlots(shown = listOf("presentation", "remote"), more = listOf("settings", "timers", "macros"))
            )
    }

    @Test
    fun `a 527 dp bar shows the three destinations`() {
        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 527, destinations = three))
            .isEqualTo(NavigationSlots(shown = three, more = emptyList()))
    }

    @Test
    fun `a bar takes 80 dp per destination`() {
        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 399, destinations = five))
            .isEqualTo(NavigationSlots(shown = three, more = listOf("timers", "macros")))
    }

    @Test
    fun `a bar holds at most five slots`() {
        val seven = five + listOf("props", "messages")

        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 1173, destinations = seven))
            .isEqualTo(NavigationSlots(shown = seven.take(4), more = seven.drop(4)))
        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 1173, destinations = five))
            .isEqualTo(NavigationSlots(shown = five, more = emptyList()))
    }
}
