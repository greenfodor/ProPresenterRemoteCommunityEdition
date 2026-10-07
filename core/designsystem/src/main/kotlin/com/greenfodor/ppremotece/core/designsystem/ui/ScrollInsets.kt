package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A `Scaffold`'s content padding split for a scrolling screen: [frame] pads the screen's content
 * and [scrollBottom] is added to the bottom content padding of its list.
 */
@Immutable
class ScrollInsets(
    val frame: PaddingValues,
    val scrollBottom: Dp
)

/**
 * Splits [padding]. Its bottom moves to [ScrollInsets.scrollBottom], so the list draws down to the
 * screen's edge; while tappable navigation buttons sit at the bottom it stays in
 * [ScrollInsets.frame] and the list ends above them.
 */
@Composable
fun scrollInsets(padding: PaddingValues): ScrollInsets {
    val buttonsAtBottom = WindowInsets.tappableElement.getBottom(LocalDensity.current) > 0
    if (buttonsAtBottom) return ScrollInsets(frame = padding, scrollBottom = 0.dp)
    val direction = LocalLayoutDirection.current
    return ScrollInsets(
        frame = PaddingValues(
            start = padding.calculateStartPadding(direction),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(direction)
        ),
        scrollBottom = padding.calculateBottomPadding()
    )
}
