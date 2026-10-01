package com.greenfodor.ppremotece.feature.clear

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

/**
 * The rail's "Clear" item; it opens the Clear sheet like [ClearFab], shown while [open]. Failures
 * are shown in the sheet while it is open and in [snackbarHostState] otherwise.
 */
@Composable
fun ClearRailButton(
    snackbarHostState: SnackbarHostState,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClearViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ClearSheetLauncher(state, viewModel.events, viewModel::onAction, snackbarHostState, open, onOpenChange) {
        ClearRailItem(onClick = { onOpenChange(true) }, modifier = modifier)
    }
}

/** A rail item with the `ink_eraser` icon and the "Clear" label. */
@Composable
fun ClearRailItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    NavigationRailItem(
        selected = false,
        onClick = onClick,
        icon = { Icon(painterResource(DesignR.drawable.ic_ink_eraser), contentDescription = null) },
        label = { Text(stringResource(R.string.clear_open)) },
        modifier = modifier
    )
}

@Preview(heightDp = 480)
@Composable
private fun ClearRailItemPreview() {
    PPRemoteTheme {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            NavigationRailItem(
                selected = true,
                onClick = {},
                icon = { Icon(painterResource(DesignR.drawable.ic_slideshow), contentDescription = null) },
                label = { Text("Presentation") }
            )
            NavigationRailItem(
                selected = false,
                onClick = {},
                icon = { Icon(painterResource(DesignR.drawable.ic_settings_remote), contentDescription = null) },
                label = { Text("Remote") }
            )
            Spacer(modifier = Modifier.weight(1f))
            ClearRailItem(onClick = {})
        }
    }
}
