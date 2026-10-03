package com.greenfodor.ppremotece.core.domain.props

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.Prop
import org.junit.jupiter.api.Test

class PropTapTest {
    private val prop = Prop("p-0", "Prop 01", 0, isActive = false, transitionName = null)

    @Test
    fun `a tap triggers an inactive prop`() {
        assertThat(propTap(prop)).isEqualTo(PropTap.TRIGGER)
    }

    @Test
    fun `a tap clears an active prop`() {
        assertThat(propTap(prop.copy(isActive = true))).isEqualTo(PropTap.CLEAR)
    }
}
