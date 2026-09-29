package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchScope
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.SlideThumbnail
import com.greenfodor.ppremotece.core.designsystem.ui.SyntheticThumbnails
import com.greenfodor.ppremotece.core.domain.layout.GridStep
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.feature.playlist.ArrangementChip
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.text
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val GridPadding = 8.dp
private const val HEADER_KEY = "header"
private val RingSlot = 4.dp
private val RingGap = 4.dp
private val LiveRingWidth = 4.dp
private val NextRingWidth = 2.dp
private val FrameWidth = 4.dp
private val FrameCorner = 4.dp
private val LabelStripHeight = 28.dp
private val BadgeIconSize = 16.dp
private val StepButtonHeight = 64.dp
private const val DISABLED_ALPHA = 0.38f

private enum class CueMark { NONE, LIVE, NEXT }

@OptIn(ExperimentalFoundationApi::class)
private object NoPrefetch : LazyGridPrefetchStrategy {
    override fun LazyGridPrefetchScope.onScroll(delta: Float, layoutInfo: LazyGridLayoutInfo) = Unit

    override fun LazyGridPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyGridLayoutInfo) = Unit

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit
}

@Composable
fun SlideGridRoot(
    item: PlaylistItemKey,
    widthClass: WidthClass,
    headerScrollsWithGrid: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SlideGridViewModel = koinViewModel(key = item.toString()) { parametersOf(item) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SlideGridEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }
    LaunchedEffect(widthClass) { viewModel.onAction(SlideGridAction.OnWidthClassChange(widthClass)) }
    SlideGridScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        headerScrollsWithGrid = headerScrollsWithGrid,
        snackbarHostState = snackbarHostState,
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
    headerScrollsWithGrid: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.grid_back))
                    }
                },
                actions = { GridActions(gridStep = state.gridStep ?: GridStep.Default, onAction = onAction) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = { StepButtons(onAction = onAction) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading || state.gridStep == null && state.error == null ->
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
                else -> CueGrid(
                    state = state,
                    gridStep = state.gridStep ?: GridStep.Default,
                    headerScrollsWithGrid = headerScrollsWithGrid,
                    onAction = onAction
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CueGrid(
    state: SlideGridState,
    gridStep: GridStep,
    headerScrollsWithGrid: Boolean,
    onAction: (SlideGridAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!headerScrollsWithGrid) GridHeader(state, horizontalPadding = GridPadding)
        LazyVerticalGrid(
            columns = gridStep.toGridCells(),
            state = rememberLazyGridState(prefetchStrategy = NoPrefetch),
            contentPadding = PaddingValues(GridPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (headerScrollsWithGrid) {
                item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    GridHeader(state, horizontalPadding = 0.dp)
                }
            }
            items(state.cues, key = { it.index }) { cue ->
                CueCell(
                    cue = cue,
                    aspect = state.aspect,
                    thumbnailGeneration = state.thumbnailGeneration,
                    mark = when (cue.index) {
                        state.liveCueIndex -> CueMark.LIVE
                        state.nextCueIndex -> CueMark.NEXT
                        else -> CueMark.NONE
                    },
                    onClick = { onAction(SlideGridAction.OnCueClick(cue.index)) }
                )
            }
        }
    }
}

@Composable
private fun GridHeader(state: SlideGridState, horizontalPadding: Dp) {
    Column {
        val cueCount = state.cues.size
        ArrangementChip(
            text = state.label?.let { pluralStringResource(R.plurals.grid_header_chip, cueCount, it.text(), cueCount) }
                ?: pluralStringResource(R.plurals.grid_cue_count, cueCount, cueCount),
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp)
        )
        if (state.countMismatch) CountMismatchLine()
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

@Composable
private fun CueCell(cue: CueUi, aspect: Float, thumbnailGeneration: Int, mark: CueMark, onClick: () -> Unit) {
    val groupColors = PPRemoteTheme.groupColors
    val frameColor = cue.groupColor?.takeIf { it.alpha > 0f }?.toColor() ?: MaterialTheme.colorScheme.outlineVariant
    val ringModifier = when (mark) {
        CueMark.LIVE -> Modifier.border(LiveRingWidth, MaterialTheme.colorScheme.tertiary, RingShape)
        CueMark.NEXT -> Modifier.border(NextRingWidth, MaterialTheme.colorScheme.secondary, RingShape)
        CueMark.NONE -> Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (cue.enabled) 1f else DISABLED_ALPHA)
            .then(ringModifier)
            .clip(RingShape)
            .clickable(enabled = cue.enabled, onClick = onClick)
            .semantics { selected = mark == CueMark.LIVE }
            .padding(RingSlot + RingGap)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(FrameCorner))
                .background(frameColor)
                .padding(start = FrameWidth, top = FrameWidth, end = FrameWidth)
        ) {
            Box {
                key(thumbnailGeneration) {
                    SlideThumbnail(
                        url = cue.thumbnail?.url,
                        cacheKey = cue.thumbnail?.cacheKey,
                        aspect = aspect,
                        fallbackText = cue.text.ifBlank { cue.groupName },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CueBadges(mark = mark, enabled = cue.enabled)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(LabelStripHeight)) {
                val labelColor = groupColors.labelOn(frameColor)
                val maxLabelWidth = maxWidth / 2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.grid_cue_label, cue.index + 1, cue.groupName),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (cue.label.isNotEmpty()) {
                        Text(
                            text = cue.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = maxLabelWidth)
                        )
                    }
                }
            }
        }
    }
}

