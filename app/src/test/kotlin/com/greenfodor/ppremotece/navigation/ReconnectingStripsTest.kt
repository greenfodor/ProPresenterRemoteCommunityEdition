package com.greenfodor.ppremotece.navigation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import org.junit.jupiter.api.Test

class ReconnectingStripsTest {
    @Test
    fun `two panes on the Presentation tab show the shell's strip and no pane strip`() {
        assertThat(reconnectingStrips(twoPanes = true, reconnecting = true, tab = ShellTab.PRESENTATION))
            .isEqualTo(ReconnectingStrips(shell = true, panes = false))
    }

    @Test
    fun `one pane shows the pane strip and no shell strip`() {
        assertThat(reconnectingStrips(twoPanes = false, reconnecting = true, tab = ShellTab.PRESENTATION))
            .isEqualTo(ReconnectingStrips(shell = false, panes = true))
    }

    @Test
    fun `another tab never shows the shell's strip`() {
        ShellTab.entries.filter { it != ShellTab.PRESENTATION }.forEach { tab ->
            assertThat(reconnectingStrips(twoPanes = true, reconnecting = true, tab = tab).shell).isEqualTo(false)
        }
    }

    @Test
    fun `nothing shows while connected`() {
        listOf(true, false).forEach { twoPanes ->
            assertThat(reconnectingStrips(twoPanes, reconnecting = false, tab = ShellTab.PRESENTATION))
                .isEqualTo(ReconnectingStrips(shell = false, panes = false))
        }
    }
}
