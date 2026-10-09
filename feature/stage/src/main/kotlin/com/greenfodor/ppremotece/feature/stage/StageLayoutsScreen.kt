package com.greenfodor.ppremotece.feature.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
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
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailRequests
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private const val COMPACT_COLUMNS = 2

/**
 * The layouts of stage screen [screenUuid]; [onBack] closes them, from the back arrow and once the
 * screen is gone from ProPresenter.
 */
@Composable
fun StageLayoutsRoot(
    screenUuid: String,
    widthClass: WidthClass,
    reconnecting: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: StageLayoutsViewModel = koinViewModel(key = "stage/$screenUuid") { parametersOf(screenUuid) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.screenGone) {
        if (state.screenGone) onBack()
    }
    StageLayoutsScreen(
        state = state,
        onAction = viewModel::onAction,
        widthClass = widthClass,
        onBack = onBack,
        reconnecting = reconnecting,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * A stage screen's layouts under its name and a back arrow: a grid of thumbnail tiles (two columns
 * on compact width, adaptive 200 / 240 dp cells on medium / expanded width), with 88 dp below the
 * last row while the Clear FAB shows; a tap sets the layout. A spinner until the layouts are
 * loaded, "No stage layouts in ProPresenter" when there are none, and "Not available on this
 * ProPresenter" when the server rejected them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageLayoutsScreen(
    state: StageLayoutsState,
    onAction: (StageLayoutsAction) -> Unit,
    widthClass: WidthClass,
    onBack: () -> Unit,
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
                title = { Text(text = state.screenName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.stage_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        Column(modifier = Modifier.fillMaxSize().padding(insets.frame)) {
            ReconnectingStrip(visible = reconnecting)
            LoadableList(state.layouts, emptyText = stringResource(R.string.stage_layouts_empty)) { layouts ->
                LazyVerticalGrid(
                    columns = columnsOf(widthClass),
                    contentPadding = PaddingValues(
                        start = GridPadding,
                        top = GridPadding,
                        end = GridPadding,
                        bottom = insets.scrollBottom +
                            if (floatingActionButton != null) BottomClearance else 0.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(layouts, key = { it.uuid }) { layout ->
                        StageLayoutTile(
                            layout = layout,
                            thumbnails = state.thumbnails,
                            onClick = { onAction(StageLayoutsAction.OnLayoutClick(layout.uuid)) }
                        )
                    }
                }
            }
        }
    }
}

private fun columnsOf(widthClass: WidthClass): GridCells =
    when (widthClass) {
        WidthClass.COMPACT -> GridCells.Fixed(COMPACT_COLUMNS)
        WidthClass.MEDIUM -> GridCells.Adaptive(200.dp)
        WidthClass.EXPANDED -> GridCells.Adaptive(240.dp)
    }

/**
 * A tile with 16 dp corners on `surfaceContainerHigh`: the layout's 16:9 thumbnail and its name
 * below in `titleSmall` on up to two lines. The layout the stage screen shows has the LIVE ring
 * ([LiveMark]) and the LIVE badge at the end of the name's row.
 */
@Composable
private fun StageLayoutTile(layout: StageLayoutUi, thumbnails: StageLayoutThumbnailRequests?, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    LiveMark(live = layout.live, shape = shape, badge = null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().semantics { selected = layout.live }
        ) {
            Column {
                StageLayoutThumbnail(
                    layoutUuid = layout.uuid,
                    version = layout.name,
                    thumbnails = thumbnails,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = layout.name,
                        style = MaterialTheme.typography.titleSmall,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (layout.live) LiveBadge()
                }
            }
        }
    }
}

private val PreviewLayouts = listOf(
    StageLayoutUi("l-0", "Layout 01", live = true),
    StageLayoutUi("l-1", "Layout 02 with a name long enough for two lines", live = false),
    StageLayoutUi("l-2", "Layout 03", live = false),
    StageLayoutUi("l-3", "Layout 04", live = false),
    StageLayoutUi("l-4", "Layout 05", live = false)
)

private val PreviewState = StageLayoutsState(
    screenName = "Stage Screen 01",
    layouts = Loadable.Loaded(PreviewLayouts),
    thumbnails = PreviewThumbnails
)

@Preview(widthDp = 527, heightDp = 700)
@Composable
private fun StageLayoutsScreenPreview() {
    PPRemoteTheme {
        StageLayoutsScreen(state = PreviewState, onAction = {}, widthClass = WidthClass.COMPACT, onBack = {})
    }
}

@Preview(widthDp = 1100, heightDp = 500)
@Composable
private fun StageLayoutsScreenWidePreview() {
    PPRemoteTheme {
        StageLayoutsScreen(state = PreviewState, onAction = {}, widthClass = WidthClass.EXPANDED, onBack = {})
    }
}

@Preview(widthDp = 411, heightDp = 300)
@Composable
private fun StageLayoutsScreenEmptyPreview() {
    PPRemoteTheme {
        StageLayoutsScreen(
            state = PreviewState.copy(layouts = Loadable.Loaded(emptyList())),
            onAction = {},
            widthClass = WidthClass.COMPACT,
            onBack = {}
        )
    }
}

@Preview(widthDp = 411, heightDp = 300)
@Composable
private fun StageLayoutsScreenUnavailablePreview() {
    PPRemoteTheme {
        StageLayoutsScreen(
            state = PreviewState.copy(layouts = Loadable.Unavailable),
            onAction = {},
            widthClass = WidthClass.COMPACT,
            onBack = {}
        )
    }
}
