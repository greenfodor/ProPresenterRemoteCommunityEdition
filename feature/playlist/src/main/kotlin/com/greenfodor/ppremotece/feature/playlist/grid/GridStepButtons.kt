package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.live.CueStep
import com.greenfodor.ppremotece.core.domain.live.CueSteps
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val StepButtonHeight = 64.dp
private val WideStepButtonHeight = 56.dp
private val WideStepButtonWidth = 160.dp

/**
 * The Previous and Next buttons under the grid, each enabled unless its step of [steps] is
 * disabled: 64 dp tall and half the width each, or, when [wide], 56 dp tall and 160 dp wide at the
 * end of the bar.
 */
@Composable
internal fun StepButtons(steps: CueSteps, wide: Boolean, onAction: (SlideGridAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(12.dp)
    ) {
        val buttonSize = if (wide) {
            Modifier.width(WideStepButtonWidth).height(WideStepButtonHeight)
        } else {
            Modifier.weight(1f).height(StepButtonHeight)
        }
        FilledTonalButton(
            onClick = { onAction(SlideGridAction.OnPreviousClick) },
            enabled = steps.previous != CueStep.Disabled,
            modifier = buttonSize
        ) {
            Icon(painterResource(DesignR.drawable.ic_skip_previous), contentDescription = null)
            Text(stringResource(R.string.grid_previous), modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = { onAction(SlideGridAction.OnNextClick) },
            enabled = steps.next != CueStep.Disabled,
            modifier = buttonSize
        ) {
            Text(stringResource(R.string.grid_next), modifier = Modifier.padding(end = 8.dp))
            Icon(painterResource(DesignR.drawable.ic_skip_next), contentDescription = null)
        }
    }
}

private val PreviewSteps = CueSteps(next = CueStep.Relative, previous = CueStep.Relative)

@Preview(widthDp = 527)
@Composable
private fun StepButtonsPreview() {
    PPRemoteTheme { StepButtons(PreviewSteps, wide = false, onAction = {}) }
}

@Preview(widthDp = 813)
@Composable
private fun StepButtonsWidePreview() {
    PPRemoteTheme { StepButtons(PreviewSteps, wide = true, onAction = {}) }
}
