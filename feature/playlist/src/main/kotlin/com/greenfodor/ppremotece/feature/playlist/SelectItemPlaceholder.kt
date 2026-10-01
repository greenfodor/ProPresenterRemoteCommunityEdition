package com.greenfodor.ppremotece.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.greenfodor.ppremotece.feature.playlist.tree.ListMode

/** Detail-pane content shown before a playlist item, or in Library [mode] a presentation, is opened. */
@Composable
fun SelectItemPlaceholder(mode: ListMode, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = stringResource(
                if (mode == ListMode.LIBRARY) R.string.grid_select_presentation else R.string.grid_select_item
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