private val RingShape = RoundedCornerShape(FrameCorner + RingGap + RingSlot)

@Composable
private fun BoxScope.CueBadges(mark: CueMark, enabled: Boolean) {
    when (mark) {
        CueMark.LIVE -> Badge(
            text = stringResource(R.string.grid_live),
            container = MaterialTheme.colorScheme.tertiary,
            content = MaterialTheme.colorScheme.onTertiary
        )
        CueMark.NEXT -> Badge(
            text = stringResource(R.string.grid_next_badge),
            container = MaterialTheme.colorScheme.secondary,
            content = MaterialTheme.colorScheme.onSecondary
        )
        CueMark.NONE -> Unit
    }
    if (!enabled) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_visibility_off),
                contentDescription = stringResource(R.string.grid_disabled),
                modifier = Modifier.padding(2.dp).size(BadgeIconSize)
            )
        }
    }
}

@Composable
private fun BoxScope.Badge(text: String, container: Color, content: Color) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun StepButtons(onAction: (SlideGridAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(12.dp)
    ) {
        FilledTonalButton(
            onClick = { onAction(SlideGridAction.OnPreviousClick) },
            modifier = Modifier.weight(1f).height(StepButtonHeight)
        ) {
            Icon(painterResource(DesignR.drawable.ic_skip_previous), contentDescription = null)
            Text(stringResource(R.string.grid_previous), modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            onClick = { onAction(SlideGridAction.OnNextClick) },
            modifier = Modifier.weight(1f).height(StepButtonHeight)
        ) {
            Text(stringResource(R.string.grid_next), modifier = Modifier.padding(end = 8.dp))
            Icon(painterResource(DesignR.drawable.ic_skip_next), contentDescription = null)
        }
    }
}

private fun GroupColor.toColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)

@Preview
@Composable
private fun SlideGridScreenPreview() {
    val chorus = GroupColor(red = 0f, green = 0.47f, blue = 0.8f, alpha = 1f)

    fun thumbnail(cue: Int) = ThumbnailRequest(
        "http://192.0.2.14:60113/v1/playlist/p/6/thumbnail/$cue?quality=400",
        "k$cue"
    )
    PPRemoteTheme {
        SyntheticThumbnails {
            SlideGridScreen(
                state = SlideGridState(
                    title = "Song C",
                    label = ArrangementLabel.Named("A"),
                    cues = listOf(
                        CueUi(0, "Verse 1", null, "Verse 1 · 1", label = "", enabled = true, thumbnail = thumbnail(0)),
                        CueUi(1, "Verse 1", null, "Verse 1 · 2", label = "", enabled = false, thumbnail = thumbnail(1)),
                        CueUi(2, "Verse 1", null, "Verse 1 · 3", label = "", enabled = true, thumbnail = thumbnail(2)),
                        CueUi(3, "Chorus", chorus, "Chorus · 1", label = "Label 01", enabled = true),
                        CueUi(4, "Chorus", chorus, "", label = "", enabled = true)
                    ),
                    aspect = 1920f / 858f,
                    countMismatch = true,
                    liveCueIndex = 0,
                    nextCueIndex = 2,
                    isLoading = false
                ),
                onAction = {},
                onBack = {}
            )
        }
    }
}
