package com.greenfodor.ppremotece.core.domain.layout

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class NavigationSlotsTest {
    private val three = listOf("presentation", "remote", "settings")
    private val five = listOf("presentation", "remote", "macros", "timers", "settings")

    private fun rail(slots: Int, destinations: List<String>) =
        navigationSlots(NavigationLayout.RAIL, availableDp = slots * RAIL_SLOT_DP, destinations = destinations)

    @Test
    fun `a rail with a slot for every destination shows them all`() {
        assertThat(rail(slots = 5, destinations = five)).isEqualTo(NavigationSlots(shown = five, more = emptyList()))
        assertThat(rail(slots = 5, destinations = three)).isEqualTo(NavigationSlots(shown = three, more = emptyList()))
        assertThat(rail(slots = 4, destinations = three)).isEqualTo(NavigationSlots(shown = three, more = emptyList()))
        assertThat(rail(slots = 3, destinations = three)).isEqualTo(NavigationSlots(shown = three, more = emptyList()))
    }

    @Test
    fun `a rail one slot short gives the last slot to more`() {
        assertThat(rail(slots = 4, destinations = five))
            .isEqualTo(NavigationSlots(shown = five.take(3), more = listOf("timers", "settings")))
        assertThat(rail(slots = 2, destinations = three))
            .isEqualTo(NavigationSlots(shown = listOf("presentation"), more = listOf("remote", "settings")))
    }

    @Test
    fun `a short rail lists the rest under more in priority order`() {
        assertThat(rail(slots = 3, destinations = five))
            .isEqualTo(
                NavigationSlots(shown = listOf("presentation", "remote"), more = listOf("macros", "timers", "settings"))
            )
        assertThat(rail(slots = 2, destinations = five))
            .isEqualTo(NavigationSlots(shown = listOf("presentation"), more = five.drop(1)))
    }

    @Test
    fun `a single rail slot holds more alone`() {
        assertThat(rail(slots = 1, destinations = five)).isEqualTo(NavigationSlots(shown = emptyList(), more = five))
        assertThat(rail(slots = 1, destinations = three)).isEqualTo(NavigationSlots(shown = emptyList(), more = three))
    }

    @Test
    fun `a rail slot is 64 dp`() {
        assertThat(navigationSlots(NavigationLayout.RAIL, availableDp = 319, destinations = five))
            .isEqualTo(NavigationSlots(shown = five.take(3), more = listOf("timers", "settings")))
    }

    @Test
    fun `a 527 dp bar shows all five destinations`() {
        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 527, destinations = five))
            .isEqualTo(NavigationSlots(shown = five, more = emptyList()))
    }

    @Test
    fun `a bar holds at most five slots`() {
        val six = five + "props"

        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 1173, destinations = six))
            .isEqualTo(NavigationSlots(shown = six.take(4), more = listOf("settings", "props")))
    }

    @Test
    fun `a 358 dp bar gives the fourth slot to more`() {
        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 358, destinations = five))
            .isEqualTo(NavigationSlots(shown = five.take(3), more = listOf("timers", "settings")))
    }

    @Test
    fun `a three slot bar lists the rest under more`() {
        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 240, destinations = five))
            .isEqualTo(
                NavigationSlots(shown = listOf("presentation", "remote"), more = listOf("macros", "timers", "settings"))
            )
        assertThat(navigationSlots(NavigationLayout.BAR, availableDp = 240, destinations = three))
            .isEqualTo(NavigationSlots(shown = three, more = emptyList()))
    }
}
