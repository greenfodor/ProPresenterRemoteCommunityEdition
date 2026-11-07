package com.greenfodor.ppremotece.feature.stage

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isNotEmpty
import com.greenfodor.ppremotece.core.domain.settings.AppPreferences
import org.junit.jupiter.api.Test

class StageLayoutsViewModelSlideLockTest {
    @Test
    fun `the view model takes no AppPreferences, so its calls do not depend on the slide lock`() {
        val constructors = StageLayoutsViewModel::class.java.constructors.toList()

        assertThat(constructors).isNotEmpty()
        constructors.forEach { constructor ->
            assertThat(constructor.parameterTypes.toList()).doesNotContain(AppPreferences::class.java)
        }
    }
}
