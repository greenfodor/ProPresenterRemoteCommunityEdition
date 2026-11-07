package com.greenfodor.ppremotece.feature.timers

import assertk.assertThat
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import org.junit.jupiter.api.Test

class TimersViewModelSlideLockTest {
    @Test
    fun `the view model does not read the slide lock`() {
        val constructors = TimersViewModel::class.java.constructors

        assertThat(
            constructors.all { constructor ->
                AppPreferences::class.java !in constructor.parameterTypes
            }
        ).isTrue()
    }
}
