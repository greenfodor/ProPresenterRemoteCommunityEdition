package com.greenfodor.ppremotece.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class GroupColorsTest {
    private val colors = GroupColors()

    @Test
    fun `the label on a light fill is black`() {
        assertThat(colors.labelOn(Color(0.95f, 0.85f, 0.3f))).isEqualTo(Color.Black)
        assertThat(colors.labelOn(Color.White)).isEqualTo(Color.Black)
    }

    @Test
    fun `the label on a dark fill is white`() {
        assertThat(colors.labelOn(Color(0.33f, 0.42f, 0.18f))).isEqualTo(Color.White)
        assertThat(colors.labelOn(Color(0xFF2B2B2B))).isEqualTo(Color.White)
    }
}
