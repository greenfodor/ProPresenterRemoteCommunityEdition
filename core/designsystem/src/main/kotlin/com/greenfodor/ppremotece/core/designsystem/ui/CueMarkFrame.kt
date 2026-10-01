package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val RingSlot = 4.dp
private val RingGap = 4.dp
private val LiveRingWidth = 4.dp
private val SecondaryRingWidth = 2.dp
private const val DISABLED_ALPHA = 0.38f

/** The corner radius of the content inside a [CueMarkFrame]. */
internal val CueContentCorner = 4.dp

private val RingShape = RoundedCornerShape(CueContentCorner + RingGap + RingSlot)

/** The ring of [mark]: 4 dp `tertiary` for LIVE, 2 dp `secondary` for NEXT and CUED, none otherwise. */
@Composable
@ReadOnlyComposable
private fun cueMarkRing(mark: CueMark): BorderStroke? =
    when (mark) {
        CueMark.LIVE -> BorderStroke(LiveRingWidth, MaterialTheme.colorScheme.tertiary)
        CueMark.NEXT, CueMark.CUED -> BorderStroke(SecondaryRingWidth, MaterialTheme.colorScheme.secondary)
        CueMark.NONE -> null
    }

/**
 * A full-width cue with a reserved ring slot around [content] holding [mark]'s ring
 * ([cueMarkRing]). A disabled cue is dimmed and not clickable; with a null [onClick] it is not
 * clickable. A LIVE cue is marked selected.
 */
@Composable
internal fun CueMarkFrame(
    mark: CueMark,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val ring = cueMarkRing(mark)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(if (ring != null) Modifier.border(ring, RingShape) else Modifier)
            .clip(RingShape)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .semantics { selected = mark == CueMark.LIVE }
            .padding(RingSlot + RingGap),
        content = content
    )
}
