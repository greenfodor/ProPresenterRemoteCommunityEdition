package com.greenfodor.ppremotece.core.domain.arrangement

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.arrangement.SongFixtures.cueList
import org.junit.jupiter.api.Test

class ResyncCueTest {
    private val long = cueList("long").cues
    private val short = cueList("short").cues

    @Test
    fun `the same repeat of the live slide is chosen`() {
        assertThat(resyncCue(long, liveIndex = 7, itemCues = short)).isEqualTo(5)
    }

    @Test
    fun `a repeat this arrangement lacks falls back to the first occurrence`() {
        assertThat(resyncCue(long, liveIndex = 10, itemCues = short)).isEqualTo(1)
    }

    @Test
    fun `a slide this arrangement lacks falls back to the first enabled cue`() {
        assertThat(resyncCue(long, liveIndex = 8, itemCues = short)).isEqualTo(0)
    }

    @Test
    fun `an unresolved live arrangement falls back to the first enabled cue`() {
        val firstDisabled = short.map { if (it.index == 0) it.copy(enabled = false) else it }

        assertThat(resyncCue(liveCues = null, liveIndex = 3, itemCues = short)).isEqualTo(0)
        assertThat(resyncCue(liveCues = null, liveIndex = 3, itemCues = firstDisabled)).isEqualTo(1)
    }

    @Test
    fun `a disabled target moves to the next enabled cue`() {
        val targetDisabled = short.map { if (it.index == 1) it.copy(enabled = false) else it }

        assertThat(resyncCue(long, liveIndex = 3, itemCues = targetDisabled)).isEqualTo(2)
    }

    @Test
    fun `nothing is chosen when no cue is enabled`() {
        assertThat(resyncCue(long, liveIndex = 3, itemCues = short.map { it.copy(enabled = false) })).isNull()
    }
}
