package com.greenfodor.ppremotece.core.domain.transport

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class FormatDurationTest {
    @Test
    fun `seconds under a minute keep a zero minute`() {
        assertThat(formatDuration(20)).isEqualTo("0:20")
        assertThat(formatDuration(0)).isEqualTo("0:00")
    }

    @Test
    fun `minutes are not padded and seconds are`() {
        assertThat(formatDuration(183)).isEqualTo("3:03")
        assertThat(formatDuration(600)).isEqualTo("10:00")
    }

    @Test
    fun `an hour or more adds the hours and pads the minutes`() {
        assertThat(formatDuration(3725)).isEqualTo("1:02:05")
        assertThat(formatDuration(3600)).isEqualTo("1:00:00")
    }

    @Test
    fun `a negative duration reads as zero`() {
        assertThat(formatDuration(-5)).isEqualTo("0:00")
    }
}
