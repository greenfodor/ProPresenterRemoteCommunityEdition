package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchScope
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.ui.ArrangementChip
import com.greenfodor.ppremotece.core.designsystem.ui.CueCell
import com.greenfodor.ppremotece.core.designsystem.ui.CueMark
import com.greenfodor.ppremotece.core.designsystem.ui.CueRow
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.ViewMode
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val GridPadding = 8.dp
private val GridBottomPadding = 88.dp
private const val HEADER_KEY = "header"

@OptIn(ExperimentalFoundationApi::class)
private object NoPrefetch : LazyGridPrefetchStrategy {
    override fun LazyGridPrefetchScope.onScroll(delta: Float, layoutInfo: LazyGridLayoutInfo) = Unit

    override fun LazyGridPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyGridLayoutInfo) = Unit

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit
}

/** The slide grid of [source]; with [closesPane] its navigation icon is a close icon, otherwise a back arrow. */
@Composable
fun SlideGridRoot(
    source: CueSource,
    widthClass: WidthClass,
    headerScrollsWithGrid: Boolean,
    reconnecting: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    closesPane: Boolean = false,
    floatingActionButton: @Composable (SnackbarHostState) -> Unit = {},
    viewModel: SlideGridViewModel = koinViewModel(key = source.toString()) { parametersOf(source) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SlideGridEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
            is SlideGridEvent.ScrollToCue -> scrollRequests.tryEmit(event.cueIndex)
        }
    }
    LaunchedEffect(widthClass) { viewModel.onAction(SlideGridAction.OnWidthClassChange(widthClass)) }
    SlideGridScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        closesPane = closesPane,
        wideSteps = widthClass == WidthClass.EXPANDED,
        headerScrollsWithGrid = headerScrollsWithGrid,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        firstVisibleCue = viewModel.firstVisibleCue,
        scrollRequests = scrollRequests,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideGridScreen(
    state: SlideGridState,
    onAction: (SlideGridAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    closesPane: Boolean = false,
    wideSteps: Boolean = false,
    headerScrollsWithGrid: Boolean = false,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: @Composable (SnackbarHostState) -> Unit = {},
    firstVisibleCue: Int = 0,
    scrollRequests: Flow<Int> = emptyFlow()
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { floatingActionButton(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        if (closesPane) {
                            Icon(painterResource(DesignR.drawable.ic_close), stringResource(R.string.grid_close))
                        } else {
                            Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.grid_back))
                        }
                    }
                },
                actions = {
                    GridActions(
                        gridStep = state.gridStep ?: GridStep.Default,
                        viewMode = state.viewMode ?: ViewMode.GRID,
                        onAction = onAction
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = { StepButtons(steps = state.steps, wide = wideSteps, onAction = onAction) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReconnectingStrip(visible = reconnecting)
            GridContent(
                state = state,
                headerScrollsWithGrid = headerScrollsWithGrid,
                firstVisibleCue = firstVisibleCue,
                scrollRequests = scrollRequests,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun GridContent(
    state: SlideGridState,
    headerScrollsWithGrid: Boolean,
    firstVisibleCue: Int,
    scrollRequests: Flow<Int>,
    onAction: (SlideGridAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading || (state.gridStep == null || state.viewMode == null) && state.error == null ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = state.error.asString(), color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    onAction(SlideGridAction.OnRetryClick)
                }) { Text(stringResource(R.string.playlists_retry)) }
            }
            state.viewMode == ViewMode.LIST -> CueList(
                state = state,
                headerScrollsWithList = headerScrollsWithGrid,
                firstVisibleCue = firstVisibleCue,
                scrollRequests = scrollRequests,
                onAction = onAction
            )
            else -> CueGrid(
                state = state,
                gridStep = state.gridStep ?: GridStep.Default,
                headerScrollsWithGrid = headerScrollsWithGrid,
                firstVisibleCue = firstVisibleCue,
                scrollRequests = scrollRequests,
                onAction = onAction
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CueGrid(
    state: SlideGridState,
    gridStep: GridStep,
    headerScrollsWithGrid: Boolean,
    firstVisibleCue: Int,
    scrollRequests: Flow<Int>,
    onAction: (SlideGridAction) -> Unit
) {
    val headerItems = if (headerScrollsWithGrid) 1 else 0
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = itemIndexOf(firstVisibleCue, state.cues, headerItems),
        prefetchStrategy = NoPrefetch
    )
    ReportFirstVisibleCue(
        firstVisibleItem = { gridState.firstVisibleItemIndex },
        isScrolling = { gridState.isScrollInProgress },
        cues = state.cues,
        headerItems = headerItems,
        onAction = onAction
    )
    ScrollToCueRequests(scrollRequests, state.cues, headerItems) { gridState.animateScrollToItem(it) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (!headerScrollsWithGrid) GridHeader(state, horizontalPadding = GridPadding, onAction = onAction)
        LazyVerticalGrid(
            columns = gridStep.toGridCells(),
            state = gridState,
            contentPadding = PaddingValues(
                start = GridPadding,
                top = GridPadding,
                end = GridPadding,
                bottom = GridBottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (headerScrollsWithGrid) {
                item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    GridHeader(state, horizontalPadding = 0.dp, onAction = onAction)
                }
            }
            items(state.cues, key = { it.index }) { cue ->
                CueCell(
                    number = cue.index + 1,
                    groupName = cue.groupName,
                    groupColor = cue.groupColor,
                    fallbackText = cue.text.ifBlank { cue.groupName },
                    aspect = state.aspect,
                    onClick = { onAction(SlideGridAction.OnCueClick(cue.index)) },
                    thumbnail = cue.thumbnail,
                    label = cue.label,
                    enabled = cue.enabled,
                    thumbnailGeneration = state.thumbnailGeneration,
                    mark = cue.mark(state),
                    showGroupName = cue.startsGroup
                )
            }
        }
    }
}

@Composable
private fun CueList(
    state: SlideGridState,
    headerScrollsWithList: Boolean,
    firstVisibleCue: Int,
    scrollRequests: Flow<Int>,
    onAction: (SlideGridAction) -> Unit
) {
    val headerItems = if (headerScrollsWithList) 1 else 0
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = itemIndexOf(firstVisibleCue, state.cues, headerItems)
    )
    ReportFirstVisibleCue(
        firstVisibleItem = { listState.firstVisibleItemIndex },
        isScrolling = { listState.isScrollInProgress },
        cues = state.cues,
        headerItems = headerItems,
        onAction = onAction
    )
    ScrollToCueRequests(scrollRequests, state.cues, headerItems) { listState.animateScrollToItem(it) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (!headerScrollsWithList) GridHeader(state, horizontalPadding = GridPadding, onAction = onAction)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = GridPadding,
                top = GridPadding,
                end = GridPadding,
                bottom = GridBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (headerScrollsWithList) {
                item(key = HEADER_KEY) {
                    GridHeader(state, horizontalPadding = 0.dp, onAction = onAction)
                }
            }
            items(state.cues, key = { it.index }) { cue ->
                CueRow(
                    number = cue.index + 1,
                    groupName = cue.groupName,
                    groupColor = cue.groupColor,
                    text = cue.text,
                    onClick = { onAction(SlideGridAction.OnCueClick(cue.index)) },
                    label = cue.label,
                    enabled = cue.enabled,
                    mark = cue.mark(state),
                    showGroupName = cue.startsGroup
                )
            }
        }
    }
}

private fun CueUi.mark(state: SlideGridState): CueMark =
    when (index) {
        state.marked?.index -> if (state.marked.cleared) CueMark.CLEARED else CueMark.LIVE
        state.marked?.next -> CueMark.NEXT
        else -> CueMark.NONE
    }

@Composable
private fun GridHeader(state: SlideGridState, horizontalPadding: Dp, onAction: (SlideGridAction) -> Unit) {
    Column {
        val cueCount = state.cues.size
        ArrangementChip(
            text = state.label?.let { pluralStringResource(R.plurals.grid_header_chip, cueCount, it.text(), cueCount) }
                ?: pluralStringResource(R.plurals.grid_cue_count, cueCount, cueCount),
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp)
        )
        if (state.groupSequence.pills.isNotEmpty()) {
            GroupStrip(
                sequence = state.groupSequence,
                cleared = state.marked?.cleared == true,
                onPillClick = { onAction(SlideGridAction.OnGroupPillClick(it)) },
                horizontalPadding = horizontalPadding
            )
        }
        if (state.countMismatch) CountMismatchLine()
        state.banner?.let { banner ->
            ArrangementBannerBar(
                banner = banner,
                resync = state.resync,
                arrangement = state.label,
                onResync = { onAction(SlideGridAction.OnResyncClick) },
                horizontalPadding = horizontalPadding
            )
        }
    }
}

@Composable
private fun CountMismatchLine() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.grid_count_mismatch),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
