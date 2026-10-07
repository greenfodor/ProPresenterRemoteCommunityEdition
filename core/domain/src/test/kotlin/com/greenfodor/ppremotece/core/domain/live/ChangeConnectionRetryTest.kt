package com.greenfodor.ppremotece.core.domain.live

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class ChangeConnectionRetryTest {
    @Test
    fun `the retry delay doubles from two seconds and is capped at thirty`() {
        assertThat((0..6).map(::changeConnectionRetryDelay))
            .isEqualTo(listOf(2, 4, 8, 16, 30, 30, 30).map { it.seconds })
    }

    @Test
    fun `a very late attempt still waits thirty seconds`() {
        assertThat(changeConnectionRetryDelay(1_000)).isEqualTo(30.seconds)
    }
}
