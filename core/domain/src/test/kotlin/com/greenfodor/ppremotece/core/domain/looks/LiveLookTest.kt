package com.greenfodor.ppremotece.core.domain.looks

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.model.Look
import org.junit.jupiter.api.Test

class LiveLookTest {
    private val looks = listOf(Look("l-0", "Look 01", 0), Look("l-1", "Look 02", 1), Look("l-2", "Look 03", 2))

    @Test
    fun `the live look matches the current look by index and name`() {
        assertThat(liveLook(looks, Look(LIVE_UUID, "Look 02", 1))).isEqualTo(looks[1])
    }

    @Test
    fun `the current look's uuid is ignored`() {
        assertThat(liveLook(looks, Look("l-0", "Look 02", 1))).isEqualTo(looks[1])
    }

    @Test
    fun `a current look whose index or name differs matches nothing`() {
        assertThat(liveLook(looks, Look(LIVE_UUID, "Look 02", 2))).isNull()
        assertThat(liveLook(looks, Look(LIVE_UUID, "Look 09", 1))).isNull()
        assertThat(liveLook(looks, null)).isNull()
    }

    @Test
    fun `one-frame and two-frame trigger sequences end on the same look`() {
        val oneFrame = listOf(Look(LIVE_UUID, "Look 02", 1))
        val twoFrames = listOf(Look("l-1", "Look 02", 1), Look(LIVE_UUID, "Look 02", 1))

        assertThat(oneFrame.map { liveLook(looks, it) }.last()).isEqualTo(looks[1])
        assertThat(twoFrames.map { liveLook(looks, it) }).isEqualTo(listOf(looks[1], looks[1]))
    }

    private companion object {
        const val LIVE_UUID = "live-look"
    }
}
