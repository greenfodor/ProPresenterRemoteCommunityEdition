package com.greenfodor.ppremotece.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.domain.layout.NavigationLayout
import com.greenfodor.ppremotece.core.domain.layout.RAIL_SLOT_DP
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import com.greenfodor.ppremotece.feature.clear.ClearRailButton
import com.greenfodor.ppremotece.feature.clear.ClearRailItem

private val RailItemSpacing = 4.dp

/** The bar or rail of [items] around [content]; the rail ends with the Clear button. */
@Composable
internal fun ShellFrame(
    layout: NavigationLayout,
    items: List<ShellItem>,
    current: ShellTab,
    onSelect: (ShellTab) -> Unit,
    snackbars: SnackbarHostState,
    clearOpen: Boolean,
    onClearOpenChange: (Boolean) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (layout == NavigationLayout.RAIL) {
            ShellRail(items = items, current = current, onSelect = onSelect) {
                ClearRailButton(snackbarHostState = snackbars, open = clearOpen, onOpenChange = onClearOpenChange)
            }
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                SnackbarHost(
                    snackbars,
                    modifier = if (layout == NavigationLayout.RAIL) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    } else {
                        Modifier
                    }
                )
            },
            bottomBar = {
                if (layout == NavigationLayout.BAR) ShellBar(items = items, current = current, onSelect = onSelect)
            },
            modifier = Modifier
                .weight(1f)
                .then(
                    if (layout == NavigationLayout.RAIL) {
                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
    }
}

/** The rail: [items] in the height left above a pinned 64 dp footer holding [clearButton]. */
@Composable
private fun ShellRail(
    items: List<ShellItem>,
    current: ShellTab,
    onSelect: (ShellTab) -> Unit,
    clearButton: @Composable () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RailItemSpacing),
            modifier = Modifier.weight(1f).clipToBounds()
        ) {
            items.forEach { item ->
                NavigationRailItem(
                    selected = current == item.tab,
                    onClick = { onSelect(item.tab) },
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                    label = { Text(stringResource(item.label)) }
                )
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(RAIL_SLOT_DP.dp)) {
            clearButton()
        }
    }
}

@Composable
private fun ShellBar(items: List<ShellItem>, current: ShellTab, onSelect: (ShellTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        items.forEach { item ->
            NavigationBarItem(
                selected = current == item.tab,
                onClick = { onSelect(item.tab) },
                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                label = { Text(stringResource(item.label)) }
            )
        }
    }
}

@Preview(heightDp = 400)
@Composable
private fun ShellRailPreview() {
    PPRemoteTheme {
        ShellRail(
            items = ShellDestination.entries.map { it.item },
            current = ShellTab.PRESENTATION,
            onSelect = {}
        ) { ClearRailItem(onClick = {}) }
    }
}

@Preview(heightDp = 240)
@Composable
private fun ShellRailMoreHighlightedPreview() {
    PPRemoteTheme {
        ShellRail(
            items = listOf(ShellDestination.PRESENTATION.item, MoreItem),
            current = ShellTab.MORE,
            onSelect = {}
        ) { ClearRailItem(onClick = {}) }
    }
}
