package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme

private val BadgeInset = 8.dp

/**
 * The LIVE encoding of a card, tile, cell or pill of [shape]: while [live], a `tertiary` ring
 * [ringWidth] wide and, unless [badge] is null, the [LiveBadge] reading [badge] at [badgeAlignment]
 * over [content].
 */
@Composable
fun LiveMark(
    live: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 2.dp,
    badge: String? = stringResource(R.string.cue_live),
    badgeAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = if (live) modifier.border(ringWidth, MaterialTheme.colorScheme.tertiary, shape) else modifier
    ) {
        content()
        if (live && badge != null) LiveBadge(Modifier.align(badgeAlignment).padding(BadgeInset), text = badge)
    }
}

@Preview
@Composable
private fun LiveMarkPreview() {
    PPRemoteTheme {
        val shape = RoundedCornerShape(16.dp)
        LiveMark(live = true, shape = shape) {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(160.dp)
            ) {
            }
        }
    }
}
