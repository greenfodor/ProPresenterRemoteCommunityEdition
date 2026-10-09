package com.greenfodor.ppremotece

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class SplashHoldTest {
    @Test
    fun `the splash is kept while start-up is unresolved and under two seconds old`() {
        assertThat(keepSplash(startupResolved = false, elapsedMillis = 0)).isTrue()
        assertThat(keepSplash(startupResolved = false, elapsedMillis = 1_900)).isTrue()
    }

    @Test
    fun `the splash is released at two seconds with start-up unresolved`() {
        assertThat(keepSplash(startupResolved = false, elapsedMillis = 2_000)).isFalse()
        assertThat(keepSplash(startupResolved = false, elapsedMillis = 60_000)).isFalse()
    }

    @Test
    fun `the splash is kept for its first 600 ms although start-up is resolved`() {
        assertThat(keepSplash(startupResolved = true, elapsedMillis = 0)).isTrue()
        assertThat(keepSplash(startupResolved = true, elapsedMillis = 599)).isTrue()
    }

    @Test
    fun `the splash is released from 600 ms on once start-up is resolved`() {
        assertThat(keepSplash(startupResolved = true, elapsedMillis = 600)).isFalse()
        assertThat(keepSplash(startupResolved = true, elapsedMillis = 1_900)).isFalse()
        assertThat(keepSplash(startupResolved = true, elapsedMillis = 2_000)).isFalse()
    }
}
