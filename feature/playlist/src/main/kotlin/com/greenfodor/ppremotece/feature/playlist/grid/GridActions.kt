package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.feature.playlist.R
import kotlin.math.roundToInt
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val SlideSizeMenuWidth = 280.dp

/** Top-bar actions of the slide grid: the slide size slider menu and the overflow menu with "Reload slides". */
@Composable
internal fun GridActions(gridStep: GridStep, onAction: (SlideGridAction) -> Unit) {
    var sizeOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { sizeOpen = true }) {
            Icon(painterResource(DesignR.drawable.ic_grid_view), stringResource(R.string.grid_slide_size))
        }
        DropdownMenu(expanded = sizeOpen, onDismissRequest = { sizeOpen = false }) {
            SlideSizeControl(
                gridStep = gridStep,
                onStepChange = { onAction(SlideGridAction.OnGridStepChange(it)) },
                onStepChangeFinished = { onAction(SlideGridAction.OnGridStepChangeFinished) }
            )
        }
    }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(painterResource(DesignR.drawable.ic_more_vert), stringResource(R.string.playlists_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.grid_reload)) },
                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_refresh), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onAction(SlideGridAction.OnReloadClick)
                }
            )
        }
    }
}

@Composable
private fun SlideSizeControl(gridStep: GridStep, onStepChange: (GridStep) -> Unit, onStepChangeFinished: () -> Unit) {
    val steps = GridStep.entries
    Column(modifier = Modifier.width(SlideSizeMenuWidth).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = stringResource(R.string.grid_slide_size), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_photo_size_select_small),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = gridStep.ordinal.toFloat(),
                onValueChange = { value -> steps[value.roundToInt()].takeIf { it != gridStep }?.let(onStepChange) },
                onValueChangeFinished = onStepChangeFinished,
                valueRange = 0f..(steps.size - 1).toFloat(),
                steps = steps.size - 2,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(DesignR.drawable.ic_photo_size_select_large),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
