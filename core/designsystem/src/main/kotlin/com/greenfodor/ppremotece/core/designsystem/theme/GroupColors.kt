package com.greenfodor.ppremotece.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val NoGroupColor = Color(0xFF696969)
private val LightLabelColor = Color(0xFFFFFFFF)
private val DarkLabelColor = Color(0xFF000000)

/**
 * Extended token for ProPresenter slide-group colours: [fallback] is used for groups without a colour,
 * and [labelOn] picks the label text colour with the higher contrast against a group colour.
 */
@Immutable
data class GroupColors(
    val fallback: Color = NoGroupColor,
    val lightLabel: Color = LightLabelColor,
    val darkLabel: Color = DarkLabelColor
) {
    fun labelOn(groupColor: Color): Color {
        val groupLuminance = groupColor.luminance()
        val lightContrast = contrast(lightLabel.luminance(), groupLuminance)
        val darkContrast = contrast(darkLabel.luminance(), groupLuminance)
        return if (lightContrast >= darkContrast) lightLabel else darkLabel
    }

    private fun contrast(first: Float, second: Float): Float =
        (maxOf(first, second) + CONTRAST_OFFSET) / (minOf(first, second) + CONTRAST_OFFSET)

    private companion object {
        const val CONTRAST_OFFSET = 0.05f
    }
}

val LocalGroupColors = staticCompositionLocalOf { GroupColors() }
