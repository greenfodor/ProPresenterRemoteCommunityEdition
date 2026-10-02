package com.greenfodor.ppremotece.core.domain.arrangement

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.cueList
import org.junit.jupiter.api.Test

class ResyncTargetTest {
    private val long = cueList("long").cues
    private val short = cueList("short").cues

    @Test
    fun `the same repeat of the live slide is chosen`() {
        assertThat(resyncTarget(long, liveIndex = 7, itemCues = short)).isEqualTo(ResyncTarget.Cue(5))
    }

    @Test
    fun `a repeat this arrangement lacks falls back to the first occurrence`() {
        assertThat(resyncTarget(long, liveIndex = 10, itemCues = short)).isEqualTo(ResyncTarget.Cue(1))
    }

    @Test
    fun `a slide this arrangement lacks is no match`() {
        assertThat(resyncTarget(long, liveIndex = 8, itemCues = short))
            .isEqualTo(ResyncTarget.NoMatch(NoMatchReason.NOT_IN_ARRANGEMENT))
    }

    @Test
    fun `a live index outside the live arrangement is no match`() {
        assertThat(resyncTarget(long, liveIndex = 11, itemCues = short))
            .isEqualTo(ResyncTarget.NoMatch(NoMatchReason.NOT_IN_ARRANGEMENT))
    }

    @Test
    fun `an unresolved live arrangement is no match`() {
        assertThat(resyncTarget(liveCues = null, liveIndex = 3, itemCues = short))
            .isEqualTo(ResyncTarget.NoMatch(NoMatchReason.LIVE_ARRANGEMENT_UNKNOWN))
    }

    @Test
    fun `a disabled target moves to the next enabled cue`() {
        val targetDisabled = short.map { if (it.index == 1) it.copy(enabled = false) else it }

        assertThat(resyncTarget(long, liveIndex = 3, itemCues = targetDisabled)).isEqualTo(ResyncTarget.Cue(2))
    }

    @Test
    fun `a disabled target with no enabled cue after it is no match`() {
        assertThat(resyncTarget(long, liveIndex = 3, itemCues = short.map { it.copy(enabled = false) }))
            .isEqualTo(ResyncTarget.NoMatch(NoMatchReason.NO_ENABLED_AFTER))
    }
}
