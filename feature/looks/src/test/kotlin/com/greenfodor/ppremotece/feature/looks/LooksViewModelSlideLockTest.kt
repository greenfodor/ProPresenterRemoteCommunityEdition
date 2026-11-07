package com.greenfodor.ppremotece.feature.looks

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isNotEmpty
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import org.junit.jupiter.api.Test

class LooksViewModelSlideLockTest {
    @Test
    fun `the view model takes no AppPreferences, so its calls do not depend on the slide lock`() {
        val constructors = LooksViewModel::class.java.constructors.toList()

        assertThat(constructors).isNotEmpty()
        constructors.forEach { constructor ->
            assertThat(constructor.parameterTypes.toList()).doesNotContain(AppPreferences::class.java)
        }
    }
}
