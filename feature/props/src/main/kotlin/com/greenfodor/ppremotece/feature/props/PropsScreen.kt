package com.greenfodor.ppremotece.feature.props

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.LiveMark
import com.greenfodor.ppremotece.core.designsystem.ui.LoadableList
import com.greenfodor.ppremotece.core.designsystem.ui.PropThumbnail
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.SyntheticThumbnails
import com.greenfodor.ppremotece.core.designsystem.ui.TabTitle
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailRequest
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailRequests
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val GridPadding = 8.dp
private val GridGap = 8.dp
private val BottomClearance = 88.dp
private val HeaderHeight = 48.dp
private const val COMPACT_COLUMNS = 2

@Composable
fun PropsRoot(
    widthClass: WidthClass,
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: PropsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    PropsScreen(
        state = state,
        onAction = viewModel::onAction,
        widthClass = widthClass,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * The Props tab: one section per collection in a grid of tiles (two columns on compact width,
 * adaptive 200 / 240 dp cells on medium / expanded width), each section under a 48 dp header while
 * there are several, with 88 dp below the last row; a spinner until the props are loaded, "No props
 * in ProPresenter" when there are none, and "Not available on this ProPresenter" when the server
 * rejected them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropsScreen(
    state: PropsState,
    onAction: (PropsAction) -> Unit,
    widthClass: WidthClass,
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
                title = { TabTitle(DesignR.drawable.ic_layers, stringResource(R.string.props_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        Column(modifier = Modifier.fillMaxSize().padding(insets.frame)) {
            ReconnectingStrip(visible = reconnecting)
            LoadableList(state.sections, emptyText = stringResource(R.string.props_empty)) { sections ->
                PropGrid(
                    sections = sections,
                    showHeaders = state.showHeaders,
                    thumbnails = state.thumbnails,
                    widthClass = widthClass,
                    bottomInset = insets.scrollBottom,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun PropGrid(
    sections: List<PropSectionUi>,
    showHeaders: Boolean,
    thumbnails: PropThumbnailRequests?,
    widthClass: WidthClass,
    bottomInset: Dp,
    onAction: (PropsAction) -> Unit
) {
    LazyVerticalGrid(
        columns = columnsOf(widthClass),
        contentPadding = PaddingValues(
            start = GridPadding,
            top = GridPadding,
            end = GridPadding,
            bottom = BottomClearance + bottomInset
        ),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        verticalArrangement = Arrangement.spacedBy(GridGap),
        modifier = Modifier.fillMaxSize()
    ) {
        sections.forEach { section ->
            if (showHeaders) {
                item(key = "header:${section.uuid}", span = { GridItemSpan(maxLineSpan) }) {
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.height(HeaderHeight)) {
                        Text(
                            text = section.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = GridPadding)
                        )
                    }
                }
            }
            items(section.props, key = { "${section.uuid}:${it.uuid}" }) { prop ->
                PropTile(
                    prop = prop,
                    thumbnails = thumbnails,
                    onClick = { onAction(PropsAction.OnPropClick(prop.uuid)) }
                )
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
 * A tile with 16 dp corners on `surfaceContainerHigh`: the prop's thumbnail over black at the
 * width the tile measures, the name below in `titleSmall` on up to two lines, and the LIVE ring
 * and badge ([LiveMark]) while the prop is active.
 */
@Composable
private fun PropTile(prop: PropUi, thumbnails: PropThumbnailRequests?, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    var widthPx by remember { mutableIntStateOf(0) }
    val request = remember(thumbnails, prop.uuid, widthPx) {
        thumbnails?.takeIf { widthPx > 0 }?.request(prop.uuid, widthPx)
    }
    LiveMark(live = prop.active, shape = shape) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { widthPx = it.width }
                .semantics { selected = prop.active }
        ) {
            Column {
                PropThumbnail(
                    url = request?.url,
                    cacheKey = request?.cacheKey,
                    version = prop.thumbnailVersion,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = prop.name,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private val PreviewThumbnails = PropThumbnailRequests { uuid, px ->
    PropThumbnailRequest("http://192.0.2.14:60113/v1/prop/$uuid/thumbnail?quality=$px", "prop:preview:$uuid:w$px")
}

private val PreviewProps = listOf(
    PropUi("p-0", "Prop 01", active = true, thumbnailVersion = "Prop 01"),
    PropUi("p-1", "Prop 02 with a name long enough for two lines", active = false, thumbnailVersion = "Prop 02"),
    PropUi("p-2", "Prop 03", active = false, thumbnailVersion = "Prop 03")
)

@Preview(widthDp = 411, heightDp = 480)
@Composable
private fun PropsScreenPreview() {
    PPRemoteTheme {
        SyntheticThumbnails {
            PropsScreen(
                state = PropsState(
                    sections = Loadable.Loaded(listOf(PropSectionUi("c-0", "Collection 01", PreviewProps))),
                    thumbnails = PreviewThumbnails
                ),
                onAction = {},
                widthClass = WidthClass.COMPACT
            )
        }
    }
}

@Preview(widthDp = 411, heightDp = 640)
@Composable
private fun PropsScreenTwoCollectionsPreview() {
    PPRemoteTheme {
        SyntheticThumbnails {
            PropsScreen(
                state = PropsState(
                    sections = Loadable.Loaded(
                        listOf(
                            PropSectionUi("c-0", "Collection 01", PreviewProps.take(2)),
                            PropSectionUi("c-1", "Collection 02", PreviewProps.drop(2))
                        )
                    ),
                    showHeaders = true,
                    thumbnails = PreviewThumbnails
                ),
                onAction = {},
                widthClass = WidthClass.COMPACT
            )
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun PropTilePreview() {
    PPRemoteTheme {
        SyntheticThumbnails {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(180.dp).padding(8.dp)) {
                PropTile(prop = PreviewProps[0], thumbnails = PreviewThumbnails, onClick = {})
                PropTile(prop = PreviewProps[1], thumbnails = PreviewThumbnails, onClick = {})
                PropTile(prop = PreviewProps[2], thumbnails = null, onClick = {})
            }
        }
    }
}
