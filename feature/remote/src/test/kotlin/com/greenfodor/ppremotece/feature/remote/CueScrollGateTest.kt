package com.greenfodor.ppremotece.feature.remote

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import org.junit.jupiter.api.Test

class CueScrollGateTest {
    private val itemA = CueSource.PlaylistItem(PlaylistItemKey("pl", 0))
    private val itemB = CueSource.PlaylistItem(PlaylistItemKey("pl", 1))
    private val gate = CueScrollGate()

    @Test
    fun `the change that reaches the tapped cue does not scroll and a later change does`() {
        gate.onTap(itemA, index = 5, focus = 4)

        assertThat(gate.onChange(itemA, focus = 5)).isFalse()
        assertThat(gate.onChange(itemA, focus = 6)).isTrue()
    }

    @Test
    fun `a change from elsewhere scrolls`() {
        assertThat(gate.onChange(itemA, focus = 7)).isTrue()
    }

    @Test
    fun `a change to another cue after a tap scrolls and forgets the tap`() {
        gate.onTap(itemA, index = 5, focus = 4)

        assertThat(gate.onChange(itemA, focus = 7)).isTrue()
        assertThat(gate.onChange(itemA, focus = 5)).isTrue()
    }

    @Test
    fun `a tap on the cue that is already the focus is not remembered`() {
        gate.onTap(itemA, index = 3, focus = 3)

        assertThat(gate.onChange(itemB, focus = 3)).isTrue()
        assertThat(gate.onChange(itemA, focus = 3)).isTrue()
    }

    @Test
    fun `the same cue index in another source scrolls`() {
        gate.onTap(itemA, index = 5, focus = 4)

        assertThat(gate.onChange(itemB, focus = 5)).isTrue()
    }

    @Test
    fun `two quick taps each reach their cue without scrolling`() {
        gate.onTap(itemA, index = 5, focus = 4)
        gate.onTap(itemA, index = 6, focus = 4)

        assertThat(gate.onChange(itemA, focus = 5)).isFalse()
        assertThat(gate.onChange(itemA, focus = 6)).isFalse()
        assertThat(gate.onChange(itemA, focus = 7)).isTrue()
    }

    @Test
    fun `a change with no focus neither scrolls nor forgets the tap`() {
        gate.onTap(itemA, index = 5, focus = 4)

        assertThat(gate.onChange(null, focus = null)).isFalse()
        assertThat(gate.onChange(itemA, focus = null)).isFalse()
        assertThat(gate.onChange(itemA, focus = 5)).isFalse()
    }
}
