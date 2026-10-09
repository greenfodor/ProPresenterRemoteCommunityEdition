package com.greenfodor.ppremotece.feature.looks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.LiveBadge
import com.greenfodor.ppremotece.core.designsystem.ui.LiveMark
import com.greenfodor.ppremotece.core.designsystem.ui.LoadableList
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.TabTitle
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.domain.live.Loadable
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val GridPadding = 8.dp
private val GridGap = 8.dp
private val BottomClearance = 88.dp
private val MinCardWidth = 280.dp
private val CardHeight = 64.dp
private val CardPadding = 16.dp
private val IconSize = 24.dp

@Composable
fun LooksRoot(
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: LooksViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LooksScreen(
        state = state,
        onAction = viewModel::onAction,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * The Looks tab under a `theater_comedy` title: one 64 dp card per look in an adaptive grid of
 * 280 dp cells, a radio group in
 * which the live look is selected and marked, with 88 dp below the last row; a spinner until the
 * looks are loaded, "No looks in ProPresenter" when there are none, and "Not available on this
 * ProPresenter" when the server rejected them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LooksScreen(
    state: LooksState,
    onAction: (LooksAction) -> Unit,
    modifier: Modifier = Modifier,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { floatingActionButton?.invoke(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { TabTitle(DesignR.drawable.ic_theater_comedy, stringResource(R.string.looks_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        Column(modifier = Modifier.fillMaxSize().padding(insets.frame)) {
            ReconnectingStrip(visible = reconnecting)
            LoadableList(state.looks, emptyText = stringResource(R.string.looks_empty)) { looks ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(MinCardWidth),
                    contentPadding = PaddingValues(
                        start = GridPadding,
                        top = GridPadding,
                        end = GridPadding,
                        bottom = insets.scrollBottom +
                            if (floatingActionButton != null) BottomClearance else 0.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                    modifier = Modifier.fillMaxSize().selectableGroup()
                ) {
                    items(looks, key = { it.uuid }) { look ->
                        LookCard(look = look, onClick = { onAction(LooksAction.OnLookClick(look.uuid)) })
                    }
                }
            }
        }
    }
}

/**
 * A 64 dp card with 16 dp corners on `surfaceContainerHigh`: the name in `titleMedium` on one
 * line and a radio, checked for the live look, which also gets the LIVE ring
 * and badge ([LiveMark]).
 */
@Composable
private fun LookCard(look: LookUi, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    LiveMark(live = look.live, shape = shape, badge = null) {
        Surface(
            selected = look.live,
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(CardHeight)
                .semantics { role = Role.RadioButton }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = CardPadding)) {
                Text(
                    text = look.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (look.live) {
                    LiveBadge(modifier = Modifier.padding(horizontal = 8.dp))
                }
                Icon(
                    painterResource(if (look.live) RadioChecked else RadioUnchecked),
                    contentDescription = null,
                    tint = radioTint(look.live),
                    modifier = Modifier.size(IconSize)
                )
            }
        }
    }
}

private val RadioChecked = DesignR.drawable.ic_radio_button_checked
private val RadioUnchecked = DesignR.drawable.ic_radio_button_unchecked

@Composable
private fun radioTint(live: Boolean) =
    if (live) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant

private val PreviewLooks = listOf(
    LookUi("l-0", "Look 01", live = false),
    LookUi("l-1", "Look 02", live = true),
    LookUi("l-2", "Look 03 with a name long enough to be cut short", live = false)
)

@Preview(widthDp = 411, heightDp = 400)
@Composable
private fun LooksScreenPreview() {
    PPRemoteTheme {
        LooksScreen(state = LooksState(Loadable.Loaded(PreviewLooks)), onAction = {})
    }
}

@Preview(widthDp = 320)
@Composable
private fun LookCardPreview() {
    PPRemoteTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            LookCard(look = PreviewLooks[1], onClick = {})
            LookCard(look = PreviewLooks[0], onClick = {})
        }
    }
}

@Preview(widthDp = 840, heightDp = 300)
@Composable
private fun LooksScreenExpandedPreview() {
    PPRemoteTheme {
        LooksScreen(state = LooksState(Loadable.Loaded(PreviewLooks)), onAction = {}, reconnecting = true)
    }
}
