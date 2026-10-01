package com.greenfodor.ppremotece.core.domain.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import org.junit.jupiter.api.Test

class KeepScreenOnTest {
    @Test
    fun `off never keeps the screen on`() {
        assertThat(ShellTab.entries.filter { keepScreenOn(KeepAwake.OFF, it) }).isEqualTo(emptyList())
    }

    @Test
    fun `remote only keeps the screen on while the remote tab is shown`() {
        assertThat(ShellTab.entries.filter { keepScreenOn(KeepAwake.REMOTE_ONLY, it) })
            .isEqualTo(listOf(ShellTab.REMOTE))
    }

    @Test
    fun `always keeps the screen on with every tab`() {
        assertThat(ShellTab.entries.filter { keepScreenOn(KeepAwake.ALWAYS, it) }).isEqualTo(ShellTab.entries)
    }
}
